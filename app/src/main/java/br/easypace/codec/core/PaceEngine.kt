package br.easypace.codec.core

import kotlin.math.abs

/**
 * ============================================================================
 *  O CEREBRO DO APP
 * ----------------------------------------------------------------------------
 *  Recebe eventos crus dos sensores e, a cada "tick" (20x por segundo),
 *  devolve um [RunSnapshot] pronto para a tela e para o audio.
 *
 *  Esta classe NAO importa nada do Android. Isso e proposital: permite
 *  simular uma corrida inteira na JVM com um relogio falso (PaceEngineTest).
 *
 *  Thread-safety: os sensores chamam de threads diferentes do loop, por isso
 *  os metodos publicos sao @Synchronized (custo desprezivel a 20 Hz).
 *
 *  ---------------------------------------------------------------------------
 *  PIPELINE DE UM TICK
 *  ---------------------------------------------------------------------------
 *   1. cadencia .......... passos por minuto, por intervalos entre pisadas
 *   2. velocidade bruta .. MEDIANA dos ultimos fixes de GPS, fundida com a
 *                          cadencia; a mediana descarta picos de multipath
 *   3. passada ........... aprende o seu comprimento de passada (GPS x cadencia)
 *   4. suavizacao ........ filtro exponencial curto, so para emendar os fixes
 *   5. distancia ......... integra a velocidade filtrada
 *   6. JANELA ............ pace = tempo da janela / distancia da janela
 *                          <- e AQUI que a leitura fica estavel
 *   7. limitador ......... impede saltos visuais no numero
 *   8. estado ............ banda morta + histerese + tempo de confirmacao
 *
 *  Por que a janela (passo 6) e o coracao da estabilidade: pace = 1000/v e
 *  uma hiperbole, entao o mesmo ruido de velocidade vira um erro de pace
 *  tanto maior quanto mais devagar voce vai. Ver a explicacao completa em
 *  [Config.paceWindowSec].
 * ============================================================================
 */
class PaceEngine(
    targetSecPerKm: Int = Config.DEFAULT_TARGET_PACE_SEC_PER_KM
) {

    /** Meta em segundos por km. Pode ser trocada a qualquer momento, de
     *  qualquer thread (o servico atualiza a cada tick). */
    @Volatile
    var targetSecPerKm: Int = targetSecPerKm

    // --- estado da sessao --------------------------------------------------
    private var running = false
    private var startMs = 0L
    private var lastTickMs = 0L
    private var distanceM = 0.0
    private var filteredSpeed = 0.0

    // --- GPS ---------------------------------------------------------------
    /** Leituras cruas recentes; a mediana delas e que vale. */
    private val gpsSpeedHistory = ArrayDeque<Double>()
    private var lastGpsMs = Long.MIN_VALUE / 4
    private var gpsSpeed = 0.0
    private var gpsAccuracyM: Float? = null

    // --- cadencia ----------------------------------------------------------
    private val stepTimes = ArrayDeque<Long>()
    private var lastStepMs = Long.MIN_VALUE / 4
    private var strideM = Config.STRIDE_INIT_M

    // --- janela deslizante de distancia ------------------------------------
    /** Pares (instante, distancia acumulada). Amostrado a cada 200 ms. */
    private val windowTimes = ArrayDeque<Long>()
    private val windowDistances = ArrayDeque<Double>()
    private var lastWindowSampleMs = Long.MIN_VALUE / 4

    /** Ultimo pace entregue, para o limitador de variacao. */
    private var reportedPace: Double? = null

    // --- maquina de estados ------------------------------------------------
    private var committed = PaceState.AQUECENDO
    private var candidate = PaceState.AQUECENDO
    private var candidateSinceMs = 0L

    // =======================================================================
    //  CICLO DE VIDA
    // =======================================================================

    /** Zera tudo e comeca a contar. [nowMs] deve vir de um relogio monotonico. */
    @Synchronized
    fun start(nowMs: Long) {
        running = true
        startMs = nowMs
        lastTickMs = nowMs
        distanceM = 0.0
        filteredSpeed = 0.0
        gpsSpeedHistory.clear()
        lastGpsMs = Long.MIN_VALUE / 4
        lastStepMs = Long.MIN_VALUE / 4
        gpsSpeed = 0.0
        gpsAccuracyM = null
        stepTimes.clear()
        strideM = Config.STRIDE_INIT_M
        windowTimes.clear()
        windowDistances.clear()
        lastWindowSampleMs = Long.MIN_VALUE / 4
        reportedPace = null
        committed = PaceState.AQUECENDO
        candidate = PaceState.AQUECENDO
        candidateSinceMs = nowMs
    }

    @Synchronized
    fun stop() {
        running = false
    }

    // =======================================================================
    //  ENTRADAS DOS SENSORES
    // =======================================================================

    /**
     * Novo fix de GPS.
     *
     * @param speedMps velocidade instantanea informada pelo GPS
     * @param speedAccuracyMps incerteza da velocidade (null em aparelhos antigos)
     * @param horizontalAccuracyM raio de erro da posicao
     */
    @Synchronized
    fun onLocation(
        nowMs: Long,
        speedMps: Double,
        speedAccuracyMps: Float?,
        horizontalAccuracyM: Float?
    ) {
        // Rejeita leituras obviamente ruins (predio, tunel, mata fechada).
        if (speedAccuracyMps != null && speedAccuracyMps > Config.GPS_MAX_SPEED_ACCURACY_MPS) return
        if (horizontalAccuracyM != null && horizontalAccuracyM > Config.GPS_MAX_HORIZONTAL_ACCURACY_M) return
        if (speedMps < 0.0 || speedMps > 12.0) return  // 12 m/s ~ 1:23 min/km: nao e corrida humana

        gpsSpeedHistory.addLast(speedMps)
        while (gpsSpeedHistory.size > Config.GPS_MEDIAN_WINDOW) gpsSpeedHistory.removeFirst()

        // A MEDIANA e que vira "a" velocidade do GPS. Um pico isolado de
        // multipath entra na lista mas nao consegue mover o valor do meio.
        gpsSpeed = median(gpsSpeedHistory)
        gpsAccuracyM = horizontalAccuracyM
        lastGpsMs = nowMs
    }

    /** Um passo detectado (sensor de passos ou pico do acelerometro). */
    @Synchronized
    fun onStep(nowMs: Long) {
        stepTimes.addLast(nowMs)
        lastStepMs = nowMs
    }

    // =======================================================================
    //  TICK
    // =======================================================================

    /** Avanca o motor e devolve o retrato atual. Chame a [Config.TICK_HZ] Hz. */
    @Synchronized
    fun tick(nowMs: Long): RunSnapshot {
        if (!running) return RunSnapshot(running = false, targetSecPerKm = targetSecPerKm)

        val dtMs = (nowMs - lastTickMs).coerceIn(0L, 2_000L)
        lastTickMs = nowMs

        // ---- 1. cadencia --------------------------------------------------
        pruneSteps(nowMs)
        val cadenceSpm = currentCadence(nowMs)

        val gpsFresh = (nowMs - lastGpsMs) <= Config.GPS_FRESH_MS
        val cadenceFresh = cadenceSpm > 0.0 && (nowMs - lastStepMs) <= Config.CADENCE_FRESH_MS

        // ---- 2/3. passada e velocidade bruta -------------------------------
        if (gpsFresh && cadenceFresh && gpsSpeed > Config.MIN_VALID_SPEED_MPS && cadenceSpm > 100.0) {
            val observed = gpsSpeed / (cadenceSpm / 60.0)
            if (observed in Config.STRIDE_MIN_M..Config.STRIDE_MAX_M) {
                strideM += Config.STRIDE_LEARN_ALPHA * (observed - strideM)
            }
        }
        val cadenceSpeed = strideM * (cadenceSpm / 60.0)

        val source: PaceSource
        val raw: Double?
        when {
            gpsFresh && cadenceFresh -> {
                source = PaceSource.FUSAO
                val w = Config.FUSION_GPS_WEIGHT
                raw = w * gpsSpeed + (1.0 - w) * cadenceSpeed
            }
            gpsFresh -> { source = PaceSource.GPS; raw = gpsSpeed }
            cadenceFresh -> { source = PaceSource.CADENCIA; raw = cadenceSpeed }
            else -> { source = PaceSource.NENHUMA; raw = null }
        }

        // ---- 4. suavizacao curta -------------------------------------------
        if (raw != null) {
            val a = PaceMath.emaAlpha(dtMs, Config.speedFilterTauS)
            filteredSpeed += a * (raw - filteredSpeed)
        } else {
            // Sem dado: cai suavemente para zero em vez de congelar um valor velho.
            val a = PaceMath.emaAlpha(dtMs, Config.speedFilterTauS * 2.0)
            filteredSpeed += a * (0.0 - filteredSpeed)
        }
        if (filteredSpeed < 0.0) filteredSpeed = 0.0

        // ---- 5. distancia ---------------------------------------------------
        distanceM += filteredSpeed * (dtMs / 1000.0)

        // ---- 6. janela deslizante -------------------------------------------
        sampleWindow(nowMs)
        val moving = filteredSpeed >= Config.MIN_VALID_SPEED_MPS
        val windowPace = if (moving) windowPace() else null
        val instantPace = if (moving) PaceMath.speedToPace(filteredSpeed) else null

        // ---- 7. limitador de variacao ---------------------------------------
        val pace = smoothPace(windowPace ?: instantPace, dtMs)

        val elapsedMs = nowMs - startMs
        val delta = pace?.let { it - targetSecPerKm }

        // ---- 8. estado -------------------------------------------------------
        val desired = when {
            elapsedMs < Config.warmupMs -> PaceState.AQUECENDO
            pace == null || delta == null -> PaceState.PARADO
            else -> classify(delta)
        }
        commitState(desired, nowMs)

        return RunSnapshot(
            running = true,
            state = committed,
            source = source,
            paceSecPerKm = pace,
            targetSecPerKm = targetSecPerKm,
            deltaSecPerKm = delta,
            speedMps = filteredSpeed,
            cadenceSpm = cadenceSpm,
            strideM = strideM,
            paceFromWindow = windowPace != null,
            distanceM = distanceM,
            elapsedMs = elapsedMs,
            gpsAccuracyM = if (gpsFresh) gpsAccuracyM else null
        )
    }

    // =======================================================================
    //  AUXILIARES
    // =======================================================================

    /** Valor do meio de uma lista. Imune a um pico isolado, ao contrario da media. */
    private fun median(values: Collection<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    private fun pruneSteps(nowMs: Long) {
        val limit = nowMs - Config.CADENCE_WINDOW_MS
        while (stepTimes.isNotEmpty() && stepTimes.first() < limit) stepTimes.removeFirst()
    }

    /**
     * Passos por minuto.
     *
     * Contamos os INTERVALOS entre os passos guardados (n-1 intervalos no tempo
     * entre o primeiro e o ultimo), e nao os passos dentro da janela. A
     * diferenca importa: dividir pelo "agora" faria a cadencia oscilar alguns
     * por cento conforme o tick caisse antes ou depois de uma pisada, e essa
     * oscilacao apareceria direto no pace.
     *
     * Quando os passos param, quem zera isto e a checagem de CADENCE_FRESH_MS
     * em [tick] — nao esta funcao.
     */
    private fun currentCadence(@Suppress("UNUSED_PARAMETER") nowMs: Long): Double {
        if (stepTimes.size < 3) return 0.0
        val spanMs = stepTimes.last() - stepTimes.first()
        if (spanMs <= 0L) return 0.0
        return (stepTimes.size - 1) * 60_000.0 / spanMs
    }

    /** Guarda um ponto (tempo, distancia) e descarta o que saiu da janela. */
    private fun sampleWindow(nowMs: Long) {
        if (nowMs - lastWindowSampleMs < Config.PACE_WINDOW_SAMPLE_MS) return
        lastWindowSampleMs = nowMs
        windowTimes.addLast(nowMs)
        windowDistances.addLast(distanceM)
        val limit = nowMs - (Config.paceWindowSec * 1000.0).toLong()
        while (windowTimes.size > 2 && windowTimes.first() < limit) {
            windowTimes.removeFirst()
            windowDistances.removeFirst()
        }
    }

    /**
     * Pace medio da janela: tempo decorrido dividido pela distancia percorrida.
     * Devolve null enquanto a janela nao tiver tempo e distancia suficientes —
     * nesse caso [tick] usa o calculo instantaneo.
     */
    private fun windowPace(): Double? {
        if (windowTimes.size < 2) return null
        val dtS = (windowTimes.last() - windowTimes.first()) / 1000.0
        val ddM = windowDistances.last() - windowDistances.first()
        if (dtS < Config.PACE_WINDOW_MIN_SEC) return null
        if (ddM < Config.PACE_WINDOW_MIN_DISTANCE_M) return null
        return dtS / (ddM / 1000.0)
    }

    /**
     * Impede que o numero na tela de saltos. Diferencas grandes (acima de
     * [Config.PACE_SNAP_SEC_PER_KM]) passam direto: sem essa valvula, sair de
     * um valor ruim — como o instantaneo dos primeiros segundos — levaria
     * minutos rastejando ate o valor certo.
     */
    private fun smoothPace(target: Double?, dtMs: Long): Double? {
        if (target == null) {
            reportedPace = null
            return null
        }
        val previous = reportedPace
        val result = if (previous == null || abs(target - previous) > Config.PACE_SNAP_SEC_PER_KM) {
            target
        } else {
            val maxStep = Config.paceSlewSecPerKmPerSec * (dtMs / 1000.0)
            previous + (target - previous).coerceIn(-maxStep, maxStep)
        }
        reportedPace = result
        return result
    }

    /**
     * Banda morta + histerese.
     * `delta > 0` significa pace MAIOR que a meta, ou seja, mais LENTO.
     */
    private fun classify(delta: Double): PaceState {
        val tol = Config.toleranceSecPerKm
        val innerTol = (tol - Config.hysteresisSecPerKm).coerceAtLeast(0.5)
        return when (committed) {
            PaceState.LENTO_DEMAIS -> when {
                delta < -tol -> PaceState.RAPIDO_DEMAIS
                delta > innerTol -> PaceState.LENTO_DEMAIS   // continua fora ate voltar bem
                else -> PaceState.NO_PACE
            }
            PaceState.RAPIDO_DEMAIS -> when {
                delta > tol -> PaceState.LENTO_DEMAIS
                delta < -innerTol -> PaceState.RAPIDO_DEMAIS
                else -> PaceState.NO_PACE
            }
            else -> when {
                delta > tol -> PaceState.LENTO_DEMAIS
                delta < -tol -> PaceState.RAPIDO_DEMAIS
                else -> PaceState.NO_PACE
            }
        }
    }

    /** So troca de estado se o candidato se sustentar por [Config.minStateDwellMs]. */
    private fun commitState(desired: PaceState, nowMs: Long) {
        if (desired != candidate) {
            candidate = desired
            candidateSinceMs = nowMs
        }
        if (desired == committed) return
        val needed = if (committed == PaceState.AQUECENDO) 0L else Config.minStateDwellMs
        if (nowMs - candidateSinceMs >= needed) committed = desired
    }
}

package br.easypace.codec.core

/**
 * Estado do corredor em relacao a meta.
 *
 * Ordem de prioridade na tela: PARADO/AQUECENDO nunca disparam audio.
 */
enum class PaceState {
    /** Ainda esperando os sensores estabilizarem depois do INICIAR. */
    AQUECENDO,

    /** Velocidade abaixo do minimo: voce parou (ou esta andando muito devagar). */
    PARADO,

    /** Pace mais rapido que a meta alem da tolerancia -> alerta AGUDO. */
    RAPIDO_DEMAIS,

    /** Dentro da banda de tolerancia -> som de "meta atingida" a cada 10 s. */
    NO_PACE,

    /** Pace mais lento que a meta alem da tolerancia -> alerta GRAVE. */
    LENTO_DEMAIS
}

/** De onde saiu a velocidade usada no calculo deste instante. */
enum class PaceSource {
    NENHUMA,   // sem dado confiavel ainda
    GPS,       // fix de GPS fresco
    CADENCIA,  // estimado por passos x comprimento de passada
    FUSAO      // GPS + cadencia combinados
}

/**
 * Retrato imutavel de um instante da corrida.
 * A UI e o modulo de audio consomem SOMENTE isto — nada mais.
 * Para expor um dado novo na tela, adicione um campo aqui e preencha em
 * [PaceEngine.tick].
 */
data class RunSnapshot(
    val running: Boolean = false,
    val state: PaceState = PaceState.AQUECENDO,
    val source: PaceSource = PaceSource.NENHUMA,

    /** Pace atual em segundos por km. `null` quando nao ha leitura util. */
    val paceSecPerKm: Double? = null,

    /** Meta em segundos por km. */
    val targetSecPerKm: Int = Config.DEFAULT_TARGET_PACE_SEC_PER_KM,

    /** pace - meta. Positivo = mais LENTO que a meta. `null` se sem leitura. */
    val deltaSecPerKm: Double? = null,

    val speedMps: Double = 0.0,
    val cadenceSpm: Double = 0.0,
    val strideM: Double = Config.STRIDE_INIT_M,
    /**
     * true quando o pace veio da janela deslizante (leitura estavel);
     * false quando ainda e o calculo instantaneo dos primeiros segundos.
     * A tela mostra isto para voce saber o quanto confiar no numero.
     */
    val paceFromWindow: Boolean = false,

    val distanceM: Double = 0.0,
    val elapsedMs: Long = 0L,
    val gpsAccuracyM: Float? = null
) {
    /** Pace medio da sessao inteira (segundos por km), ou null se sem distancia. */
    val averagePaceSecPerKm: Double?
        get() = if (distanceM > 20.0 && elapsedMs > 0) {
            (elapsedMs / 1000.0) / (distanceM / 1000.0)
        } else null
}

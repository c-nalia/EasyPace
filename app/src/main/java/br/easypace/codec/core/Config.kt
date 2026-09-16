package br.easypace.codec.core

/**
 * ============================================================================
 *  PAINEL DE AJUSTES DO EASYPACE
 * ----------------------------------------------------------------------------
 *  Este e o UNICO arquivo que voce precisa abrir para mudar o comportamento do
 *  app sem mexer em logica. Cada constante diz o que faz, a unidade e o efeito
 *  pratico de aumentar/diminuir o valor.
 *
 *  `const val` = valor fixo, compilado junto (mude e recompile).
 *  `var`       = pode ser alterado em tempo de execucao (pela tela de ajustes).
 * ============================================================================
 */
object Config {

    // ------------------------------------------------------------------
    // 1) FREQUENCIA DO LOOP
    // ------------------------------------------------------------------
    /** Quantas vezes por segundo o motor recalcula pace/estado/audio.
     *  20 Hz da resposta praticamente instantanea e custa quase nada de CPU.
     *  Faixa saudavel: 10..30. Acima de 30 so gasta bateria. */
    const val TICK_HZ: Int = 20

    /** Periodo do loop em milissegundos (derivado de TICK_HZ). */
    const val TICK_MS: Long = 1000L / TICK_HZ

    // ------------------------------------------------------------------
    // 2) META DE PACE E TOLERANCIA
    // ------------------------------------------------------------------
    /** Pace-alvo inicial, em SEGUNDOS por quilometro. 330 = 5:30 min/km. */
    const val DEFAULT_TARGET_PACE_SEC_PER_KM: Int = 330

    /** Limites aceitos pelo seletor da tela (segundos por km). */
    const val MIN_TARGET_PACE_SEC_PER_KM: Int = 150   // 2:30 min/km
    const val MAX_TARGET_PACE_SEC_PER_KM: Int = 900   // 15:00 min/km

    /** Banda morta em torno da meta: dentro dela o pace conta como "estavel".
     *  8 s/km e confortavel para rua. Diminua para treino de precisao;
     *  aumente se os alertas estiverem incomodando. */
    var toleranceSecPerKm: Double = 8.0

    /** Histerese: depois de sair da banda, e preciso voltar ate
     *  (tolerancia - histerese) para o app declarar "no pace" de novo.
     *  Evita alternar alerta/meta sem parar na fronteira. */
    var hysteresisSecPerKm: Double = 4.0

    /** Tempo minimo que um novo estado precisa se sustentar antes de valer.
     *  Filtra picos do GPS. Aumentar = mais calmo e mais lento para avisar. */
    var minStateDwellMs: Long = 2_500L

    // ------------------------------------------------------------------
    // 3) FILTRAGEM DA VELOCIDADE
    // ------------------------------------------------------------------
    /** Constante de tempo do filtro exponencial da velocidade, em segundos.
     *  Este filtro NAO e mais quem estabiliza a leitura — quem faz isso e a
     *  janela deslizante do item 3b. Aqui basta um valor pequeno, so para
     *  emendar o intervalo entre um fix de GPS e o proximo. */
    var speedFilterTauS: Double = 2.5

    /**
     * Quantas leituras cruas de GPS entram na MEDIANA.
     *
     * Por que mediana e nao media: perto de predios e sob arvores o GPS solta
     * picos isolados (multipath) que chegam a dobrar a velocidade. Uma media
     * absorve o pico e passa a mentir; a mediana simplesmente o descarta.
     * Sem isto, medindo com picos a cada 20 s, o pace exibido fica ~15 s/km
     * mais RAPIDO que o real correndo, e ~35 s/km andando — nao e so tremor,
     * e erro sistematico.
     *
     * Use sempre um numero impar. 5 leituras = 5 segundos de historico.
     */
    const val GPS_MEDIAN_WINDOW: Int = 5

    /** Abaixo desta velocidade (m/s) o app considera que voce parou e cala a boca.
     *  0.8 m/s ~= 20:50 min/km (caminhada bem lenta). */
    const val MIN_VALID_SPEED_MPS: Double = 0.8

    /** Descarta fixes de GPS cuja incerteza de velocidade passe deste valor (m/s). */
    const val GPS_MAX_SPEED_ACCURACY_MPS: Float = 2.0f

    /** Descarta fixes com raio de erro horizontal maior que isto (metros). */
    const val GPS_MAX_HORIZONTAL_ACCURACY_M: Float = 30f

    /** Um fix de GPS e considerado "fresco" por este tempo. */
    const val GPS_FRESH_MS: Long = 1_800L

    // ------------------------------------------------------------------
    // 3b) JANELA DESLIZANTE — o que realmente estabiliza o pace
    // ------------------------------------------------------------------
    /**
     * O pace mostrado e avaliado NAO e "1000 / velocidade agora". E a media
     * sobre os ultimos N segundos: distancia percorrida na janela dividida
     * pelo tempo da janela.
     *
     * Por que isso importa tanto: pace = 1000/velocidade e uma hiperbole. A
     * derivada e -1000/v^2, entao o mesmo errinho de velocidade vira um erro
     * de pace MUITO maior quanto mais devagar voce vai. Correndo a 3,3 m/s,
     * um erro de 0,1 m/s desloca o pace em 9 s/km. Andando a 1,4 m/s, o MESMO
     * erro desloca 51 s/km. E por isso que o numero parecia enlouquecer numa
     * caminhada tranquila: o ruido nao aumentou, a amplificacao e que e maior.
     *
     * A janela mata isso porque integra: 12 fixes de GPS medios valem muito
     * mais que o ultimo fix sozinho.
     *
     * O custo e atraso. Medido em simulacao, para uma mudanca real de ritmo:
     *      janela  8 s -> reage em  7 s   desvio  6,9 s/km
     *      janela 12 s -> reage em  9 s   desvio  6,0 s/km   <== padrao
     *      janela 20 s -> reage em 12 s   desvio  4,7 s/km
     * Ajustavel na tela de AJUSTES, sem recompilar.
     */
    var paceWindowSec: Double = 12.0
    const val MIN_PACE_WINDOW_SEC: Double = 5.0
    const val MAX_PACE_WINDOW_SEC: Double = 30.0

    /** De quanto em quanto tempo a janela guarda um ponto (distancia, tempo).
     *  200 ms basta e evita encher a memoria com 20 amostras por segundo. */
    const val PACE_WINDOW_SAMPLE_MS: Long = 200L

    /** A janela so vale depois de acumular este tempo... */
    const val PACE_WINDOW_MIN_SEC: Double = 5.0
    /** ...e esta distancia. Antes disso, cai no calculo instantaneo. */
    const val PACE_WINDOW_MIN_DISTANCE_M: Double = 8.0

    /** Teto de variacao do pace exibido, em segundos por km A CADA SEGUNDO.
     *  E so um verniz: impede o numero de dar saltos visuais. A estabilidade
     *  de verdade vem da janela. */
    var paceSlewSecPerKmPerSec: Double = 25.0

    /** Diferenca acima da qual o limitador acima e ignorado e o valor "pula"
     *  direto. Sem esta valvula, sair de um valor ruim levaria minutos. */
    const val PACE_SNAP_SEC_PER_KM: Double = 90.0

    /** Quantas vezes por segundo a TELA e redesenhada. O motor continua em
     *  TICK_HZ (20 Hz) para o audio reagir; so o numero na tela e atualizado
     *  mais devagar — um digito trocando 20x por segundo parece instavel
     *  mesmo quando a medicao esta boa. */
    const val UI_UPDATE_HZ: Int = 4

    // ------------------------------------------------------------------
    // 4) CADENCIA E COMPRIMENTO DE PASSADA (fusao de sensores)
    // ------------------------------------------------------------------
    /** Janela deslizante usada para calcular passos por minuto. */
    const val CADENCE_WINDOW_MS: Long = 6_000L

    /** Passo considerado "recente" por este tempo. */
    const val CADENCE_FRESH_MS: Long = 2_500L

    /** Chute inicial do comprimento da passada, em metros. O app aprende o
     *  seu valor real comparando GPS x cadencia enquanto voce corre. */
    const val STRIDE_INIT_M: Double = 1.10
    const val STRIDE_MIN_M: Double = 0.50
    const val STRIDE_MAX_M: Double = 2.20

    /** Velocidade do aprendizado da passada (0..1). 0.05 = aprende devagar
     *  e com seguranca; 0.2 = adapta rapido mas balanca mais. */
    const val STRIDE_LEARN_ALPHA: Double = 0.05

    /** Peso do GPS quando GPS e cadencia estao disponiveis ao mesmo tempo.
     *  0.65 = 65% GPS + 35% cadencia. O GPS da a verdade; a cadencia da a
     *  reacao rapida entre fixes (que chegam so 1x por segundo). */
    const val FUSION_GPS_WEIGHT: Double = 0.65

    // ------------------------------------------------------------------
    // 5) POLITICA DE AUDIO
    // ------------------------------------------------------------------
    /** Silencio inicial apos apertar INICIAR: da tempo do GPS engatar. */
    var warmupMs: Long = 8_000L

    /** Intervalo do som de "meta atingida" enquanto o pace esta estavel. */
    var onPaceRepeatMs: Long = 10_000L

    /** Intervalo dos bipes de correcao (agudo/grave) enquanto voce esta fora
     *  da faixa. O primeiro toca imediatamente ao entrar no estado. */
    var correctionRepeatMs: Long = 4_000L

    /** Volume dos bipes sintetizados (0.0 a 1.0). */
    var audioVolume: Float = 0.85f

    /**
     * Mapeamento dos tons. Padrao (false):
     *      RAPIDO DEMAIS (acelerou)  -> bipe AGUDO
     *      LENTO DEMAIS  (caiu)      -> bipe GRAVE
     * Coloque `true` para inverter, se voce preferir o contrario.
     */
    var invertAlertTones: Boolean = false

    // ------------------------------------------------------------------
    // 6) ESTETICA CODEC
    // ------------------------------------------------------------------
    /** Frequencia exibida no cabecalho, so por diversao. */
    const val CODEC_FREQUENCY: String = "140.85"
}

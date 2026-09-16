package br.easypace.codec.audio

import br.easypace.codec.audio.ToneSynth.Wave

/**
 * ============================================================================
 *  CATALOGO DE SONS
 * ----------------------------------------------------------------------------
 *  Cada som e um buffer PCM montado a partir de [ToneSynth]. Para redesenhar
 *  um alerta, mexa apenas nas frequencias/duracoes daqui — nada mais no app
 *  precisa mudar.
 *
 *  Referencia rapida de altura:
 *      220 Hz = La3 (grave)   |   880 Hz = La5   |   1760 Hz = La6 (agudo)
 * ============================================================================
 */
object CodecSounds {

    /**
     * ALERTA AGUDO — "voce acelerou / esta acima da meta".
     * Dois blips curtos subindo, bem penetrantes.
     */
    fun agudo(): ShortArray = ToneSynth.concat(
        ToneSynth.tone(1760.0, 2100.0, durationMs = 80, wave = Wave.CODEC, amplitude = 0.85),
        ToneSynth.silence(55),
        ToneSynth.tone(2100.0, 2350.0, durationMs = 90, wave = Wave.CODEC, amplitude = 0.9)
    )

    /**
     * ALERTA GRAVE — "voce caiu de ritmo / esta abaixo da meta".
     * Um blip longo descendo, com peso.
     */
    fun grave(): ShortArray = ToneSynth.concat(
        ToneSynth.tone(330.0, 240.0, durationMs = 170, wave = Wave.CODEC, amplitude = 0.95, releaseMs = 45),
        ToneSynth.silence(50),
        ToneSynth.tone(240.0, 190.0, durationMs = 190, wave = Wave.CODEC, amplitude = 0.9, releaseMs = 60)
    )

    /**
     * META ATINGIDA — o "chamado de codec": dois toques limpos, um ligeiro
     * intervalo, repetidos. E o som que roda a cada 10 s enquanto o pace esta
     * estavel: precisa ser agradavel de ouvir dezenas de vezes.
     */
    fun metaAtingida(): ShortArray {
        val blip1 = ToneSynth.tone(1046.5, 1046.5, durationMs = 70, wave = Wave.SENOIDE, amplitude = 0.55)
        val blip2 = ToneSynth.tone(1396.9, 1396.9, durationMs = 70, wave = Wave.SENOIDE, amplitude = 0.55)
        return ToneSynth.concat(
            blip1, ToneSynth.silence(60),
            blip2, ToneSynth.silence(140),
            blip1, ToneSynth.silence(60),
            blip2
        )
    }

    /** Confirmacao de INICIAR: o "clique" de abrir o canal. */
    fun inicio(): ShortArray = ToneSynth.concat(
        ToneSynth.tone(660.0, 990.0, durationMs = 90, wave = Wave.CODEC, amplitude = 0.7),
        ToneSynth.silence(40),
        ToneSynth.tone(1320.0, 1320.0, durationMs = 120, wave = Wave.CODEC, amplitude = 0.7)
    )

    /** Confirmacao de PARAR: canal fechando. */
    fun fim(): ShortArray = ToneSynth.concat(
        ToneSynth.tone(1320.0, 990.0, durationMs = 110, wave = Wave.CODEC, amplitude = 0.7),
        ToneSynth.silence(40),
        ToneSynth.tone(660.0, 440.0, durationMs = 180, wave = Wave.CODEC, amplitude = 0.7, releaseMs = 60)
    )
}

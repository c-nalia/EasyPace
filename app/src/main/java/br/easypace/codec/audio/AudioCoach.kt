package br.easypace.codec.audio

import br.easypace.codec.core.Config
import br.easypace.codec.core.PaceState

/**
 * ============================================================================
 *  TREINADOR SONORO
 * ----------------------------------------------------------------------------
 *  Junta a politica ([AlertPolicy]) com os sons ([CodecSounds]).
 *  O servico chama [onTick] 20x por segundo; a politica decide se algo toca.
 *
 *  Os buffers sao sintetizados UMA vez na construcao (custa poucos ms) e ficam
 *  residentes na memoria do driver, entao o disparo em corrida e imediato.
 * ============================================================================
 */
class AudioCoach {

    private val sfxAgudo = Sfx(CodecSounds.agudo())
    private val sfxGrave = Sfx(CodecSounds.grave())
    private val sfxMeta = Sfx(CodecSounds.metaAtingida())
    private val sfxInicio = Sfx(CodecSounds.inicio())
    private val sfxFim = Sfx(CodecSounds.fim())

    private val policy = AlertPolicy()

    /**
     * true quando TODOS os efeitos foram criados com sucesso.
     * Se vier false, o aparelho recusou o AudioTrack e nenhum bipe vai sair —
     * a tela mostra o aviso em vez de deixar voce correndo em silencio sem
     * entender o motivo.
     */
    val isReady: Boolean
        get() = sfxAgudo.isReady && sfxGrave.isReady && sfxMeta.isReady &&
            sfxInicio.isReady && sfxFim.isReady

    /** Ultimo cue efetivamente tocado — a UI usa para piscar em sincronia. */
    @Volatile var lastCue: Cue = Cue.NENHUM
        private set

    /** Instante (relogio monotonico) do ultimo cue. */
    @Volatile var lastCueAtMs: Long = 0L
        private set

    /** Chame a cada tick do motor. */
    fun onTick(state: PaceState, nowMs: Long) {
        val cue = policy.decide(state, nowMs)
        if (cue == Cue.NENHUM) return
        play(cue)
        lastCue = cue
        lastCueAtMs = nowMs
    }

    /** Toca um som avulso (botoes de teste da tela de ajustes). */
    fun preview(cue: Cue) = play(cue)

    fun playStart() {
        policy.reset()
        sfxInicio.play(Config.audioVolume)
    }

    fun playStop() {
        policy.reset()
        sfxFim.play(Config.audioVolume)
    }

    fun release() {
        sfxAgudo.release(); sfxGrave.release(); sfxMeta.release()
        sfxInicio.release(); sfxFim.release()
    }

    private fun play(cue: Cue) {
        when (cue) {
            Cue.AGUDO -> sfxAgudo.play(Config.audioVolume)
            Cue.GRAVE -> sfxGrave.play(Config.audioVolume)
            Cue.META -> sfxMeta.play(Config.audioVolume)
            Cue.NENHUM -> Unit
        }
    }
}

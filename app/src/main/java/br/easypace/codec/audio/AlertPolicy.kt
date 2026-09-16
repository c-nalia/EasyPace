package br.easypace.codec.audio

import br.easypace.codec.core.Config
import br.easypace.codec.core.PaceState

/** O que deve tocar neste instante. */
enum class Cue { NENHUM, AGUDO, GRAVE, META }

/**
 * ============================================================================
 *  QUANDO TOCAR O QUE
 * ----------------------------------------------------------------------------
 *  Regras (todas com prazos configuraveis em [Config]):
 *
 *   * NO_PACE ......... toca META na hora em que entra no estado e depois
 *                       repete a cada `onPaceRepeatMs` (padrao 10 s).
 *   * RAPIDO_DEMAIS ... toca AGUDO na hora e repete a cada `correctionRepeatMs`.
 *   * LENTO_DEMAIS .... toca GRAVE na hora e repete a cada `correctionRepeatMs`.
 *   * AQUECENDO/PARADO  silencio total.
 *
 *  A inversao AGUDO<->GRAVE e aplicada aqui, controlada por
 *  [Config.invertAlertTones].
 *
 *  Classe pura (sem Android) para poder ser testada com relogio falso.
 * ============================================================================
 */
class AlertPolicy {

    private var lastState: PaceState? = null
    private var lastPlayMs = Long.MIN_VALUE / 4

    /** Chame a cada tick. Devolve [Cue.NENHUM] quando nao ha nada a tocar. */
    fun decide(state: PaceState, nowMs: Long): Cue {
        val cue = when (state) {
            PaceState.NO_PACE -> Cue.META
            PaceState.RAPIDO_DEMAIS -> if (Config.invertAlertTones) Cue.GRAVE else Cue.AGUDO
            PaceState.LENTO_DEMAIS -> if (Config.invertAlertTones) Cue.AGUDO else Cue.GRAVE
            PaceState.AQUECENDO, PaceState.PARADO -> Cue.NENHUM
        }

        if (cue == Cue.NENHUM) {
            lastState = state
            return Cue.NENHUM
        }

        val interval =
            if (state == PaceState.NO_PACE) Config.onPaceRepeatMs else Config.correctionRepeatMs

        val entrouAgora = state != lastState
        val venceuOPrazo = nowMs - lastPlayMs >= interval
        lastState = state

        return if (entrouAgora || venceuOPrazo) {
            lastPlayMs = nowMs
            cue
        } else {
            Cue.NENHUM
        }
    }

    /** Esquece o historico (use ao iniciar/parar uma sessao). */
    fun reset() {
        lastState = null
        lastPlayMs = Long.MIN_VALUE / 4
    }
}

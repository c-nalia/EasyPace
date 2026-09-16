package br.easypace.codec

import br.easypace.codec.audio.AlertPolicy
import br.easypace.codec.audio.Cue
import br.easypace.codec.core.Config
import br.easypace.codec.core.PaceState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlertPolicyTest {

    @Before
    fun padroes() {
        Config.onPaceRepeatMs = 10_000
        Config.correctionRepeatMs = 4_000
        Config.invertAlertTones = false
    }

    @Test
    fun metaRepeteACada10Segundos() {
        val p = AlertPolicy()
        assertEquals(Cue.META, p.decide(PaceState.NO_PACE, 0))
        assertEquals(Cue.NENHUM, p.decide(PaceState.NO_PACE, 5_000))
        assertEquals(Cue.NENHUM, p.decide(PaceState.NO_PACE, 9_999))
        assertEquals(Cue.META, p.decide(PaceState.NO_PACE, 10_000))
        assertEquals(Cue.NENHUM, p.decide(PaceState.NO_PACE, 15_000))
        assertEquals(Cue.META, p.decide(PaceState.NO_PACE, 20_000))
    }

    @Test
    fun trocaDeEstadoTocaImediatamente() {
        val p = AlertPolicy()
        assertEquals(Cue.META, p.decide(PaceState.NO_PACE, 0))
        // 1 segundo depois o corredor acelera: nao espera o prazo.
        assertEquals(Cue.AGUDO, p.decide(PaceState.RAPIDO_DEMAIS, 1_000))
        assertEquals(Cue.NENHUM, p.decide(PaceState.RAPIDO_DEMAIS, 2_000))
        assertEquals(Cue.AGUDO, p.decide(PaceState.RAPIDO_DEMAIS, 5_000))
    }

    @Test
    fun lentoTocaGrave() {
        val p = AlertPolicy()
        assertEquals(Cue.GRAVE, p.decide(PaceState.LENTO_DEMAIS, 0))
    }

    @Test
    fun inversaoTrocaOsTons() {
        Config.invertAlertTones = true
        val p = AlertPolicy()
        assertEquals(Cue.GRAVE, p.decide(PaceState.RAPIDO_DEMAIS, 0))
        assertEquals(Cue.AGUDO, p.decide(PaceState.LENTO_DEMAIS, 1_000))
    }

    @Test
    fun silencioAoAquecerEParado() {
        val p = AlertPolicy()
        assertEquals(Cue.NENHUM, p.decide(PaceState.AQUECENDO, 0))
        assertEquals(Cue.NENHUM, p.decide(PaceState.PARADO, 30_000))
    }
}

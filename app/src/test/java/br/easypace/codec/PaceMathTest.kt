package br.easypace.codec

import br.easypace.codec.core.PaceMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Testes das conversoes puras. Rode com: build.bat test */
class PaceMathTest {

    @Test
    fun converteVelocidadeEmPace() {
        // 3.3333 m/s = 1 km a cada 300 s = 5:00 min/km
        assertEquals(300.0, PaceMath.speedToPace(1000.0 / 300.0)!!, 0.001)
        assertNull(PaceMath.speedToPace(0.0))
    }

    @Test
    fun formataPace() {
        assertEquals("5:00", PaceMath.formatPace(300.0))
        assertEquals("5:30", PaceMath.formatPace(330.0))
        assertEquals("4:07", PaceMath.formatPace(247.0))
        assertEquals("--:--", PaceMath.formatPace(null))
    }

    @Test
    fun formataDelta() {
        assertEquals("+12s", PaceMath.formatDelta(12.4))
        assertEquals("-7s", PaceMath.formatDelta(-7.0))
        assertEquals("---", PaceMath.formatDelta(null))
    }

    @Test
    fun leOPaceDigitado() {
        assertEquals(330, PaceMath.parsePace("5:30"))
        assertEquals(330, PaceMath.parsePace(" 5'30 "))
        assertEquals(300, PaceMath.parsePace("300"))
        assertNull(PaceMath.parsePace("abc"))
        assertNull(PaceMath.parsePace("1:00"))   // fora da faixa permitida
    }

    @Test
    fun alphaDoFiltroRespeitaOTempoReal() {
        // Um passo igual a constante de tempo deve consumir ~63% do erro.
        assertEquals(0.632, PaceMath.emaAlpha(3000, 3.0), 0.01)
        // Passo zero nao altera nada.
        assertEquals(0.0, PaceMath.emaAlpha(0, 3.0), 1e-9)
    }

    @Test
    fun formataDistanciaETempo() {
        assertEquals("480 m", PaceMath.formatDistance(480.0))
        assertEquals("1.23 km", PaceMath.formatDistance(1234.0))
        assertEquals("02:05", PaceMath.formatClock(125_000))
        assertEquals("1:00:00", PaceMath.formatClock(3_600_000))
    }
}

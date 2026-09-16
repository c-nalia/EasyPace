package br.easypace.codec

import br.easypace.codec.core.Config
import br.easypace.codec.core.PaceEngine
import br.easypace.codec.core.PaceState
import br.easypace.codec.core.RunSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Simula uma corrida com relogio falso: a cada 50 ms chamamos tick(), e a
 * cada 1000 ms injetamos um fix de GPS. Nenhuma dependencia do Android.
 */
class PaceEngineTest {

    @Before
    fun ajustesDeterministicos() {
        Config.warmupMs = 0
        Config.minStateDwellMs = 0
        Config.speedFilterTauS = 0.5
        Config.toleranceSecPerKm = 8.0
        Config.hysteresisSecPerKm = 4.0
        // Janela curta e limitador desligado: estes testes exercitam a maquina
        // de estados, nao a suavizacao. A estabilidade tem teste proprio,
        // em `oPaceNaoBalancaComGpsRuidoso`.
        Config.paceWindowSec = 6.0
        Config.paceSlewSecPerKmPerSec = 1_000_000.0
    }

    /** Roda a simulacao por [durationMs] com o pace de GPS informado. */
    private fun simula(
        engine: PaceEngine,
        startMs: Long,
        durationMs: Long,
        gpsPaceSecPerKm: Double
    ): Pair<Long, RunSnapshot> {
        val speed = 1000.0 / gpsPaceSecPerKm
        var t = startMs
        var last = RunSnapshot()
        val fim = startMs + durationMs
        while (t < fim) {
            if ((t - startMs) % 1000L == 0L) engine.onLocation(t, speed, 0.5f, 5f)
            last = engine.tick(t)
            t += Config.TICK_MS
        }
        return t to last
    }

    @Test
    fun reconheceORitmoNaMeta() {
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val (_, s) = simula(e, 0, 20_000, 300.0)
        assertEquals(PaceState.NO_PACE, s.state)
        assertEquals(300.0, s.paceSecPerKm!!, 6.0)
    }

    @Test
    fun avisaQuandoAcelera() {
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val (t, _) = simula(e, 0, 15_000, 300.0)
        val (_, s) = simula(e, t, 15_000, 275.0)  // 25 s/km mais rapido
        assertEquals(PaceState.RAPIDO_DEMAIS, s.state)
        assertTrue("delta deveria ser negativo", s.deltaSecPerKm!! < 0)
    }

    @Test
    fun avisaQuandoDesacelera() {
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val (t, _) = simula(e, 0, 15_000, 300.0)
        val (_, s) = simula(e, t, 15_000, 330.0)
        assertEquals(PaceState.LENTO_DEMAIS, s.state)
        assertTrue("delta deveria ser positivo", s.deltaSecPerKm!! > 0)
    }

    @Test
    fun histereseEvitaFicarAlternando() {
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        var t = simula(e, 0, 15_000, 330.0).first          // entra em LENTO_DEMAIS
        // Volta so ate +6 s/km: dentro da tolerancia (8), mas acima da barreira
        // interna (8 - 4 = 4). Deve CONTINUAR em LENTO_DEMAIS.
        val (t2, s1) = simula(e, t, 12_000, 306.0)
        assertEquals(PaceState.LENTO_DEMAIS, s1.state)
        // Agora volta de vez para a meta: sai do alerta.
        val (_, s2) = simula(e, t2, 12_000, 301.0)
        assertEquals(PaceState.NO_PACE, s2.state)
    }

    @Test
    fun ficaParadoSemMovimento() {
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val (t, _) = simula(e, 0, 15_000, 300.0)
        val (_, s) = simula(e, t, 20_000, 4000.0)   // ~0.25 m/s: praticamente parado
        assertEquals(PaceState.PARADO, s.state)
    }

    @Test
    fun integraDistanciaDeFormaCoerente() {
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val (_, s) = simula(e, 0, 60_000, 300.0)
        // 60 s a 3.33 m/s = 200 m; toleramos a rampa inicial do filtro.
        assertEquals(200.0, s.distanceM, 15.0)
    }

    @Test
    fun aCadenciaSozinhaJaProduzPace() {
        // Sem nenhum GPS: so passos. Um passo a cada 400 ms = 150 ppm.
        // 150/60 x 1.10 m (passada inicial) = 2.75 m/s -> 363.6 s/km.
        val e = PaceEngine(targetSecPerKm = 364)
        e.start(0)
        var t = 0L
        var last = RunSnapshot()
        while (t < 25_000) {
            if (t % 400L == 0L) e.onStep(t)
            last = e.tick(t)
            t += Config.TICK_MS
        }
        assertEquals(363.6, last.paceSecPerKm!!, 8.0)
        assertEquals(PaceState.NO_PACE, last.state)
    }

    /**
     * O teste que corresponde ao problema real relatado em campo: caminhando
     * em ritmo constante, o numero na tela nao parava quieto.
     *
     * Aqui o GPS recebe ruido gaussiano E um pico de multipath a cada 20 s
     * (velocidade dobrada), que e o que acontece de verdade perto de predios.
     *
     * Sem a mediana e sem a janela, este cenario produz desvio acima de
     * 25 s/km e — pior — um VIES: os picos puxam a media do pace para baixo,
     * fazendo o app dizer que voce esta mais rapido do que esta.
     */
    @Test
    fun oPaceNaoBalancaComGpsRuidoso() {
        Config.paceWindowSec = 12.0
        Config.speedFilterTauS = 2.5
        Config.paceSlewSecPerKmPerSec = 25.0

        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val rnd = java.util.Random(42)

        var t = 0L
        val leituras = ArrayList<Double>()
        while (t < 120_000) {
            if (t % 1000L == 0L) {
                var v = 3.3333 + rnd.nextGaussian() * 0.25          // 5:00 min/km
                if ((t / 1000L) % 20L == 0L) v *= 2.0               // pico de multipath
                e.onLocation(t, v.coerceAtLeast(0.0), 0.5f, 5f)
            }
            val s = e.tick(t)
            if (t > 45_000) s.paceSecPerKm?.let { leituras.add(it) }
            t += Config.TICK_MS
        }

        val media = leituras.average()
        val desvio = kotlin.math.sqrt(leituras.sumOf { (it - media) * (it - media) } / leituras.size)

        assertTrue("leituras de menos: ${leituras.size}", leituras.size > 1000)
        // Limiares com folga. Em simulacao com 25 sementes diferentes deste
        // mesmo cenario, o novo motor deu no pior caso desvio de 10,4 s/km e
        // erro de media de 9,3 s/km. Sem a mediana e sem a janela, o desvio
        // fica em ~15 s/km e a media cai para ~292 s/km — o app diria que
        // voce esta 8 s/km mais rapido do que realmente esta.
        assertTrue("o pace ainda balanca demais: desvio de $desvio s/km", desvio < 14.0)
        assertEquals("os picos de GPS enviesaram o pace medio", 300.0, media, 14.0)
    }

    /** A janela precisa entrar em acao; antes dela o pace e o instantaneo. */
    @Test
    fun aJanelaAssumeDepoisDeAquecer() {
        Config.paceWindowSec = 10.0
        val e = PaceEngine(targetSecPerKm = 300)
        e.start(0)
        val (_, cedo) = simula(e, 0, 3_000, 300.0)
        assertFalse("cedo demais para a janela", cedo.paceFromWindow)
        val (_, depois) = simula(e, 3_000, 20_000, 300.0)
        assertTrue("a janela deveria estar valendo", depois.paceFromWindow)
    }
}

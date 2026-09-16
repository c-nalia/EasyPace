package br.easypace.codec

import br.easypace.codec.audio.CodecSounds
import br.easypace.codec.audio.ToneSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {

    @Test
    fun duracaoBateComATaxaDeAmostragem() {
        val pcm = ToneSynth.tone(1000.0, durationMs = 100)
        assertEquals(ToneSynth.SAMPLE_RATE / 10, pcm.size)
    }

    @Test
    fun envelopeEvitaEstalo() {
        val pcm = ToneSynth.tone(1000.0, durationMs = 100)
        // Primeira e ultima amostras praticamente em zero.
        assertTrue(abs(pcm.first().toInt()) < 500)
        assertTrue(abs(pcm.last().toInt()) < 500)
    }

    @Test
    fun naoEstoura16Bits() {
        for (som in listOf(CodecSounds.agudo(), CodecSounds.grave(), CodecSounds.metaAtingida())) {
            assertTrue(som.isNotEmpty())
            // Nenhuma amostra colada no limite (sinal de clipping).
            val colados = som.count { abs(it.toInt()) >= Short.MAX_VALUE - 1 }
            assertTrue("clipping em $colados amostras", colados < som.size / 100)
        }
    }

    @Test
    fun concatSomaOsTamanhos() {
        val a = ToneSynth.tone(440.0, durationMs = 50)
        val b = ToneSynth.silence(50)
        assertEquals(a.size + b.size, ToneSynth.concat(a, b).size)
    }
}

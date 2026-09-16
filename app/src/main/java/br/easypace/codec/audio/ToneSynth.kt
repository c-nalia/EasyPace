package br.easypace.codec.audio

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * ============================================================================
 *  SINTETIZADOR DE TONS (PCM 16 bits, mono)
 * ----------------------------------------------------------------------------
 *  Gera os bipes por calculo, sem nenhum arquivo de audio no APK. Vantagens:
 *  APK minusculo, latencia baixissima e qualquer som pode ser reajustado
 *  mudando numeros — nada de abrir editor de audio.
 *
 *  Como o codigo e Kotlin puro (sem Android), da para testa-lo na JVM.
 * ============================================================================
 */
object ToneSynth {

    /** 44.1 kHz: taxa universal, aceita por qualquer aparelho. */
    const val SAMPLE_RATE = 44_100

    /** Formatos de onda disponiveis. */
    enum class Wave {
        /** Senoide pura: doce, discreta. */
        SENOIDE,

        /** Senoide + 3o e 5o harmonicos: o "buzz" eletronico do codec. */
        CODEC,

        /** Quadrada: bem agressiva, corta o ruido da rua. */
        QUADRADA
    }

    /**
     * Gera um tom com glissando opcional (a frequencia desliza de inicio a fim).
     *
     * @param freqStartHz frequencia no inicio
     * @param freqEndHz   frequencia no fim (igual a freqStartHz = tom fixo)
     * @param durationMs  duracao
     * @param wave        timbre
     * @param amplitude   0.0..1.0
     * @param attackMs    rampa de subida — evita o "clique" no ataque
     * @param releaseMs   rampa de descida — evita o "clique" no fim
     */
    fun tone(
        freqStartHz: Double,
        freqEndHz: Double = freqStartHz,
        durationMs: Int,
        wave: Wave = Wave.CODEC,
        amplitude: Double = 0.9,
        attackMs: Int = 5,
        releaseMs: Int = 20
    ): ShortArray {
        val n = (SAMPLE_RATE * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val out = ShortArray(n)

        val attack = (SAMPLE_RATE * attackMs / 1000.0).toInt().coerceAtLeast(1)
        val release = (SAMPLE_RATE * releaseMs / 1000.0).toInt().coerceAtLeast(1)

        // Acumulador de fase: garante continuidade mesmo com a frequencia variando.
        var phase = 0.0

        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = freqStartHz + (freqEndHz - freqStartHz) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            val sample = when (wave) {
                Wave.SENOIDE -> sin(phase)
                Wave.CODEC -> (sin(phase) + 0.35 * sin(3 * phase) + 0.15 * sin(5 * phase)) / 1.5
                Wave.QUADRADA -> if (sin(phase) >= 0) 0.7 else -0.7
            }

            // Envelope trapezoidal
            val env = min(
                if (i < attack) i.toDouble() / attack else 1.0,
                if (i > n - release) (n - i).toDouble() / release else 1.0
            ).coerceIn(0.0, 1.0)

            out[i] = (sample * env * amplitude * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    /** Trecho de silencio, usado como espacador entre bipes. */
    fun silence(durationMs: Int): ShortArray =
        ShortArray((SAMPLE_RATE * durationMs / 1000.0).toInt().coerceAtLeast(1))

    /** Cola varios trechos em um unico buffer. */
    fun concat(vararg parts: ShortArray): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }

    /** Soma dois buffers (mistura). O menor e completado com silencio. */
    fun mix(a: ShortArray, b: ShortArray, gainA: Double = 1.0, gainB: Double = 1.0): ShortArray {
        val out = ShortArray(maxOf(a.size, b.size))
        for (i in out.indices) {
            val va = if (i < a.size) a[i] * gainA else 0.0
            val vb = if (i < b.size) b[i] * gainB else 0.0
            out[i] = (va + vb).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }
}

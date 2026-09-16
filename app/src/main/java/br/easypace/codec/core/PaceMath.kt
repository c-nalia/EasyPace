package br.easypace.codec.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Conversoes e formatacoes puras (sem Android, sem estado).
 * Por serem puras, todas sao testadas em `app/src/test`.
 */
object PaceMath {

    /** m/s -> segundos por km. Retorna null se a velocidade for desprezivel. */
    fun speedToPace(speedMps: Double): Double? =
        if (speedMps <= 0.05) null else 1000.0 / speedMps

    /** segundos por km -> m/s. */
    fun paceToSpeed(secPerKm: Double): Double =
        if (secPerKm <= 0.0) 0.0 else 1000.0 / secPerKm

    /**
     * Coeficiente do filtro exponencial (EMA) para um passo de tempo qualquer.
     *
     * Usar `1 - e^(-dt/tau)` em vez de um alpha fixo faz o filtro se comportar
     * igual mesmo que o loop atrase — o que importa e o tempo real decorrido.
     *
     * @param dtMs tempo desde a ultima amostra, em ms
     * @param tauS constante de tempo desejada, em segundos
     */
    fun emaAlpha(dtMs: Long, tauS: Double): Double {
        if (tauS <= 0.0) return 1.0
        val dt = dtMs.coerceAtLeast(0L) / 1000.0
        return 1.0 - exp(-dt / tauS)
    }

    /** 330.0 -> "5:30". null -> "--:--". */
    fun formatPace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite()) return "--:--"
        if (secPerKm > 3599) return ">59:59"
        val total = secPerKm.roundToInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** Diferenca com sinal: 12.4 -> "+12s", -7.0 -> "-7s". */
    fun formatDelta(deltaSec: Double?): String {
        if (deltaSec == null) return "---"
        val v = deltaSec.roundToInt()
        return if (v >= 0) "+${v}s" else "${v}s"
    }

    /** 1234.0 -> "1.23 km"; 480.0 -> "480 m". */
    fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else "%.2f km".format(meters / 1000.0)

    /** ms -> "MM:SS" (ou "H:MM:SS" acima de uma hora). */
    fun formatClock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    /** "5:30" (ou "530"/"330") digitado pelo usuario -> segundos por km. */
    fun parsePace(text: String): Int? {
        val t = text.trim()
        val parts = t.split(":", "'", ".")
        return try {
            when (parts.size) {
                2 -> parts[0].trim().toInt() * 60 + parts[1].trim().toInt()
                1 -> t.toIntOrNull()
                else -> null
            }?.takeIf { it in Config.MIN_TARGET_PACE_SEC_PER_KM..Config.MAX_TARGET_PACE_SEC_PER_KM }
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Normaliza o desvio para -1..+1, util para desenhar a barra do medidor. */
    fun normalizedDeviation(deltaSec: Double?, fullScaleSec: Double): Float {
        if (deltaSec == null || fullScaleSec <= 0.0) return 0f
        val v = (deltaSec / fullScaleSec).coerceIn(-1.0, 1.0)
        return if (abs(v) < 1e-4) 0f else v.toFloat()
    }
}

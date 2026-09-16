package br.easypace.codec.core

import android.content.Context

/**
 * Persistencia simples das preferencias em SharedPreferences.
 *
 * Para adicionar um novo ajuste persistente:
 *   1. crie a `var` em [Config];
 *   2. leia em [load] e grave em [save].
 */
object Prefs {

    private const val FILE = "easypace_prefs"

    private const val K_TARGET = "target_sec_per_km"
    private const val K_TOLERANCE = "tolerance"
    private const val K_HYSTERESIS = "hysteresis"
    private const val K_DWELL = "dwell_ms"
    private const val K_TAU = "filter_tau"
    private const val K_WINDOW = "pace_window_sec"
    private const val K_SLEW = "pace_slew"
    private const val K_WARMUP = "warmup_ms"
    private const val K_ON_PACE = "on_pace_repeat_ms"
    private const val K_CORRECTION = "correction_repeat_ms"
    private const val K_VOLUME = "volume"
    private const val K_INVERT = "invert_tones"

    /** Le tudo do disco e aplica em [Config]. Devolve o pace-alvo salvo. */
    fun load(context: Context): Int {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        Config.toleranceSecPerKm = p.getFloat(K_TOLERANCE, 8.0f).toDouble()
        Config.hysteresisSecPerKm = p.getFloat(K_HYSTERESIS, 4.0f).toDouble()
        Config.minStateDwellMs = p.getLong(K_DWELL, 2_500L)
        Config.speedFilterTauS = p.getFloat(K_TAU, 2.5f).toDouble()
        Config.paceWindowSec = p.getFloat(K_WINDOW, 12.0f).toDouble()
        Config.paceSlewSecPerKmPerSec = p.getFloat(K_SLEW, 25.0f).toDouble()
        Config.warmupMs = p.getLong(K_WARMUP, 8_000L)
        Config.onPaceRepeatMs = p.getLong(K_ON_PACE, 10_000L)
        Config.correctionRepeatMs = p.getLong(K_CORRECTION, 4_000L)
        Config.audioVolume = p.getFloat(K_VOLUME, 0.85f)
        Config.invertAlertTones = p.getBoolean(K_INVERT, false)
        return p.getInt(K_TARGET, Config.DEFAULT_TARGET_PACE_SEC_PER_KM)
    }

    /** Grava o estado atual de [Config] mais o pace-alvo informado. */
    fun save(context: Context, targetSecPerKm: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            putInt(K_TARGET, targetSecPerKm)
            putFloat(K_TOLERANCE, Config.toleranceSecPerKm.toFloat())
            putFloat(K_HYSTERESIS, Config.hysteresisSecPerKm.toFloat())
            putLong(K_DWELL, Config.minStateDwellMs)
            putFloat(K_TAU, Config.speedFilterTauS.toFloat())
            putFloat(K_WINDOW, Config.paceWindowSec.toFloat())
            putFloat(K_SLEW, Config.paceSlewSecPerKmPerSec.toFloat())
            putLong(K_WARMUP, Config.warmupMs)
            putLong(K_ON_PACE, Config.onPaceRepeatMs)
            putLong(K_CORRECTION, Config.correctionRepeatMs)
            putFloat(K_VOLUME, Config.audioVolume)
            putBoolean(K_INVERT, Config.invertAlertTones)
        }.apply()
    }
}

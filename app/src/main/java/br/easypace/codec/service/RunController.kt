package br.easypace.codec.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import br.easypace.codec.core.Config
import br.easypace.codec.core.Prefs
import br.easypace.codec.core.RunSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ============================================================================
 *  PONTE ENTRE O SERVICO E A TELA
 * ----------------------------------------------------------------------------
 *  Um unico objeto (singleton) com StateFlows. O servico escreve, a UI le.
 *  Assim a Activity pode ser destruida e recriada (girar a tela, voltar do
 *  segundo plano) sem perder nada — o servico segue vivo.
 * ============================================================================
 */
object RunController {

    // --- retrato da corrida ------------------------------------------------
    private val _snapshot = MutableStateFlow(RunSnapshot())
    val snapshot: StateFlow<RunSnapshot> = _snapshot.asStateFlow()

    // --- meta de pace ------------------------------------------------------
    private val _targetSecPerKm = MutableStateFlow(Config.DEFAULT_TARGET_PACE_SEC_PER_KM)
    val targetSecPerKm: StateFlow<Int> = _targetSecPerKm.asStateFlow()

    // --- o servico esta rodando? ------------------------------------------
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    // --- qual sensor de passos foi escolhido ------------------------------
    private val _sensorMode = MutableStateFlow("--")
    val sensorMode: StateFlow<String> = _sensorMode.asStateFlow()

    // --- o motor de audio conseguiu se inicializar? -----------------------
    private val _audioReady = MutableStateFlow(true)
    val audioReady: StateFlow<Boolean> = _audioReady.asStateFlow()

    private var loaded = false

    /** Carrega as preferencias do disco uma unica vez. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        _targetSecPerKm.value = Prefs.load(context)
        loaded = true
    }

    fun setTarget(context: Context, secPerKm: Int) {
        val v = secPerKm.coerceIn(Config.MIN_TARGET_PACE_SEC_PER_KM, Config.MAX_TARGET_PACE_SEC_PER_KM)
        _targetSecPerKm.value = v
        Prefs.save(context, v)
    }

    /** Grava os ajustes de [Config] que a tela alterou. */
    fun persistSettings(context: Context) = Prefs.save(context, _targetSecPerKm.value)

    // --- comandos ----------------------------------------------------------
    fun start(context: Context) {
        val i = Intent(context, RunService::class.java).setAction(RunService.ACTION_START)
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context) {
        val i = Intent(context, RunService::class.java).setAction(RunService.ACTION_STOP)
        context.startService(i)
    }

    // --- usados apenas pelo servico ---------------------------------------
    internal fun publish(s: RunSnapshot) { _snapshot.value = s }
    internal fun setActive(v: Boolean) { _active.value = v }
    internal fun setSensorMode(v: String) { _sensorMode.value = v }
    internal fun setAudioReady(v: Boolean) { _audioReady.value = v }

    /** A tela tambem cria um AudioCoach (para os botoes de teste) e reporta. */
    fun setAudioReadyFromUi(v: Boolean) { _audioReady.value = v }
}

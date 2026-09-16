package br.easypace.codec.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import br.easypace.codec.MainActivity
import br.easypace.codec.R
import br.easypace.codec.audio.AudioCoach
import br.easypace.codec.core.Config
import br.easypace.codec.core.PaceEngine
import br.easypace.codec.core.PaceMath
import br.easypace.codec.core.PaceState
import br.easypace.codec.core.RunSnapshot
import br.easypace.codec.sensors.LocationSource
import br.easypace.codec.sensors.MotionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ============================================================================
 *  SERVICO EM PRIMEIRO PLANO — o coracao que bate 20x por segundo
 * ----------------------------------------------------------------------------
 *  Por que um Service e nao a Activity: com a tela apagada (ou o celular no
 *  bolso), a Activity e congelada pelo sistema. Um foreground service com
 *  notificacao persistente continua rodando — e a unica forma suportada de
 *  monitorar uma corrida inteira.
 *
 *  Estrutura do loop:
 *      relogio monotonico (SystemClock.elapsedRealtime) -> engine.tick ->
 *      publica o snapshot -> audio decide -> dorme ate o proximo deadline
 *
 *  O loop usa "deadline fixo" em vez de `delay(50)` puro: se um tick demorar,
 *  o proximo compensa e a cadencia media fica exatamente em TICK_HZ.
 * ============================================================================
 */
class RunService : Service() {

    private val engine = PaceEngine()
    private lateinit var coach: AudioCoach
    private lateinit var location: LocationSource
    private lateinit var motion: MotionSource

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    private var lastNotifUpdateMs = 0L
    private var lastUiPublishMs = 0L
    private var lastPublishedState: PaceState? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        coach = AudioCoach()
        RunController.setAudioReady(coach.isReady)
        location = LocationSource(this)
        motion = MotionSource(this)
        RunController.ensureLoaded(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        // START_STICKY: se o sistema matar o servico por falta de memoria, ele volta.
        return START_STICKY
    }

    // =======================================================================
    //  INICIO / FIM
    // =======================================================================

    private fun startTracking() {
        if (loopJob?.isActive == true) return

        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(RunSnapshot(targetSecPerKm = RunController.targetSecPerKm.value)),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (e: Exception) {
            // Acontece se a permissao de localizacao foi negada (Android 14+).
            Log.e(TAG, "Nao foi possivel entrar em primeiro plano", e)
            stopSelf()
            return
        }

        acquireWakeLock()

        val now = SystemClock.elapsedRealtime()
        engine.targetSecPerKm = RunController.targetSecPerKm.value
        engine.start(now)

        location.start { speed, speedAcc, horizAcc, fixMs ->
            engine.onLocation(fixMs, speed, speedAcc, horizAcc)
        }
        motion.start { stepMs -> engine.onStep(stepMs) }

        RunController.setSensorMode(motion.modo)
        RunController.setActive(true)

        coach.playStart()
        loopJob = scope.launch { runLoop() }
    }

    private fun stopTracking() {
        loopJob?.cancel()
        loopJob = null
        location.stop()
        motion.stop()
        engine.stop()
        if (RunController.active.value) coach.playStop()
        releaseWakeLock()
        RunController.setActive(false)
        RunController.publish(RunSnapshot(targetSecPerKm = RunController.targetSecPerKm.value))
    }

    override fun onDestroy() {
        stopTracking()
        coach.release()
        scope.cancel()
        super.onDestroy()
    }

    // =======================================================================
    //  LOOP PRINCIPAL
    // =======================================================================

    private suspend fun runLoop() {
        var deadline = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            deadline += Config.TICK_MS

            val now = SystemClock.elapsedRealtime()

            // A meta pode ter sido alterada na tela durante a corrida.
            engine.targetSecPerKm = RunController.targetSecPerKm.value

            val snapshot = engine.tick(now)

            // O audio recebe TODOS os ticks (20 Hz): e ele que precisa reagir.
            coach.onTick(snapshot.state, now)

            // A tela recebe menos (UI_UPDATE_HZ). Um digito trocando 20x por
            // segundo parece instavel mesmo com a medicao boa — e ainda faz o
            // Compose redesenhar 20x por segundo a toa. Mudanca de estado
            // passa na hora, para o aviso visual nao atrasar.
            if (now - lastUiPublishMs >= UI_PUBLISH_MS || snapshot.state != lastPublishedState) {
                lastUiPublishMs = now
                lastPublishedState = snapshot.state
                RunController.publish(snapshot)
            }

            // A notificacao so precisa de 1 Hz; atualizar a 20 Hz gastaria bateria.
            if (now - lastNotifUpdateMs >= NOTIF_UPDATE_MS) {
                lastNotifUpdateMs = now
                notificationManager().notify(NOTIF_ID, buildNotification(snapshot))
            }

            val sleep = deadline - SystemClock.elapsedRealtime()
            if (sleep > 0) {
                delay(sleep)
            } else {
                // Ficamos atrasados (CPU ocupada): reancora para nao acumular divida.
                deadline = SystemClock.elapsedRealtime()
            }
        }
    }

    // =======================================================================
    //  NOTIFICACAO
    // =======================================================================

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW   // LOW = notificacao sem som proprio
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(s: RunSnapshot): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val parar = PendingIntent.getService(
            this, 1,
            Intent(this, RunService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val estado = when (s.state) {
            PaceState.AQUECENDO -> "CALIBRANDO"
            PaceState.PARADO -> "PARADO"
            PaceState.RAPIDO_DEMAIS -> "RAPIDO DEMAIS"
            PaceState.NO_PACE -> "NO PACE"
            PaceState.LENTO_DEMAIS -> "LENTO DEMAIS"
        }

        val titulo = "${PaceMath.formatPace(s.paceSecPerKm)} /km  -  $estado"
        val texto = "meta ${PaceMath.formatPace(s.targetSecPerKm.toDouble())}  -  " +
            "${PaceMath.formatDistance(s.distanceM)}  -  ${PaceMath.formatClock(s.elapsedMs)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(abrir)
            .addAction(0, "PARAR", parar)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // =======================================================================
    //  WAKE LOCK — mantem a CPU acordada com a tela apagada
    // =======================================================================

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyPace::corrida").apply {
            setReferenceCounted(false)
            acquire(MAX_RUN_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "RunService"
        const val ACTION_START = "br.easypace.codec.START"
        const val ACTION_STOP = "br.easypace.codec.STOP"

        private const val CHANNEL_ID = "easypace_run"
        private const val NOTIF_ID = 1985
        private const val NOTIF_UPDATE_MS = 1_000L
        private val UI_PUBLISH_MS = 1000L / Config.UI_UPDATE_HZ

        /** Limite de seguranca do wake lock: 6 horas. */
        private const val MAX_RUN_MS = 6L * 60 * 60 * 1000
    }
}

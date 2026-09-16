package br.easypace.codec.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * ============================================================================
 *  FONTE DE MOVIMENTO (passos / cadencia)
 * ----------------------------------------------------------------------------
 *  Por que isso existe: o GPS entrega 1 leitura por segundo. Se o app dependesse
 *  so dele, voce mudaria de ritmo e ouviria o aviso um segundo depois — e ainda
 *  com o atraso do filtro. A cadencia (passos por minuto) reage em ~300 ms.
 *
 *  Duas estrategias, escolhidas automaticamente:
 *   1. TYPE_STEP_DETECTOR — sensor dedicado, feito em hardware, gasta quase
 *      nada de bateria. Precisa da permissao ACTIVITY_RECOGNITION no Android 10+.
 *   2. Acelerometro cru — plano B universal. Filtramos a gravidade com um passa-
 *      altas e contamos os picos acima de um limiar, com periodo refratario.
 *
 *  Para calibrar a deteccao por acelerometro, mexa em PEAK_THRESHOLD e
 *  REFRACTORY_MS.
 * ============================================================================
 */
class MotionSource(private val context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var listener: SensorEventListener? = null
    private var onStep: ((Long) -> Unit)? = null

    /** Qual estrategia acabou sendo usada (util para mostrar na tela). */
    @Volatile var modo: String = "nenhum"
        private set

    // --- estado do detector por acelerometro -------------------------------
    private var gravityEma = 9.81
    private var armed = true
    private var lastStepMs = 0L

    fun hasActivityPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

    /** @param onStepDetected recebe o instante monotonico de cada passo. */
    fun start(onStepDetected: (Long) -> Unit) {
        stop()
        onStep = onStepDetected

        val stepSensor =
            if (hasActivityPermission()) sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            else null

        if (stepSensor != null) {
            modo = "sensor de passos"
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // O sensor emite um evento por passo (values[0] == 1.0).
                    onStep?.invoke(SystemClock.elapsedRealtime())
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)
            return
        }

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel == null) {
            modo = "indisponivel"
            Log.w(TAG, "Aparelho sem acelerometro — o app vai depender so do GPS.")
            return
        }
        modo = "acelerometro"
        gravityEma = 9.81
        armed = true
        lastStepMs = 0L
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onAcceleration(event)
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        // SENSOR_DELAY_GAME ~= 50 Hz: suficiente para picos de passada (2-3 Hz).
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        onStep = null
    }

    // -----------------------------------------------------------------------
    //  Deteccao de passo por pico do acelerometro
    // -----------------------------------------------------------------------
    private fun onAcceleration(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())

        // Passa-altas simples: tira a gravidade e a orientacao lenta do celular.
        gravityEma += GRAVITY_ALPHA * (magnitude - gravityEma)
        val dynamic = magnitude - gravityEma

        val now = SystemClock.elapsedRealtime()
        if (armed && dynamic > PEAK_THRESHOLD && now - lastStepMs > REFRACTORY_MS) {
            armed = false
            lastStepMs = now
            onStep?.invoke(now)
        } else if (!armed && dynamic < RE_ARM_THRESHOLD) {
            // So aceita um novo pico depois que o sinal voltar para baixo.
            armed = true
        }
    }

    private companion object {
        const val TAG = "MotionSource"

        /** Velocidade com que a "gravidade" e reestimada (0..1, por amostra). */
        const val GRAVITY_ALPHA = 0.02

        /** Aceleracao dinamica (m/s^2) que caracteriza uma pisada. */
        const val PEAK_THRESHOLD = 2.2

        /** Abaixo disto o detector rearma para o proximo passo. */
        const val RE_ARM_THRESHOLD = 0.6

        /** Intervalo minimo entre passos (ms). 240 ms = teto de 250 passos/min. */
        const val REFRACTORY_MS = 240L
    }
}

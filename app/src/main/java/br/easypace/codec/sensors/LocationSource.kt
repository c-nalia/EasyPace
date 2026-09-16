package br.easypace.codec.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * ============================================================================
 *  FONTE DE LOCALIZACAO (GPS fundido)
 * ----------------------------------------------------------------------------
 *  Usa o FusedLocationProvider do Google Play Services, que combina GPS,
 *  Wi-Fi e sensores internos — bem mais estavel que o LocationManager cru.
 *
 *  Pedimos 1 atualizacao por segundo (o maximo util na pratica: o chip de GPS
 *  do celular resolve posicao a 1 Hz). A responsividade "entre fixes" vem da
 *  cadencia, tratada em [MotionSource].
 *
 *  Para trocar a frequencia, mexa em INTERVAL_MS abaixo.
 * ============================================================================
 */
class LocationSource(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var callback: LocationCallback? = null

    /** true se o app tem permissao de localizacao precisa. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Comeca a receber fixes.
     *
     * @param onFix chamado a cada leitura: (velocidade m/s, incerteza da
     *              velocidade em m/s ou null, raio de erro em metros ou null,
     *              instante monotonico do fix em ms)
     * @return false se faltar permissao
     */
    @SuppressLint("MissingPermission")
    fun start(onFix: (Double, Float?, Float?, Long) -> Unit): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Sem permissao de localizacao — GPS nao sera usado.")
            return false
        }
        stop()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                if (!loc.hasSpeed()) return

                val speedAcc: Float? =
                    if (loc.hasSpeedAccuracy()) loc.speedAccuracyMetersPerSecond else null
                val horizAcc: Float? = if (loc.hasAccuracy()) loc.accuracy else null

                // elapsedRealtimeNanos e monotonico — imune a mudanca de fuso/hora.
                val fixMs = loc.elapsedRealtimeNanos / 1_000_000L
                val nowMs = SystemClock.elapsedRealtime()
                // Ignora fixes antigos guardados em cache pelo sistema.
                if (nowMs - fixMs > MAX_FIX_AGE_MS) return

                onFix(loc.speed.toDouble(), speedAcc, horizAcc, fixMs)
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        return true
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    private companion object {
        const val TAG = "LocationSource"

        /** Intervalo desejado entre fixes (ms). */
        const val INTERVAL_MS = 1_000L

        /** O sistema pode entregar mais rapido que isso, ate este limite. */
        const val MIN_INTERVAL_MS = 500L

        /** Descarta fixes mais velhos que isto. */
        const val MAX_FIX_AGE_MS = 4_000L
    }
}

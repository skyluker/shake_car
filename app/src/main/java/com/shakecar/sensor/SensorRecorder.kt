package com.shakecar.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.shakecar.domain.SensorSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import android.os.Looper

/**
 * Strumieniuje SensorSample. Łączy:
 *   - TYPE_LINEAR_ACCELERATION (przyspieszenie bez grawitacji)
 *   - TYPE_GRAVITY (kierunek grawitacji = pion w układzie telefonu)
 *   - TYPE_GYROSCOPE (prędkość kątowa)
 *   - FusedLocationProvider (prędkość pojazdu)
 *
 * Każda próbka akcelerometru emituje SensorSample z najnowszymi znanymi wartościami
 * pozostałych czujników (sensor fusion w stylu nearest-neighbour).
 */
class SensorRecorder(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun stream(): Flow<SensorSample> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val gyr = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (acc == null) { close(); return@callbackFlow }

        // Domyślnie pion w dół układu telefonu (Z-up).
        // Zwykłe var bez @Volatile - wszystkie callbacki sensorów i lokalizacji
        // chodzą na tym samym wątku (main looper), więc nie ma wyścigu.
        var gx = 0f
        var gy = 0f
        var gz = 9.81f
        var rx = 0f
        var ry = 0f
        var rz = 0f
        var speedKmh = Float.NaN
        var gpsAcc = Float.NaN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GRAVITY -> {
                        gx = event.values[0]; gy = event.values[1]; gz = event.values[2]
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        rx = event.values[0]; ry = event.values[1]; rz = event.values[2]
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION,
                    Sensor.TYPE_ACCELEROMETER -> {
                        val s = SensorSample(
                            timestampNs = event.timestamp,
                            accX = event.values[0],
                            accY = event.values[1],
                            accZ = event.values[2],
                            gravX = gx, gravY = gy, gravZ = gz,
                            gyrX = rx, gyrY = ry, gyrZ = rz,
                            speedKmh = speedKmh,
                            gpsAccuracyM = gpsAcc,
                        )
                        trySend(s)
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }

        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_FASTEST)
        if (grav != null) sm.registerListener(listener, grav, SensorManager.SENSOR_DELAY_GAME)
        if (gyr != null) sm.registerListener(listener, gyr, SensorManager.SENSOR_DELAY_GAME)

        // GPS - jeśli przyznano uprawnienia.
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val fused = LocationServices.getFusedLocationProviderClient(context)
        val locReq = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
            .setMinUpdateIntervalMillis(250L)
            .build()
        val locCb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                val loc = r.lastLocation ?: return
                speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else Float.NaN
                gpsAcc = if (loc.hasAccuracy()) loc.accuracy else Float.NaN
            }
        }
        if (fineGranted) {
            fused.requestLocationUpdates(locReq, locCb, Looper.getMainLooper())
        }

        awaitClose {
            sm.unregisterListener(listener)
            if (fineGranted) fused.removeLocationUpdates(locCb)
        }
    }
}

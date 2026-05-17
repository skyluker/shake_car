package com.shakecar.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.shakecar.domain.SensorSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Strumieniuje SensorSample z czujnik\u00f3w. U\u017cywa LINEAR_ACCELERATION (bez grawitacji)
 * + GYROSCOPE. Pr\u00f3bkowanie SENSOR_DELAY_FASTEST -> typowo 100-500 Hz w zale\u017cno\u015bci od urz\u0105dzenia.
 *
 * Akcelerometr i \u017cyroskop s\u0105 \u0142\u0105czone naiwnie - ka\u017cda pr\u00f3bka akcelerometru emituje
 * SensorSample z ostatni\u0105 znan\u0105 warto\u015bci\u0105 \u017cyroskopu.
 */
class SensorRecorder(private val context: Context) {

    fun stream(): Flow<SensorSample> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyr = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (acc == null) { close(); return@callbackFlow }

        var gx = 0f; var gy = 0f; var gz = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        gx = event.values[0]; gy = event.values[1]; gz = event.values[2]
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION,
                    Sensor.TYPE_ACCELEROMETER -> {
                        val s = SensorSample(
                            timestampNs = event.timestamp,
                            accX = event.values[0],
                            accY = event.values[1],
                            accZ = event.values[2],
                            gyrX = gx, gyrY = gy, gyrZ = gz,
                        )
                        trySend(s)
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }

        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_FASTEST)
        if (gyr != null) sm.registerListener(listener, gyr, SensorManager.SENSOR_DELAY_FASTEST)

        awaitClose { sm.unregisterListener(listener) }
    }
}

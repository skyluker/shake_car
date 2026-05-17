package com.shakecar.domain

/**
 * Pojedyncza próbka z czujników.
 *  - acc*: przyspieszenie liniowe (m/s², bez grawitacji), z TYPE_LINEAR_ACCELERATION
 *  - grav*: wektor grawitacji w układzie telefonu (m/s²), z TYPE_GRAVITY
 *  - gyr*: prędkość kątowa (rad/s)
 *  - speedKmh: prędkość z GPS (jeśli dostępna)
 *  - gpsAccuracyM: dokładność GPS (m)
 *  - timestampNs: czas zdarzenia w ns od boot
 */
data class SensorSample(
    val timestampNs: Long,
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val gravX: Float = 0f,
    val gravY: Float = 0f,
    val gravZ: Float = 9.81f,
    val gyrX: Float = 0f,
    val gyrY: Float = 0f,
    val gyrZ: Float = 0f,
    val speedKmh: Float = Float.NaN,
    val gpsAccuracyM: Float = Float.NaN,
)

package com.shakecar.domain

/**
 * Pojedyncza próbka z czujników. Przyspieszenia w m/s^2 (już bez grawitacji),
 * prędkość kątowa w rad/s. Czas w nanosekundach od boot.
 */
data class SensorSample(
    val timestampNs: Long,
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val gyrX: Float = 0f,
    val gyrY: Float = 0f,
    val gyrZ: Float = 0f,
    val speedKmh: Float = Float.NaN,
)

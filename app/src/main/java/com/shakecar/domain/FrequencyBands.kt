package com.shakecar.domain

/**
 * Pasma diagnostyczne wynikające z literatury.
 *  - MOTION_SICKNESS (0.1-0.5 Hz): mdłości, ISO 2631-1
 *  - BODY_BOUNCE     (0.8-2.5 Hz): rezonans masy resorowanej, kondycja amortyzatorów
 *  - DISCOMFORT      (4-8 Hz):     najgorzej tolerowane przez człowieka, dyskomfort jazdy
 *  - WHEEL_HOP       (10-15 Hz):   rezonans masy nieresorowanej, opony/tuleje
 *  - WHEEL_IMBALANCE (15-25 Hz):   typowe dla niewyważonych kół na autostradzie
 *  - HIGH_FREQ       (25-40 Hz):   stuki, klekoty, ostre nierówności
 */
enum class FrequencyBand(val label: String, val lowHz: Float, val highHz: Float) {
    MOTION_SICKNESS("Motion sickness", 0.1f, 0.5f),
    BODY_BOUNCE("Body bounce (shocks)", 0.8f, 2.5f),
    DISCOMFORT("Human discomfort", 4f, 8f),
    WHEEL_HOP("Wheel hop", 10f, 15f),
    WHEEL_IMBALANCE("Wheel imbalance", 15f, 25f),
    HIGH_FREQ("Knocks / rattles", 25f, 40f),
}

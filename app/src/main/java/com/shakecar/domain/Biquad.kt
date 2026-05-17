package com.shakecar.domain

/**
 * Sekcja biquad direct-form-1 z filtrem IIR drugiego rzędu:
 * y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
 *
 * Współczynniki przyjęte już znormalizowane (a0 = 1).
 */
class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }

    companion object {
        /**
         * Bilinear transform analogowego biquadu
         *   H(s) = (B2*s^2 + B1*s + B0) / (A2*s^2 + A1*s + A0)
         * z częstotliwością próbkowania fs, używając c = 2*fs (bez prewarpingu).
         */
        fun fromAnalog(
            B2: Double, B1: Double, B0: Double,
            A2: Double, A1: Double, A0: Double,
            fs: Double,
        ): Biquad {
            val c = 2.0 * fs
            val c2 = c * c
            val a0 = A2 * c2 + A1 * c + A0
            val a1 = (-2.0 * A2 * c2 + 2.0 * A0) / a0
            val a2 = (A2 * c2 - A1 * c + A0) / a0
            val b0 = (B2 * c2 + B1 * c + B0) / a0
            val b1 = (-2.0 * B2 * c2 + 2.0 * B0) / a0
            val b2 = (B2 * c2 - B1 * c + B0) / a0
            return Biquad(b0, b1, b2, a1, a2)
        }
    }
}

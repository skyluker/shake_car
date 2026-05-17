package com.shakecar.domain

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Filtr ważący Wk wg ISO 2631-1:1997 dla osi pionowej (siedzenie kierowcy, plecy).
 *
 * Kaskada czterech sekcji, parametry analogowe wg normy:
 *   Sekcja 1: HP rzędu 2, f1 = 0.4 Hz,  Q1 = 1/√2  (ograniczenie pasma od dołu)
 *   Sekcja 2: LP rzędu 2, f2 = 100 Hz,  Q2 = 1/√2  (ograniczenie pasma od góry)
 *   Sekcja 3: transition a-v, f3 = 12.5 Hz, Q3 = 0.63 (jeden zero, dwa bieguny)
 *   Sekcja 4: upward step, f5 = 2.37 Hz / Q5 = 0.91, f6 = 3.35 Hz / Q6 = 0.91
 *
 * Każda sekcja jest dyskretyzowana metodą bilinear (s = 2*fs * (1 - z^-1)/(1 + z^-1)).
 * Bez prewarpingu - akceptowalne w paśmie 0.5-30 Hz przy fs >= 100 Hz.
 *
 * Referencje:
 *   - ISO 2631-1:1997, Annex A
 *   - Rimell & Mansfield (2007), "Design of digital filters for frequency
 *     weightings required for risk assessments of workers exposed to vibration"
 */
class IsoWkFilter(fs: Double) {

    private val sections: Array<Biquad>

    init {
        val w1 = 2.0 * PI * 0.4
        val q1 = 1.0 / sqrt(2.0)
        val w2 = 2.0 * PI * 100.0
        val q2 = 1.0 / sqrt(2.0)
        val w3 = 2.0 * PI * 12.5
        val q3 = 0.63
        val w4 = 2.0 * PI * 12.5  // f4 = f3 wg normy
        val w5 = 2.0 * PI * 2.37
        val q5 = 0.91
        val w6 = 2.0 * PI * 3.35
        val q6 = 0.91

        // Sekcja 1: HP, H(s) = s^2 / (s^2 + s*w1/Q1 + w1^2)
        val s1 = Biquad.fromAnalog(
            B2 = 1.0, B1 = 0.0, B0 = 0.0,
            A2 = 1.0, A1 = w1 / q1, A0 = w1 * w1,
            fs = fs,
        )

        // Sekcja 2: LP, H(s) = w2^2 / (s^2 + s*w2/Q2 + w2^2)
        val s2 = Biquad.fromAnalog(
            B2 = 0.0, B1 = 0.0, B0 = w2 * w2,
            A2 = 1.0, A1 = w2 / q2, A0 = w2 * w2,
            fs = fs,
        )

        // Sekcja 3: a-v transition.
        //   H(s) = (1 + s/w4) / (s^2/w3^2 + s/(w3*Q3) + 1)
        // mnożymy num i denom przez w3^2:
        //   num: w3^2 + (w3^2/w4) * s
        //   den: s^2 + (w3/Q3) * s + w3^2
        val s3 = Biquad.fromAnalog(
            B2 = 0.0, B1 = w3 * w3 / w4, B0 = w3 * w3,
            A2 = 1.0, A1 = w3 / q3, A0 = w3 * w3,
            fs = fs,
        )

        // Sekcja 4: upward step.
        //   H(s) = (w6^2 / w5^2) * (s^2 + s*w5/Q5 + w5^2) / (s^2 + s*w6/Q6 + w6^2)
        // Wymnażamy współczynnik w stałą wzmocnienia:
        val gain = (w6 * w6) / (w5 * w5)
        val s4 = Biquad.fromAnalog(
            B2 = gain, B1 = gain * w5 / q5, B0 = gain * w5 * w5,
            A2 = 1.0, A1 = w6 / q6, A0 = w6 * w6,
            fs = fs,
        )

        sections = arrayOf(s1, s2, s3, s4)
    }

    /**
     * Filtruje sygnał wejściowy (m/s²) i zwraca sygnał ważony Wk (m/s²).
     */
    fun apply(input: FloatArray): FloatArray {
        val out = FloatArray(input.size)
        for (i in input.indices) {
            var v = input[i].toDouble()
            for (s in sections) v = s.process(v)
            out[i] = v.toFloat()
        }
        return out
    }

    /** Zwraca RMS sygnału przefiltrowanego (m/s²). */
    fun computeRms(input: FloatArray): Float {
        if (input.isEmpty()) return 0f
        var sum = 0.0
        for (s in sections) s.reset()
        for (x in input) {
            var v = x.toDouble()
            for (s in sections) v = s.process(v)
            sum += v * v
        }
        return sqrt(sum / input.size).toFloat()
    }
}

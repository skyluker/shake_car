package com.shakecar.domain

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Wynik analizy: skalary + spektrum mocy oraz energie w pasmach.
 */
data class AnalysisResult(
    val sampleRateHz: Float,
    val durationSec: Float,
    val acceptedFraction: Float,        // udział próbek po segmentacji prędkości
    val avgSpeedKmh: Float,             // średnia prędkość z zaakceptowanego segmentu
    val rmsVertical: Float,
    val rmsWeightedIso2631: Float,
    val crestFactor: Float,
    val dominantFrequencyHz: Float,
    val bodyBounceFreqHz: Float,
    val dampingRatioBody: Float,
    val bandEnergy: Map<FrequencyBand, Float>,
    val powerSpectrum: FloatArray,
    val frequencies: FloatArray,
    val comfortScore: Float,
    val suspensionScore: Float,
)

/**
 * Konfiguracja segmentacji wg prędkości GPS.
 */
data class SpeedSegmentationConfig(
    val minSpeedKmh: Float = 30f,           // odsiewamy postoje i wolny ruch miejski
    val maxSpeedKmh: Float = 130f,          // bezpiecznik na przyspieszenia ekstremalne
    val maxStdDevKmh: Float = 5f,           // okno musi być stabilne prędkościowo
    val windowSec: Float = 5f,
)

object SignalAnalyzer {

    private const val MIN_SAMPLES = 256

    fun analyze(
        samples: List<SensorSample>,
        segConfig: SpeedSegmentationConfig = SpeedSegmentationConfig(),
    ): AnalysisResult? {
        if (samples.size < MIN_SAMPLES) return null

        val n0 = samples.size
        val firstNs = samples.first().timestampNs
        val lastNs = samples.last().timestampNs
        val durationSec = ((lastNs - firstNs).coerceAtLeast(1L)) / 1e9f
        val fs = (n0 - 1) / durationSec
        if (fs < 20f) return null

        // 1. Korekta orientacji - pionowa składowa = projekcja na wektor grawitacji.
        val vertical = projectOnGravity(samples)

        // 2. Segmentacja prędkości - jeśli mamy GPS, zostaw tylko stabilne okna.
        val mask = buildSpeedMask(samples, fs, segConfig)
        val acceptedFraction = if (mask == null) 1f else mask.count { it } / n0.toFloat()
        val accepted = if (mask == null) vertical else applyMask(vertical, mask)
        if (accepted.size < MIN_SAMPLES) {
            // Za mało stabilnego materiału - zwróć analizę całości z ostrzeżeniem
            return analyzeArray(vertical, fs, durationSec, 1f, avgSpeed(samples, null))
        }
        val avgSp = avgSpeed(samples, mask)

        return analyzeArray(accepted, fs, durationSec, acceptedFraction, avgSp)
    }

    private fun analyzeArray(
        vertical: FloatArray,
        fs: Float,
        durationSec: Float,
        acceptedFraction: Float,
        avgSpeed: Float,
    ): AnalysisResult {
        val n0 = vertical.size

        // Usuwamy DC
        var meanD = 0.0
        for (v in vertical) meanD += v
        val mean = (meanD / n0).toFloat()
        val centered = FloatArray(n0) { vertical[it] - mean }

        // RMS w domenie czasu (przed oknem, m/s²)
        var sumSq = 0.0
        var peak = 0f
        for (v in centered) {
            sumSq += v * v
            if (abs(v) > peak) peak = abs(v)
        }
        val rms = sqrt(sumSq / n0).toFloat()
        val crest = if (rms > 1e-6f) peak / rms else 0f

        // RMS ważone Wk - prawdziwy IIR ISO 2631-1
        val rmsWk = IsoWkFilter(fs.toDouble()).computeRms(centered)

        // Padding do potęgi 2 i okno Hann do FFT
        val n = nextPow2(n0)
        val data = FloatArray(n)
        for (i in 0 until n0) {
            val w = 0.5f * (1f - cos(2.0 * Math.PI * i / (n0 - 1)).toFloat())
            data[i] = centered[i] * w
        }

        val fft = FloatFFT_1D(n.toLong())
        fft.realForward(data)

        // PSD jednostronna
        val half = n / 2
        val psd = FloatArray(half + 1)
        psd[0] = data[0] * data[0]
        psd[half] = data[1] * data[1]
        for (k in 1 until half) {
            val re = data[2 * k]
            val im = data[2 * k + 1]
            psd[k] = re * re + im * im
        }
        // Skala mocy okna Hann
        val norm = 1f / (n0 * 0.375f)
        for (i in psd.indices) psd[i] *= norm

        val freqs = FloatArray(half + 1) { it * fs / n }
        val df = fs / n

        val bandEnergy = HashMap<FrequencyBand, Float>()
        for (band in FrequencyBand.values()) {
            var e = 0f
            for (i in psd.indices) {
                val f = freqs[i]
                if (f in band.lowHz..band.highHz) e += psd[i] * df
            }
            bandEnergy[band] = e
        }

        // Częstotliwość dominująca (poza DC i powyżej 25 Hz)
        var maxIdx = 1
        for (i in psd.indices) {
            if (freqs[i] < 0.3f) continue
            if (freqs[i] > 25f) break
            if (psd[i] > psd[maxIdx]) maxIdx = i
        }
        val dominant = freqs[maxIdx]

        val (bodyFreq, dampingRatio) = halfPowerInBand(psd, freqs, 0.6f, 3.5f)

        val comfort = mapComfort(rmsWk)
        val suspension = mapSuspension(dampingRatio, bandEnergy)

        return AnalysisResult(
            sampleRateHz = fs,
            durationSec = durationSec,
            acceptedFraction = acceptedFraction,
            avgSpeedKmh = avgSpeed,
            rmsVertical = rms,
            rmsWeightedIso2631 = rmsWk,
            crestFactor = crest,
            dominantFrequencyHz = dominant,
            bodyBounceFreqHz = bodyFreq,
            dampingRatioBody = dampingRatio,
            bandEnergy = bandEnergy,
            powerSpectrum = psd,
            frequencies = freqs,
            comfortScore = comfort,
            suspensionScore = suspension,
        )
    }

    /**
     * Projektuje wektor przyspieszenia liniowego na kierunek grawitacji
     * (czyli pion w układzie świata, niezależny od orientacji telefonu).
     *
     *   a_pion = (a · g_hat),  gdzie g_hat = g / |g|
     */
    private fun projectOnGravity(samples: List<SensorSample>): FloatArray {
        val out = FloatArray(samples.size)
        for (i in samples.indices) {
            val s = samples[i]
            val mag = sqrt(s.gravX * s.gravX + s.gravY * s.gravY + s.gravZ * s.gravZ)
            if (mag < 1e-3f) {
                out[i] = s.accZ
            } else {
                out[i] = (s.accX * s.gravX + s.accY * s.gravY + s.accZ * s.gravZ) / mag
            }
        }
        return out
    }

    /**
     * Buduje maskę boolean[] o długości próbek - true jeśli okno o szerokości windowSec
     * wokół tej próbki ma stabilną prędkość. Zwraca null jeśli brak GPS.
     */
    private fun buildSpeedMask(
        samples: List<SensorSample>,
        fs: Float,
        cfg: SpeedSegmentationConfig,
    ): BooleanArray? {
        val hasGps = samples.any { !it.speedKmh.isNaN() }
        if (!hasGps) return null

        val n = samples.size
        val mask = BooleanArray(n)
        val w = (cfg.windowSec * fs).toInt().coerceAtLeast(64)
        val half = w / 2

        // Wypełniamy puste GPS-y ostatnią znaną próbką (forward fill).
        val sp = FloatArray(n)
        var last = Float.NaN
        for (i in 0 until n) {
            val v = samples[i].speedKmh
            if (!v.isNaN()) last = v
            sp[i] = last
        }

        for (i in 0 until n) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(n - 1)
            var sum = 0.0; var sumSq = 0.0; var cnt = 0
            for (j in lo..hi) {
                val v = sp[j]
                if (v.isNaN()) continue
                sum += v; sumSq += v * v; cnt++
            }
            if (cnt < (hi - lo + 1) * 0.6) { mask[i] = false; continue }
            val mean = sum / cnt
            val variance = max(0.0, sumSq / cnt - mean * mean)
            val std = sqrt(variance).toFloat()
            mask[i] = mean >= cfg.minSpeedKmh &&
                mean <= cfg.maxSpeedKmh &&
                std <= cfg.maxStdDevKmh
        }
        return mask
    }

    private fun applyMask(values: FloatArray, mask: BooleanArray): FloatArray {
        var cnt = 0
        for (b in mask) if (b) cnt++
        val out = FloatArray(cnt)
        var k = 0
        for (i in values.indices) if (mask[i]) { out[k++] = values[i] }
        return out
    }

    private fun avgSpeed(samples: List<SensorSample>, mask: BooleanArray?): Float {
        var sum = 0.0; var cnt = 0
        for (i in samples.indices) {
            val v = samples[i].speedKmh
            if (v.isNaN()) continue
            if (mask != null && !mask[i]) continue
            sum += v; cnt++
        }
        return if (cnt == 0) Float.NaN else (sum / cnt).toFloat()
    }

    private fun halfPowerInBand(
        psd: FloatArray, freqs: FloatArray, lo: Float, hi: Float,
    ): Pair<Float, Float> {
        var iPeak = -1
        var pPeak = 0f
        for (i in psd.indices) {
            val f = freqs[i]
            if (f < lo) continue
            if (f > hi) break
            if (psd[i] > pPeak) { pPeak = psd[i]; iPeak = i }
        }
        if (iPeak < 0 || pPeak <= 0f) return 0f to 0f
        val halfPower = pPeak / 2f
        var i1 = iPeak
        while (i1 > 0 && psd[i1] > halfPower) i1--
        var i2 = iPeak
        while (i2 < psd.size - 1 && psd[i2] > halfPower) i2++
        val fPeak = freqs[iPeak]
        val f1 = freqs[i1]
        val f2 = freqs[i2]
        val zeta = if (fPeak > 0f) (f2 - f1) / (2f * fPeak) else 0f
        return fPeak to zeta
    }

    private fun mapComfort(rmsWk: Float): Float {
        // ISO 2631-1 awc skala: <0.315 nieuciążliwe, >2.0 ekstremalnie nieprzyjemne.
        return 100f * (1f - (rmsWk / 2f)).coerceIn(0f, 1f)
    }

    private fun mapSuspension(dampingRatio: Float, bands: Map<FrequencyBand, Float>): Float {
        val dampingScore = ((dampingRatio - 0.05f) / 0.30f).coerceIn(0f, 1f) * 70f
        val body = bands[FrequencyBand.BODY_BOUNCE] ?: 0f
        val hop = bands[FrequencyBand.WHEEL_HOP] ?: 0f
        val ratio = if (body + hop > 1e-6f) body / (body + hop) else 0.5f
        val balanceScore = ratio.coerceIn(0f, 1f) * 30f
        return dampingScore + balanceScore
    }

    private fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }
}

package com.shakecar.domain

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Wynik analizy: skalary + spektrum mocy oraz energie w pasmach.
 */
data class AnalysisResult(
    val sampleRateHz: Float,
    val durationSec: Float,
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

object SignalAnalyzer {

    private const val MIN_SAMPLES = 256

    fun analyze(samples: List<SensorSample>): AnalysisResult? {
        if (samples.size < MIN_SAMPLES) return null

        val n0 = samples.size
        val firstNs = samples.first().timestampNs
        val lastNs = samples.last().timestampNs
        val durationSec = ((lastNs - firstNs).coerceAtLeast(1L)) / 1e9f
        val fs = (n0 - 1) / durationSec
        if (fs < 20f) return null // za mała częstotliwość próbkowania

        // Pionowa składowa: Z to oś prostopadła do ekranu telefonu.
        // W idealnym przypadku telefon przykręcony Z = pionowa oś auta.
        // Aby być odpornym na orientację, używamy wartości skalarnej (magnitude - g_const).
        // Jednak dla MVP: bierzemy oś o największej wariancji.
        val verticalAxis = pickVerticalAxis(samples)

        // Padding do potęgi 2 dla FFT
        val n = nextPow2(n0)
        val data = FloatArray(n)
        for (i in 0 until n0) {
            data[i] = when (verticalAxis) {
                Axis.X -> samples[i].accX
                Axis.Y -> samples[i].accY
                Axis.Z -> samples[i].accZ
            }
        }
        // Usuwamy DC
        val mean = data.sum() / n0
        for (i in 0 until n0) data[i] -= mean

        // RMS (przed oknem, w domenie czasu)
        var sumSq = 0.0
        var peak = 0f
        for (i in 0 until n0) {
            val v = data[i]
            sumSq += v * v
            if (abs(v) > peak) peak = abs(v)
        }
        val rms = sqrt(sumSq / n0).toFloat()
        val crest = if (rms > 1e-6f) peak / rms else 0f

        // Hann window
        for (i in 0 until n0) {
            val w = 0.5f * (1f - cos(2.0 * Math.PI * i / (n0 - 1))).toFloat()
            data[i] = data[i] * w
        }

        // FFT real
        val fft = FloatFFT_1D(n.toLong())
        fft.realForward(data)

        // PSD jednostronna; output JTransforms: re[0], re[n/2], re[1], im[1], re[2], im[2], ...
        val half = n / 2
        val psd = FloatArray(half + 1)
        psd[0] = data[0] * data[0]
        psd[half] = data[1] * data[1]
        for (k in 1 until half) {
            val re = data[2 * k]
            val im = data[2 * k + 1]
            psd[k] = re * re + im * im
        }
        // Normalizacja (skala mocy okna Hann ~ 0.375)
        val norm = 1f / (n0 * 0.375f)
        for (i in psd.indices) psd[i] *= norm

        val freqs = FloatArray(half + 1) { it * fs / n }

        // Energia w pasmach (suma PSD * df)
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

        // Częstotliwość dominująca (pomijając pasmo DC < 0.3 Hz)
        var maxIdx = 1
        for (i in psd.indices) {
            if (freqs[i] < 0.3f) continue
            if (freqs[i] > 25f) break
            if (psd[i] > psd[maxIdx]) maxIdx = i
        }
        val dominant = freqs[maxIdx]

        // Pik w paśmie body bounce + half-power bandwidth -> damping ratio
        val (bodyFreq, dampingRatio) = halfPowerInBand(psd, freqs, 0.6f, 3.5f)

        // Filtr Wk (uproszczone przybliżenie: szczyt przy 4-8 Hz)
        val rmsWk = computeIsoWeightedRms(psd, freqs, df)

        // Score komfortu (im niższe RMS Wk, tym wyżej, ISO 2631 awc skala)
        val comfortScore = mapComfort(rmsWk)
        // Score zawieszenia: niski damping = niski wynik
        val suspensionScore = mapSuspension(dampingRatio, bandEnergy)

        return AnalysisResult(
            sampleRateHz = fs,
            durationSec = durationSec,
            rmsVertical = rms,
            rmsWeightedIso2631 = rmsWk,
            crestFactor = crest,
            dominantFrequencyHz = dominant,
            bodyBounceFreqHz = bodyFreq,
            dampingRatioBody = dampingRatio,
            bandEnergy = bandEnergy,
            powerSpectrum = psd,
            frequencies = freqs,
            comfortScore = comfortScore,
            suspensionScore = suspensionScore,
        )
    }

    private enum class Axis { X, Y, Z }

    private fun pickVerticalAxis(samples: List<SensorSample>): Axis {
        var sx = 0.0; var sy = 0.0; var sz = 0.0
        var mx = 0.0; var my = 0.0; var mz = 0.0
        for (s in samples) { mx += s.accX; my += s.accY; mz += s.accZ }
        mx /= samples.size; my /= samples.size; mz /= samples.size
        for (s in samples) {
            sx += (s.accX - mx) * (s.accX - mx)
            sy += (s.accY - my) * (s.accY - my)
            sz += (s.accZ - mz) * (s.accZ - mz)
        }
        return when {
            sz >= sx && sz >= sy -> Axis.Z
            sy >= sx -> Axis.Y
            else -> Axis.X
        }
    }

    /**
     * Half-power bandwidth method: znajduje pik PSD w danym paśmie i wyznacza
     * ζ = (f2 - f1) / (2 * fpeak), gdzie f1, f2 to częstotliwości przy mocy = pik/2.
     */
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

    /**
     * Uproszczone Wk frequency weighting (ISO 2631-1) dla osi pionowej.
     * Krzywa z najwyższym czułością w pasmie 4-8 Hz.
     * Dla kompletnej zgodności wymagałoby IIR filtra Wk; tu mnożymy PSD przez |H(f)|^2
     * w postaci aproksymacji band-pass.
     */
    private fun computeIsoWeightedRms(psd: FloatArray, freqs: FloatArray, df: Float): Float {
        var s = 0f
        for (i in psd.indices) {
            val f = freqs[i]
            if (f < 0.5f || f > 80f) continue
            val w = wkWeight(f)
            s += psd[i] * w * w * df
        }
        return sqrt(s.toDouble()).toFloat()
    }

    private fun wkWeight(f: Float): Float {
        // Aproksymacja Wk: pasmo 1Hz -> ~0.5, szczyt 4-8 Hz ~ 1.0, opadanie 1/f powyżej.
        val low = 1f / sqrt(1f + (1f / f) * (1f / f))
        val high = 1f / sqrt(1f + (f / 12.5f) * (f / 12.5f))
        return low * high
    }

    private fun mapComfort(rmsWk: Float): Float {
        // ISO 2631: <0.315 m/s^2 not uncomfortable; >2 m/s^2 extremely uncomfortable.
        // Mapujemy do 0-100, gdzie 100 = bardzo komfortowo.
        val score = 100f * (1f - (rmsWk / 2f)).coerceIn(0f, 1f)
        return score
    }

    private fun mapSuspension(dampingRatio: Float, bands: Map<FrequencyBand, Float>): Float {
        // Nowoczesny amortyzator: ζ ~ 0.25-0.35; zużyty: ζ < 0.15.
        val dampingScore = ((dampingRatio - 0.05f) / 0.30f).coerceIn(0f, 1f) * 70f
        // Wkład wheel hop: jeśli mocno dominuje, źle (luzy/opony)
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

package com.shakecar.domain

import kotlin.math.ln
import kotlin.math.max

/**
 * Klasyfikator typu nawierzchni na podstawie sygnatury widmowej i wskaźników skalarnych.
 *
 * Reguły heurystyczne (do kalibracji na realnych danych):
 *
 *   GŁADKI ASFALT  - niski RMS (<0.6 m/s²), mała energia >10 Hz, niski crest
 *   SZORSTKI ASFALT - umiarkowany RMS, podwyższona energia 10-25 Hz, crest ~3-5
 *   KOSTKA BRUKOWA  - wysokie RMS, dominacja 5-15 Hz, regularne piki
 *   BETON Z DYLATACJAMI - regularne impulsy (wysoki crest), pik dominujący < 4 Hz
 *   SZUTER / GRAVEL  - szerokie pasmo szumu, wysokie RMS, niski stosunek tonality
 *   DZIURY / WYBOJE  - bardzo wysoki crest factor (>8), nieregularne impulsy
 *
 * Każda reguła zwraca confidence 0-1 i wybierany jest typ z najwyższą wartością.
 */
enum class SurfaceType(val label: String) {
    SMOOTH_ASPHALT("Gładki asfalt"),
    ROUGH_ASPHALT("Szorstki asfalt"),
    COBBLESTONE("Kostka brukowa"),
    CONCRETE_JOINTS("Beton z dylatacjami"),
    GRAVEL("Szuter / makadam"),
    POTHOLES("Wyboje / dziury"),
    UNKNOWN("Nieokreślona"),
}

data class SurfaceDetection(
    val type: SurfaceType,
    val confidence: Float,
    val features: SurfaceFeatures,
)

/**
 * Cechy ekstrahowane z PSD - przydatne też do treningu klasyfikatora ML w przyszłości.
 */
data class SurfaceFeatures(
    val totalEnergy: Float,
    val lowBandFraction: Float,    // 0-4 Hz
    val midBandFraction: Float,    // 4-15 Hz
    val highBandFraction: Float,   // 15-40 Hz
    val spectralFlatness: Float,   // 0=tonal, 1=biały szum
    val spectralCentroid: Float,   // środek ciężkości widma w Hz
    val crestFactor: Float,
    val rms: Float,
)

object SurfaceClassifier {

    fun classify(result: AnalysisResult): SurfaceDetection {
        val feats = extractFeatures(result)
        val scores = scoreAll(feats)
        val (type, conf) = scores.maxByOrNull { it.value } ?.toPair() ?: (SurfaceType.UNKNOWN to 0f)
        return SurfaceDetection(type, conf, feats)
    }

    /**
     * Ekstrakcja cech sygnałowych - lekkie do policzenia, dobre dla klasyfikatora.
     */
    private fun extractFeatures(r: AnalysisResult): SurfaceFeatures {
        val psd = r.powerSpectrum
        val freqs = r.frequencies
        if (psd.isEmpty()) {
            return SurfaceFeatures(0f, 0f, 0f, 0f, 0f, 0f, r.crestFactor, r.rmsVertical)
        }

        var total = 0.0
        var low = 0.0; var mid = 0.0; var high = 0.0
        var fxp = 0.0     // suma f * P(f)
        var logSum = 0.0  // do flatness
        var arithSum = 0.0
        var bins = 0
        for (i in psd.indices) {
            val f = freqs[i]
            if (f > 40f) break
            val p = psd[i].toDouble().coerceAtLeast(1e-12)
            total += p
            fxp += f * p
            arithSum += p
            logSum += ln(p)
            bins++
            when {
                f < 4f -> low += p
                f < 15f -> mid += p
                else -> high += p
            }
        }

        val totalEnergy = total.toFloat()
        val centroid = if (total > 0) (fxp / total).toFloat() else 0f
        val flatness = if (bins > 0 && arithSum > 0)
            (Math.exp(logSum / bins) / (arithSum / bins)).toFloat()
        else 0f
        val safeTotal = max(total, 1e-12).toFloat()

        return SurfaceFeatures(
            totalEnergy = totalEnergy,
            lowBandFraction = (low / safeTotal).toFloat(),
            midBandFraction = (mid / safeTotal).toFloat(),
            highBandFraction = (high / safeTotal).toFloat(),
            spectralFlatness = flatness,
            spectralCentroid = centroid,
            crestFactor = r.crestFactor,
            rms = r.rmsVertical,
        )
    }

    private fun scoreAll(f: SurfaceFeatures): Map<SurfaceType, Float> {
        val s = mutableMapOf<SurfaceType, Float>()

        // 1. Wyboje / dziury - dominuje crest, energia szerokopasmowa
        s[SurfaceType.POTHOLES] = membership(f.crestFactor, lo = 6f, peak = 10f, hi = 20f) *
            membership(f.rms, lo = 0.6f, peak = 2.5f, hi = 6f)

        // 2. Beton z dylatacjami - regularne impulsy, niski centroid (1-4 Hz peak),
        //    średni crest (4-7), niska flatness (tonalne)
        s[SurfaceType.CONCRETE_JOINTS] = membership(f.crestFactor, 3f, 5f, 8f) *
            membership(f.spectralCentroid, 1f, 3f, 6f) *
            membership(1f - f.spectralFlatness, 0.5f, 0.85f, 1f)

        // 3. Kostka brukowa - silny pik 5-15 Hz, wysoki RMS, umiarkowany crest, tonal
        s[SurfaceType.COBBLESTONE] = membership(f.midBandFraction, 0.35f, 0.6f, 0.9f) *
            membership(f.spectralCentroid, 5f, 9f, 14f) *
            membership(f.rms, 0.8f, 2f, 5f) *
            membership(1f - f.spectralFlatness, 0.4f, 0.7f, 1f)

        // 4. Szuter - szeroki szum, wysoka flatness, wysoki RMS
        s[SurfaceType.GRAVEL] = membership(f.spectralFlatness, 0.3f, 0.6f, 1f) *
            membership(f.rms, 0.7f, 1.8f, 5f) *
            membership(f.highBandFraction, 0.2f, 0.45f, 0.8f)

        // 5. Szorstki asfalt - umiarkowane RMS, energia w wyższym paśmie, średnia flatness
        s[SurfaceType.ROUGH_ASPHALT] = membership(f.rms, 0.3f, 0.7f, 1.5f) *
            membership(f.highBandFraction, 0.15f, 0.3f, 0.55f) *
            membership(f.crestFactor, 2.5f, 4f, 7f)

        // 6. Gładki asfalt - niski RMS, niski crest, dominuje pasmo niskie
        s[SurfaceType.SMOOTH_ASPHALT] = membership(f.rms, 0f, 0.25f, 0.7f) *
            membership(f.crestFactor, 0f, 3f, 5f) *
            membership(f.lowBandFraction, 0.4f, 0.7f, 1f)

        // Normalizacja confidence do [0,1]
        val maxScore = s.values.maxOrNull() ?: 0f
        if (maxScore < 0.15f) {
            // Zbyt słabe dopasowanie - oznacz UNKNOWN
            return mapOf(SurfaceType.UNKNOWN to 0.5f)
        }
        return s
    }

    /**
     * Trapezoidalna funkcja przynależności rozmytej:
     *   <lo lub >hi -> 0
     *   peak        -> 1
     *   liniowo pomiędzy
     */
    private fun membership(x: Float, lo: Float, peak: Float, hi: Float): Float {
        if (x <= lo || x >= hi) return 0f
        return if (x < peak) (x - lo) / (peak - lo) else (hi - x) / (hi - peak)
    }
}

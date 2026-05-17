package com.shakecar.data

import android.content.ContentResolver
import android.net.Uri
import com.shakecar.domain.AnalysisResult
import com.shakecar.domain.FrequencyBand
import com.shakecar.domain.SensorSample
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

/**
 * Eksport sesji do CSV (surowe próbki) i JSON (metadane + wynik analizy).
 *
 * Format CSV:
 *   timestampNs,accX,accY,accZ,gravX,gravY,gravZ,gyrX,gyrY,gyrZ,speedKmh,gpsAccM
 *
 * JSON: pełna sesja z polami z SessionEntity, VehicleEntity, AnalysisResult
 *   (bez pełnego PSD - tylko wskaźniki skalarne i energie pasm).
 */
object SessionExporter {

    fun writeCsv(
        cr: ContentResolver,
        uri: Uri,
        samples: List<SensorSample>,
    ) {
        cr.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                w.appendLine("timestampNs,accX,accY,accZ,gravX,gravY,gravZ,gyrX,gyrY,gyrZ,speedKmh,gpsAccM")
                for (s in samples) {
                    w.append(s.timestampNs.toString()).append(',')
                    w.append(fmt(s.accX)).append(',')
                    w.append(fmt(s.accY)).append(',')
                    w.append(fmt(s.accZ)).append(',')
                    w.append(fmt(s.gravX)).append(',')
                    w.append(fmt(s.gravY)).append(',')
                    w.append(fmt(s.gravZ)).append(',')
                    w.append(fmt(s.gyrX)).append(',')
                    w.append(fmt(s.gyrY)).append(',')
                    w.append(fmt(s.gyrZ)).append(',')
                    w.append(fmt(s.speedKmh)).append(',')
                    w.append(fmt(s.gpsAccuracyM)).append('\n')
                }
                w.flush()
            }
        }
    }

    fun writeJson(
        cr: ContentResolver,
        uri: Uri,
        vehicle: VehicleEntity?,
        session: SessionEntity,
        result: AnalysisResult?,
    ) {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("session", JSONObject().apply {
            put("id", session.id)
            put("startedAt", session.startedAt)
            put("endedAt", session.endedAt ?: JSONObject.NULL)
            put("mileageKm", session.mileageKm ?: JSONObject.NULL)
            put("roadType", session.roadType ?: JSONObject.NULL)
            put("notes", session.notes ?: JSONObject.NULL)
        })
        if (vehicle != null) {
            root.put("vehicle", JSONObject().apply {
                put("make", vehicle.make)
                put("model", vehicle.model)
                put("year", vehicle.year)
                put("tireSpec", vehicle.tireSpec ?: JSONObject.NULL)
            })
        }
        if (result != null) {
            root.put("analysis", JSONObject().apply {
                put("sampleRateHz", result.sampleRateHz.toDouble())
                put("durationSec", result.durationSec.toDouble())
                put("acceptedFraction", result.acceptedFraction.toDouble())
                put("avgSpeedKmh", if (result.avgSpeedKmh.isNaN()) JSONObject.NULL else result.avgSpeedKmh.toDouble())
                put("rmsVertical", result.rmsVertical.toDouble())
                put("rmsWeightedIso2631", result.rmsWeightedIso2631.toDouble())
                put("crestFactor", result.crestFactor.toDouble())
                put("dominantFrequencyHz", result.dominantFrequencyHz.toDouble())
                put("bodyBounceFreqHz", result.bodyBounceFreqHz.toDouble())
                put("dampingRatio", result.dampingRatioBody.toDouble())
                put("comfortScore", result.comfortScore.toDouble())
                put("suspensionScore", result.suspensionScore.toDouble())
                val bands = JSONArray()
                for (b in FrequencyBand.values()) {
                    bands.put(JSONObject().apply {
                        put("name", b.name)
                        put("lowHz", b.lowHz.toDouble())
                        put("highHz", b.highHz.toDouble())
                        put("energy", (result.bandEnergy[b] ?: 0f).toDouble())
                    })
                }
                put("bands", bands)
            })
        }
        cr.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                w.write(root.toString(2))
                w.flush()
            }
        }
    }

    private fun fmt(v: Float): String =
        if (v.isNaN()) "" else "%.6f".format(v)
}

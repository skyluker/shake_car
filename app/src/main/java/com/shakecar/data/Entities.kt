package com.shakecar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val make: String,
    val model: String,
    val year: Int,
    val tireSpec: String? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("vehicleId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val mileageKm: Int? = null,
    val roadType: String? = null,
    val avgSpeedKmh: Float? = null,
    val durationSec: Float? = null,
    val sampleRateHz: Float? = null,
    // Skalary z analizy
    val rmsVertical: Float? = null,
    val rmsWeighted: Float? = null,
    val crestFactor: Float? = null,
    val dominantFreqHz: Float? = null,
    val bodyBounceFreqHz: Float? = null,
    val dampingRatio: Float? = null,
    val comfortScore: Float? = null,
    val suspensionScore: Float? = null,
    // Sp\u0142aszczone PSD - lekka serializacja CSV pasm
    val bandEnergiesCsv: String? = null,
    val notes: String? = null,
)

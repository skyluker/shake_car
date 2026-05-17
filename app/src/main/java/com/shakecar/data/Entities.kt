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
    val acceptedFraction: Float? = null,
    val durationSec: Float? = null,
    val sampleRateHz: Float? = null,
    val rmsVertical: Float? = null,
    val rmsWeighted: Float? = null,
    val crestFactor: Float? = null,
    val dominantFreqHz: Float? = null,
    val bodyBounceFreqHz: Float? = null,
    val dampingRatio: Float? = null,
    val comfortScore: Float? = null,
    val suspensionScore: Float? = null,
    val bandEnergiesCsv: String? = null,
    val rawFilePath: String? = null,
    val notes: String? = null,
)

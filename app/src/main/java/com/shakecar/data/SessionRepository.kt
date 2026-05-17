package com.shakecar.data

import com.shakecar.domain.AnalysisResult
import com.shakecar.domain.FrequencyBand
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val vehicleDao: VehicleDao,
    private val sessionDao: SessionDao,
) {
    fun observeVehicles(): Flow<List<VehicleEntity>> = vehicleDao.observeAll()
    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()
    fun observeSessionsForVehicle(vehicleId: Long): Flow<List<SessionEntity>> =
        sessionDao.observeByVehicle(vehicleId)

    suspend fun upsertVehicle(vehicle: VehicleEntity): Long = vehicleDao.upsert(vehicle)
    suspend fun getVehicle(id: Long): VehicleEntity? = vehicleDao.getById(id)
    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getById(id)

    suspend fun startSession(vehicleId: Long, mileageKm: Int?, roadType: String?): Long {
        val s = SessionEntity(
            vehicleId = vehicleId,
            startedAt = System.currentTimeMillis(),
            mileageKm = mileageKm,
            roadType = roadType,
        )
        return sessionDao.insert(s)
    }

    suspend fun finishSession(
        sessionId: Long,
        result: AnalysisResult?,
        rawFilePath: String?,
        notes: String? = null,
    ) {
        val existing = sessionDao.getById(sessionId) ?: return
        val updated = existing.copy(
            endedAt = System.currentTimeMillis(),
            avgSpeedKmh = result?.avgSpeedKmh?.takeIf { !it.isNaN() },
            acceptedFraction = result?.acceptedFraction,
            durationSec = result?.durationSec,
            sampleRateHz = result?.sampleRateHz,
            rmsVertical = result?.rmsVertical,
            rmsWeighted = result?.rmsWeightedIso2631,
            crestFactor = result?.crestFactor,
            dominantFreqHz = result?.dominantFrequencyHz,
            bodyBounceFreqHz = result?.bodyBounceFreqHz,
            dampingRatio = result?.dampingRatioBody,
            comfortScore = result?.comfortScore,
            suspensionScore = result?.suspensionScore,
            bandEnergiesCsv = result?.bandEnergy?.let { be ->
                FrequencyBand.values().joinToString(",") { b -> "%.6f".format(be[b] ?: 0f) }
            },
            rawFilePath = rawFilePath,
            notes = notes,
        )
        sessionDao.update(updated)
    }
}

package com.shakecar.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shakecar.ShakeCarApp
import com.shakecar.data.SessionExporter
import com.shakecar.data.SessionRepository
import com.shakecar.data.VehicleEntity
import com.shakecar.domain.AnalysisResult
import com.shakecar.domain.Diagnosis
import com.shakecar.domain.Finding
import com.shakecar.sensor.RecordingController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun CreationExtras.app(): ShakeCarApp =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ShakeCarApp)

private fun CreationExtras.repo(): SessionRepository {
    val app = app()
    return SessionRepository(app.database.vehicleDao(), app.database.sessionDao())
}

object AppViewModels {
    val Factory = viewModelFactory {
        initializer { VehicleListViewModel(repo()) }
        initializer { RecordViewModel(repo()) }
        initializer { SessionsViewModel(repo()) }
        initializer { AnalysisViewModel(repo()) }
        initializer { TrendViewModel(repo()) }
    }
}

class VehicleListViewModel(private val repo: SessionRepository) : ViewModel() {
    val vehicles = repo.observeVehicles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun add(make: String, model: String, year: Int, tires: String?) = viewModelScope.launch {
        repo.upsertVehicle(VehicleEntity(make = make, model = model, year = year, tireSpec = tires))
    }
}

class RecordViewModel(private val repo: SessionRepository) : ViewModel() {
    private val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    fun startSession(vehicleId: Long, mileage: Int?, road: String?) = viewModelScope.launch {
        _sessionId.value = repo.startSession(vehicleId, mileage, road)
    }

    fun finishSession(result: AnalysisResult?, rawFilePath: String?, notes: String?) = viewModelScope.launch {
        val id = _sessionId.value ?: return@launch
        repo.finishSession(id, result = result, rawFilePath = rawFilePath, notes = notes)
    }
}

class SessionsViewModel(repo: SessionRepository) : ViewModel() {
    val sessions = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class AnalysisViewModel(private val repo: SessionRepository) : ViewModel() {
    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun computeFindings(result: AnalysisResult) {
        _findings.value = Diagnosis.evaluate(result)
    }

    fun exportCsv(sessionId: Long, cr: ContentResolver, uri: Uri) = viewModelScope.launch {
        try {
            val samples = RecordingController.lastSamples()
            withContext(Dispatchers.IO) { SessionExporter.writeCsv(cr, uri, samples) }
            _exportStatus.value = "CSV zapisany (${samples.size} próbek)"
        } catch (e: Exception) {
            _exportStatus.value = "Błąd CSV: ${e.message}"
        }
    }

    fun exportJson(sessionId: Long, cr: ContentResolver, uri: Uri) = viewModelScope.launch {
        try {
            val session = repo.getSession(sessionId) ?: return@launch
            val vehicle = repo.getVehicle(session.vehicleId)
            val result = RecordingController.lastResult()
            withContext(Dispatchers.IO) { SessionExporter.writeJson(cr, uri, vehicle, session, result) }
            _exportStatus.value = "JSON zapisany"
        } catch (e: Exception) {
            _exportStatus.value = "Błąd JSON: ${e.message}"
        }
    }
}



@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrendViewModel(private val repo: SessionRepository) : ViewModel() {
    private val _vehicleId = MutableStateFlow<Long>(-1)

    fun setVehicle(id: Long) { _vehicleId.value = id }

    val sessions: StateFlow<List<com.shakecar.data.SessionEntity>> = _vehicleId
        .flatMapLatest { vid ->
            if (vid < 0) kotlinx.coroutines.flow.flowOf(emptyList())
            else repo.observeSessionsForVehicle(vid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

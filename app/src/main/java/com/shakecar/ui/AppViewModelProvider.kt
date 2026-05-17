package com.shakecar.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakecar.ShakeCarApp
import com.shakecar.data.SessionRepository
import com.shakecar.data.VehicleEntity
import com.shakecar.domain.AnalysisResult
import com.shakecar.domain.Diagnosis
import com.shakecar.domain.Finding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    }
}

class VehicleListViewModel(private val repo: SessionRepository) : ViewModel() {
    val vehicles = repo.observeVehicles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
    fun finishSession(result: AnalysisResult?, notes: String?) = viewModelScope.launch {
        val id = _sessionId.value ?: return@launch
        repo.finishSession(id, avgSpeedKmh = null, result = result, notes = notes)
    }
}

class SessionsViewModel(repo: SessionRepository) : ViewModel() {
    val sessions = repo.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

class AnalysisViewModel(private val repo: SessionRepository) : ViewModel() {
    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings.asStateFlow()
    fun computeFindings(result: AnalysisResult) {
        _findings.value = Diagnosis.evaluate(result)
    }
    suspend fun loadSession(id: Long) = repo.getSession(id)
}

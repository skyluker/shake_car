package com.shakecar.sensor

import android.content.Context
import com.shakecar.domain.AnalysisResult
import com.shakecar.domain.SensorSample
import com.shakecar.domain.SignalAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton kontroluj\u0105cy aktywne nagrywanie. Trzyma bufor pr\u00f3bek w pami\u0119ci.
 * Po zatrzymaniu - liczy AnalysisResult.
 */
object RecordingController {

    data class State(
        val recording: Boolean = false,
        val sampleCount: Int = 0,
        val elapsedSec: Float = 0f,
        val lastSampleRateHz: Float = 0f,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob())
    private var job: Job? = null
    private val buffer = ArrayList<SensorSample>(64_000)
    private val lastResult = AtomicReference<AnalysisResult?>(null)

    fun start(context: Context) {
        if (_state.value.recording) return
        buffer.clear()
        lastResult.set(null)
        val recorder = SensorRecorder(context.applicationContext)
        _state.value = State(recording = true)
        job = scope.launch {
            val startNs = System.nanoTime()
            recorder.stream().collect { s ->
                buffer.add(s)
                val elapsed = (System.nanoTime() - startNs) / 1e9f
                val rate = if (elapsed > 0) buffer.size / elapsed else 0f
                _state.value = State(
                    recording = true,
                    sampleCount = buffer.size,
                    elapsedSec = elapsed,
                    lastSampleRateHz = rate,
                )
            }
        }
    }

    fun stop(): AnalysisResult? {
        job?.cancel()
        scope.coroutineContext.cancelChildren()
        val snapshot = buffer.toList()
        val res = SignalAnalyzer.analyze(snapshot)
        lastResult.set(res)
        _state.value = State(recording = false, sampleCount = snapshot.size)
        return res
    }

    fun snapshot(): List<SensorSample> = buffer.toList()
    fun lastResult(): AnalysisResult? = lastResult.get()
}

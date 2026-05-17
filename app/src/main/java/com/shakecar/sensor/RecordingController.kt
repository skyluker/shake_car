package com.shakecar.sensor

import android.content.Context
import com.shakecar.domain.AnalysisResult
import com.shakecar.domain.SensorSample
import com.shakecar.domain.SignalAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton kontrolujący nagrywanie. Próbki strumieniowane do pliku binarnego
 * w katalogu cache (12 floatów + long timestamp na próbkę = 56 bajtów).
 * To pozwala później odczytać sesję dla eksportu CSV/JSON i ponownej analizy.
 */
object RecordingController {

    data class State(
        val recording: Boolean = false,
        val sampleCount: Int = 0,
        val elapsedSec: Float = 0f,
        val lastSampleRateHz: Float = 0f,
        val rawFilePath: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var rawFile: File? = null
    private var dos: DataOutputStream? = null
    private var sampleCount = 0
    private val lastResult = AtomicReference<AnalysisResult?>(null)
    private val lastSamples = AtomicReference<List<SensorSample>?>(null)

    fun start(context: Context) {
        if (_state.value.recording) return
        sampleCount = 0
        lastResult.set(null)
        lastSamples.set(null)
        val dir = File(context.cacheDir, "sessions").apply { mkdirs() }
        val file = File(dir, "session_${System.currentTimeMillis()}.bin")
        rawFile = file
        dos = DataOutputStream(FileOutputStream(file).buffered())

        val recorder = SensorRecorder(context.applicationContext)
        _state.value = State(recording = true, rawFilePath = file.absolutePath)
        job = scope.launch {
            val startNs = System.nanoTime()
            recorder.stream().collect { s ->
                writeSample(s)
                sampleCount++
                val elapsed = (System.nanoTime() - startNs) / 1e9f
                val rate = if (elapsed > 0) sampleCount / elapsed else 0f
                _state.value = State(
                    recording = true,
                    sampleCount = sampleCount,
                    elapsedSec = elapsed,
                    lastSampleRateHz = rate,
                    rawFilePath = file.absolutePath,
                )
            }
        }
    }

    suspend fun stop(): AnalysisResult? = withContext(Dispatchers.Default) {
        job?.cancel()
        scope.coroutineContext.cancelChildren()
        try { dos?.flush(); dos?.close() } catch (_: Exception) {}
        dos = null

        val samples = readAll()
        lastSamples.set(samples)
        val res = SignalAnalyzer.analyze(samples)
        lastResult.set(res)
        _state.value = State(
            recording = false,
            sampleCount = samples.size,
            rawFilePath = rawFile?.absolutePath,
        )
        res
    }

    fun lastResult(): AnalysisResult? = lastResult.get()
    fun lastSamples(): List<SensorSample> = lastSamples.get() ?: emptyList()

    @Synchronized
    private fun writeSample(s: SensorSample) {
        val out = dos ?: return
        out.writeLong(s.timestampNs)
        out.writeFloat(s.accX); out.writeFloat(s.accY); out.writeFloat(s.accZ)
        out.writeFloat(s.gravX); out.writeFloat(s.gravY); out.writeFloat(s.gravZ)
        out.writeFloat(s.gyrX); out.writeFloat(s.gyrY); out.writeFloat(s.gyrZ)
        out.writeFloat(s.speedKmh); out.writeFloat(s.gpsAccuracyM)
    }

    private fun readAll(): List<SensorSample> {
        val f = rawFile ?: return emptyList()
        if (!f.exists()) return emptyList()
        val out = ArrayList<SensorSample>(sampleCount.coerceAtLeast(0))
        DataInputStream(FileInputStream(f).buffered()).use { dis ->
            while (true) {
                try {
                    val t = dis.readLong()
                    val ax = dis.readFloat(); val ay = dis.readFloat(); val az = dis.readFloat()
                    val gx = dis.readFloat(); val gy = dis.readFloat(); val gz = dis.readFloat()
                    val rx = dis.readFloat(); val ry = dis.readFloat(); val rz = dis.readFloat()
                    val sp = dis.readFloat(); val acc = dis.readFloat()
                    out += SensorSample(
                        timestampNs = t,
                        accX = ax, accY = ay, accZ = az,
                        gravX = gx, gravY = gy, gravZ = gz,
                        gyrX = rx, gyrY = ry, gyrZ = rz,
                        speedKmh = sp, gpsAccuracyM = acc,
                    )
                } catch (_: Exception) {
                    break
                }
            }
        }
        return out
    }
}

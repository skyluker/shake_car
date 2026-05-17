package com.shakecar.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shakecar.domain.FrequencyBand
import com.shakecar.domain.Severity
import com.shakecar.domain.SurfaceClassifier
import com.shakecar.sensor.RecordingController
import com.shakecar.ui.AnalysisViewModel
import com.shakecar.ui.AppViewModels
import kotlin.math.log10
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(sessionId: Long, nav: NavController) {
    val ctx = LocalContext.current
    val vm: AnalysisViewModel = viewModel(factory = AppViewModels.Factory)
    val findings by vm.findings.collectAsState()
    val exportStatus by vm.exportStatus.collectAsState()
    val result = remember { RecordingController.lastResult() }
    val surface = remember(result) { result?.let { SurfaceClassifier.classify(it) } }

    LaunchedEffect(result) { result?.let { vm.computeFindings(it) } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { vm.exportCsv(sessionId, ctx.contentResolver, it) } }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportJson(sessionId, ctx.contentResolver, it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Analiza sesji") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (result == null) {
                Text("Brak wyniku analizy w pamięci. Wykonaj nowe nagranie.")
                return@Column
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Czas: %.1f s   Fs: %.0f Hz".format(result.durationSec, result.sampleRateHz))
                    Text("Wykorzystane próbki: %.0f%%".format(result.acceptedFraction * 100))
                    if (!result.avgSpeedKmh.isNaN()) Text("Średnia prędkość: %.0f km/h".format(result.avgSpeedKmh))
                    Text("RMS pion: %.3f m/s²".format(result.rmsVertical))
                    Text("RMS Wk (ISO 2631): %.3f m/s²".format(result.rmsWeightedIso2631))
                    Text("Crest factor: %.2f".format(result.crestFactor))
                    Text("Pik nadwozia: %.2f Hz".format(result.bodyBounceFreqHz))
                    Text("Damping ratio ζ: %.3f".format(result.dampingRatioBody))
                    Spacer(Modifier.height(4.dp))
                    Text("Score komfortu: %.0f / 100".format(result.comfortScore))
                    Text("Score zawieszenia: %.0f / 100".format(result.suspensionScore))
                }
            }

            surface?.let { sd ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Wykryta nawierzchnia", style = MaterialTheme.typography.titleSmall)
                        Text(sd.type.label, style = MaterialTheme.typography.titleMedium)
                        Text("Pewność: %.0f%%".format(sd.confidence * 100))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "centroid %.1f Hz · flatness %.2f · low %.0f%% · mid %.0f%% · high %.0f%%".format(
                                sd.features.spectralCentroid,
                                sd.features.spectralFlatness,
                                sd.features.lowBandFraction * 100,
                                sd.features.midBandFraction * 100,
                                sd.features.highBandFraction * 100,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text("Spektrum mocy", style = MaterialTheme.typography.titleMedium)
            SpectrumChart(
                freqs = result.frequencies,
                psd = result.powerSpectrum,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )

            Text("Energia w pasmach", style = MaterialTheme.typography.titleMedium)
            BandsChart(
                bandEnergy = result.bandEnergy,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )

            Text("Diagnoza", style = MaterialTheme.typography.titleMedium)
            findings.forEach { f ->
                val color = when (f.severity) {
                    Severity.OK -> Color(0xFF2E7D32)
                    Severity.WARNING -> Color(0xFFEF6C00)
                    Severity.ALERT -> Color(0xFFC62828)
                }
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(f.component, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(f.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text("Eksport", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { csvLauncher.launch("shakecar_session_${sessionId}.csv") },
                    modifier = Modifier.weight(1f),
                ) { Text("CSV (próbki)") }
                FilledTonalButton(
                    onClick = { jsonLauncher.launch("shakecar_session_${sessionId}.json") },
                    modifier = Modifier.weight(1f),
                ) { Text("JSON (analiza)") }
            }
            exportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SpectrumChart(freqs: FloatArray, psd: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFFF5F5F5))) {
        if (psd.isEmpty()) return@Canvas
        val maxF = 40f
        val maxIdx = freqs.indexOfLast { it <= maxF }.coerceAtLeast(1)
        var pmax = 0f
        for (i in 1..maxIdx) if (psd[i] > pmax) pmax = psd[i]
        val logMax = log10(max(pmax, 1e-9f).toDouble()).toFloat()
        val logMin = logMax - 4f
        val w = size.width
        val h = size.height
        val step = w / maxIdx.toFloat()
        for (i in 1..maxIdx) {
            val v = log10(max(psd[i], 1e-12f).toDouble()).toFloat()
            val y = h - ((v - logMin) / (logMax - logMin)).coerceIn(0f, 1f) * h
            drawLine(
                color = Color(0xFF1976D2),
                start = Offset(i * step, h),
                end = Offset(i * step, y),
                strokeWidth = 1.5f,
            )
        }
    }
}

@Composable
private fun BandsChart(bandEnergy: Map<FrequencyBand, Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFFF5F5F5))) {
        val bands = FrequencyBand.values()
        val maxE = (bandEnergy.values.maxOrNull() ?: 1f).coerceAtLeast(1e-9f)
        val barW = size.width / bands.size
        bands.forEachIndexed { idx, b ->
            val e = bandEnergy[b] ?: 0f
            val barH = (e / maxE) * size.height
            drawRect(
                color = Color(0xFF388E3C),
                topLeft = Offset(idx * barW + 6f, size.height - barH),
                size = androidx.compose.ui.geometry.Size(barW - 12f, barH),
            )
            drawContext.canvas.nativeCanvas.apply {
                val tp = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 22f
                }
                drawText("%.1f-%.0fHz".format(b.lowHz, b.highHz), idx * barW + 8f, size.height - 4f, tp)
            }
        }
        drawRect(color = Color.LightGray, style = Stroke(width = 1f))
    }
}

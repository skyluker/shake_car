package com.shakecar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shakecar.domain.FrequencyBand
import com.shakecar.domain.Severity
import com.shakecar.sensor.RecordingController
import com.shakecar.ui.AnalysisViewModel
import com.shakecar.ui.AppViewModels
import kotlin.math.log10
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(sessionId: Long, nav: NavController) {
    val vm: AnalysisViewModel = viewModel(factory = AppViewModels.Factory)
    val findings by vm.findings.collectAsState()
    val result = remember { RecordingController.lastResult() }

    LaunchedEffect(result) { result?.let { vm.computeFindings(it) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Analiza sesji") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (result == null) {
                Text("Brak wyniku analizy w pami\u0119ci. Wykonaj nowe nagranie.")
                return@Column
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Czas: %.1f s   Fs: %.0f Hz".format(result.durationSec, result.sampleRateHz))
                    Text("RMS pion: %.3f m/s\u00b2".format(result.rmsVertical))
                    Text("RMS Wk (ISO 2631): %.3f m/s\u00b2".format(result.rmsWeightedIso2631))
                    Text("Crest factor: %.2f".format(result.crestFactor))
                    Text("Pik nadwozia: %.2f Hz".format(result.bodyBounceFreqHz))
                    Text("Damping ratio \u03b6: %.3f".format(result.dampingRatioBody))
                    Spacer(Modifier.height(4.dp))
                    Text("Score komfortu: %.0f / 100".format(result.comfortScore))
                    Text("Score zawieszenia: %.0f / 100".format(result.suspensionScore))
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
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(f.component, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(f.explanation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
        // Stroke ramki
        drawRect(color = Color.LightGray, style = Stroke(width = 1f))
    }
}

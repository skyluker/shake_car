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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shakecar.data.SessionEntity
import com.shakecar.domain.SurfaceType
import com.shakecar.ui.AppViewModels
import com.shakecar.ui.TrendViewModel
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(vehicleId: Long, nav: NavController) {
    val vm: TrendViewModel = viewModel(factory = AppViewModels.Factory)
    LaunchedEffect(vehicleId) { vm.setVehicle(vehicleId) }
    val sessions by vm.sessions.collectAsState()

    var surfaceFilter by remember { mutableStateOf<SurfaceType?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val filtered = sessions.filter { s ->
        s.mileageKm != null && s.dampingRatio != null &&
            (surfaceFilter == null || s.surfaceType == surfaceFilter!!.name)
    }.sortedBy { it.mileageKm }

    Scaffold(topBar = { TopAppBar(title = { Text("Trend kondycji") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Porównuj sesje wykonane w podobnych warunkach. Filtr po typie nawierzchni " +
                    "minimalizuje zaburzenie - im więcej punktów na tej samej drodze, tym wiarygodniejszy trend.",
                style = MaterialTheme.typography.bodyMedium,
            )

            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = surfaceFilter?.label ?: "Wszystkie",
                    onValueChange = {},
                    label = { Text("Filtr nawierzchni") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Wszystkie") },
                        onClick = { surfaceFilter = null; menuOpen = false }
                    )
                    SurfaceType.values().forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st.label) },
                            onClick = { surfaceFilter = st; menuOpen = false }
                        )
                    }
                }
            }

            if (filtered.size < 2) {
                Card { Text(
                    "Za mało sesji do wyrysowania trendu. Wykonaj co najmniej 2 nagrania " +
                        "z wpisanym przebiegiem (najlepiej w odstępach ~10 000 km).",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                ) }
                return@Column
            }

            Text("Współczynnik tłumienia ζ vs przebieg", style = MaterialTheme.typography.titleMedium)
            TrendChart(
                points = filtered.map { (it.mileageKm!!).toFloat() to it.dampingRatio!! },
                yLabel = "ζ",
                xLabel = "km",
                lineColor = Color(0xFF1976D2),
                horizontalRefs = listOf(0.10f to "alarm", 0.20f to "uwaga", 0.30f to "OK"),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )

            val susPoints = filtered
                .filter { it.suspensionScore != null }
                .map { it.mileageKm!!.toFloat() to it.suspensionScore!! }
            if (susPoints.size >= 2) {
                Text("Score zawieszenia vs przebieg", style = MaterialTheme.typography.titleMedium)
                TrendChart(
                    points = susPoints,
                    yLabel = "score",
                    xLabel = "km",
                    lineColor = Color(0xFF388E3C),
                    horizontalRefs = listOf(50f to "50"),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }

            val rmsPoints = filtered
                .filter { it.rmsWeighted != null }
                .map { it.mileageKm!!.toFloat() to it.rmsWeighted!! }
            if (rmsPoints.size >= 2) {
                Text("RMS Wk (komfort) vs przebieg", style = MaterialTheme.typography.titleMedium)
                TrendChart(
                    points = rmsPoints,
                    yLabel = "m/s²",
                    xLabel = "km",
                    lineColor = Color(0xFFEF6C00),
                    horizontalRefs = listOf(0.315f to "ISO próg"),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }

            Text("${filtered.size} sesji w wykresie", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrendChart(
    points: List<Pair<Float, Float>>,
    yLabel: String,
    xLabel: String,
    lineColor: Color,
    horizontalRefs: List<Pair<Float, String>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.background(Color(0xFFF7F7F7))) {
        if (points.size < 2) return@Canvas
        val padL = 60f; val padR = 16f; val padT = 16f; val padB = 32f
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val xMin = xs.min(); val xMax = xs.max().let { if (it == xMin) it + 1f else it }
        var yMin = ys.min(); var yMax = ys.max()
        // Włącz progi referencyjne do skali
        for ((y, _) in horizontalRefs) {
            yMin = min(yMin, y); yMax = max(yMax, y)
        }
        if (yMax - yMin < 1e-3f) { yMax = yMin + 1f }
        val yRange = yMax - yMin
        // Margines pionowy 10%
        yMin -= yRange * 0.1f
        yMax += yRange * 0.1f

        fun xPx(x: Float) = padL + (x - xMin) / (xMax - xMin) * w
        fun yPx(y: Float) = padT + h - (y - yMin) / (yMax - yMin) * h

        // Osie
        drawLine(Color.Gray, Offset(padL, padT), Offset(padL, padT + h), strokeWidth = 1.5f)
        drawLine(Color.Gray, Offset(padL, padT + h), Offset(padL + w, padT + h), strokeWidth = 1.5f)

        // Progi referencyjne
        val refPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 22f
        }
        for ((y, lab) in horizontalRefs) {
            val py = yPx(y)
            drawLine(
                Color(0x66888888),
                Offset(padL, py),
                Offset(padL + w, py),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(lab, padL + w - 60f, py - 4f, refPaint)
        }

        // Linia
        for (i in 1 until points.size) {
            val (x0, y0) = points[i - 1]
            val (x1, y1) = points[i]
            drawLine(
                lineColor,
                Offset(xPx(x0), yPx(y0)),
                Offset(xPx(x1), yPx(y1)),
                strokeWidth = 3f,
            )
        }
        // Punkty
        for ((x, y) in points) {
            drawCircle(lineColor, radius = 5f, center = Offset(xPx(x), yPx(y)))
        }

        // Etykiety osi
        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 24f
        }
        drawContext.canvas.nativeCanvas.apply {
            // Y min/max
            drawText("%.2f".format(yMax), 4f, padT + 16f, axisPaint)
            drawText("%.2f".format(yMin), 4f, padT + h, axisPaint)
            drawText(yLabel, 4f, padT + h / 2, axisPaint)
            // X min/max
            drawText("%.0f".format(xMin), padL, size.height - 6f, axisPaint)
            drawText("%.0f".format(xMax), padL + w - 60f, size.height - 6f, axisPaint)
            drawText(xLabel, padL + w / 2, size.height - 6f, axisPaint)
        }

        // Ramka
        drawRect(
            color = Color.LightGray,
            topLeft = Offset(padL, padT),
            size = Size(w, h),
            style = Stroke(width = 1f),
        )
    }
}

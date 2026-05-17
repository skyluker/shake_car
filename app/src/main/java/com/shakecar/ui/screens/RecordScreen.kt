package com.shakecar.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shakecar.sensor.RecordingController
import com.shakecar.sensor.RecordingService
import com.shakecar.ui.AppViewModels
import com.shakecar.ui.RecordViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vehicleId: Long, nav: NavController) {
    val ctx = LocalContext.current
    val vm: RecordViewModel = viewModel(factory = AppViewModels.Factory)
    val state by RecordingController.state.collectAsState()
    var mileage by remember { mutableStateOf("") }
    var road by remember { mutableStateOf("asfalt") }
    val sessionId by vm.sessionId.collectAsState()
    val coScope = rememberCoroutineScope()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Nagrywanie") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Przyklej telefon stabilnie do nadwozia. Aplikacja sama wykryje pion z TYPE_GRAVITY. " +
                    "Włącz GPS - próbki ze zmianą prędkości będą automatycznie odsiewane (stabilne okna ≥30 km/h, " +
                    "σ ≤ 5 km/h przez 5 s). Najlepiej jechać 60-90 km/h przez ~60 s.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(mileage, { mileage = it }, label = { Text("Przebieg (km)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(road, { road = it }, label = { Text("Typ nawierzchni") }, modifier = Modifier.fillMaxWidth())

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Stan: ${if (state.recording) "NAGRYWANIE" else "STOP"}")
                    Text("Próbek: ${state.sampleCount}")
                    Text("Czas: %.1f s".format(state.elapsedSec))
                    Text("Częstotliwość: %.0f Hz".format(state.lastSampleRateHz))
                }
            }

            if (!state.recording) {
                Button(
                    onClick = {
                        vm.startSession(vehicleId, mileage.toIntOrNull(), road.ifBlank { null })
                        ctx.startService(Intent(ctx, RecordingService::class.java))
                        RecordingController.start(ctx)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start nagrywania") }
            } else {
                Button(
                    onClick = {
                        coScope.launch {
                            val result = RecordingController.stop()
                            ctx.stopService(Intent(ctx, RecordingService::class.java))
                            vm.finishSession(
                                result = result,
                                rawFilePath = RecordingController.state.value.rawFilePath,
                                notes = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Stop i analizuj") }
            }

            sessionId?.let { sid ->
                if (!state.recording && state.sampleCount > 0) {
                    OutlinedButton(
                        onClick = { nav.navigate("analysis/$sid") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Zobacz wyniki") }
                }
            }
        }
    }
}

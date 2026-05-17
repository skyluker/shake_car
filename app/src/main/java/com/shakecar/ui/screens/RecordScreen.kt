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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(vehicleId: Long, nav: NavController) {
    val ctx = LocalContext.current
    val vm: RecordViewModel = viewModel(factory = AppViewModels.Factory)
    val state by RecordingController.state.collectAsState()
    var mileage by remember { mutableStateOf("") }
    var road by remember { mutableStateOf("asfalt") }
    val sessionId by vm.sessionId.collectAsState()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                "Przyklej telefon stabilnie do nadwozia (np. uchwyt na podsufitce lub konsoli). " +
                    "Jed\u017a r\u00f3wnomiernie 60-90 km/h przez ~60 sekund po jednolitej nawierzchni.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(mileage, { mileage = it }, label = { Text("Przebieg (km)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(road, { road = it }, label = { Text("Typ nawierzchni") }, modifier = Modifier.fillMaxWidth())

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Stan: ${if (state.recording) "NAGRYWANIE" else "STOP"}")
                    Text("Pr\u00f3bek: ${state.sampleCount}")
                    Text("Czas: %.1f s".format(state.elapsedSec))
                    Text("Cz\u0119stotliwo\u015b\u0107: %.0f Hz".format(state.lastSampleRateHz))
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
                        val result = RecordingController.stop()
                        ctx.stopService(Intent(ctx, RecordingService::class.java))
                        vm.finishSession(result, notes = null)
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

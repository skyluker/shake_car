package com.shakecar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.shakecar.ui.AppViewModels
import com.shakecar.ui.SessionsViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(vehicleId: Long, nav: NavController) {
    val vm: SessionsViewModel = viewModel(factory = AppViewModels.Factory)
    val sessions by vm.sessions.collectAsState()
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    val filtered = sessions.filter { it.vehicleId == vehicleId }

    Scaffold(topBar = { TopAppBar(title = { Text("Historia sesji") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (filtered.isEmpty()) item { Text("Brak nagra\u0144 dla tego pojazdu.") }
            items(filtered, key = { it.id }) { s ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(df.format(Date(s.startedAt)), style = MaterialTheme.typography.titleMedium)
                        s.mileageKm?.let { Text("Przebieg: $it km") }
                        s.dampingRatio?.let { Text("\u03b6 nadwozia: %.2f".format(it)) }
                        s.suspensionScore?.let { Text("Score zawieszenia: %.0f / 100".format(it)) }
                        s.comfortScore?.let { Text("Komfort: %.0f / 100".format(it)) }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { nav.navigate("analysis/${s.id}") }) { Text("Szczeg\u00f3\u0142y") }
                    }
                }
            }
        }
    }
}

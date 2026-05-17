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
import com.shakecar.ui.VehicleListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(nav: NavController) {
    val vm: VehicleListViewModel = viewModel(factory = AppViewModels.Factory)
    val vehicles by vm.vehicles.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ShakeCar - pojazdy") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showDialog = true }, text = { Text("Dodaj") }, icon = {})
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            if (vehicles.isEmpty()) {
                item { Text("Dodaj pierwszy pojazd, aby rozpocz\u0105\u0107 pomiary.") }
            }
            items(vehicles, key = { it.id }) { v ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("${v.make} ${v.model} (${v.year})", style = MaterialTheme.typography.titleMedium)
                        v.tireSpec?.let { Text("Opony: $it") }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { nav.navigate("record/${v.id}") }) { Text("Nagraj") }
                            OutlinedButton(onClick = { nav.navigate("sessions/${v.id}") }) { Text("Sesje") }
                            OutlinedButton(onClick = { nav.navigate("trend/${v.id}") }) { Text("Trend") }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddVehicleDialog(
            onDismiss = { showDialog = false },
            onAdd = { make, model, year, tires ->
                vm.add(make, model, year, tires)
                showDialog = false
            }
        )
    }
}

@Composable
private fun AddVehicleDialog(onDismiss: () -> Unit, onAdd: (String, String, Int, String?) -> Unit) {
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var tires by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowy pojazd") },
        confirmButton = {
            TextButton(onClick = {
                onAdd(make.ifBlank { "?" }, model.ifBlank { "?" }, year.toIntOrNull() ?: 0, tires.ifBlank { null })
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(make, { make = it }, label = { Text("Marka") })
                OutlinedTextField(model, { model = it }, label = { Text("Model") })
                OutlinedTextField(year, { year = it }, label = { Text("Rok") })
                OutlinedTextField(tires, { tires = it }, label = { Text("Opony (np. 205/55R16)") })
            }
        }
    )
}

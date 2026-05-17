package com.shakecar.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shakecar.ui.screens.AnalysisScreen
import com.shakecar.ui.screens.RecordScreen
import com.shakecar.ui.screens.SessionsScreen
import com.shakecar.ui.screens.TrendScreen
import com.shakecar.ui.screens.VehicleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "vehicles") {
                        composable("vehicles") { VehicleScreen(nav) }
                        composable("record/{vehicleId}") { entry ->
                            val vid = entry.arguments?.getString("vehicleId")?.toLongOrNull() ?: 0L
                            RecordScreen(vehicleId = vid, nav = nav)
                        }
                        composable("sessions/{vehicleId}") { entry ->
                            val vid = entry.arguments?.getString("vehicleId")?.toLongOrNull() ?: 0L
                            SessionsScreen(vehicleId = vid, nav = nav)
                        }
                        composable("trend/{vehicleId}") { entry ->
                            val vid = entry.arguments?.getString("vehicleId")?.toLongOrNull() ?: 0L
                            TrendScreen(vehicleId = vid, nav = nav)
                        }
                        composable("analysis/{sessionId}") { entry ->
                            val sid = entry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
                            AnalysisScreen(sessionId = sid, nav = nav)
                        }
                    }
                }
            }
        }
    }
}

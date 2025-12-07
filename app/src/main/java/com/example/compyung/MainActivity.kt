package com.example.compyung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothClient: BluetoothClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothClient = BluetoothClient(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                bluetoothClient = bluetoothClient,
                                onNavigateToModeSelection = { navController.navigate("detection_mode") }
                            )
                        }
                        composable("detection_mode") {
                            DetectionModeScreen(
                                onNavigateToHandTracking = { navController.navigate("camera") },
                                onNavigateToFaceDetection = { navController.navigate("face_camera") }
                            )
                        }
                        composable("camera") {
                            // CameraScreen에도 bluetoothClient를 넘겨줘야 나중에 제어 가능
                            CameraScreen(bluetoothClient = bluetoothClient)
                        }
                        composable("face_camera") {
                            FaceCameraScreen(bluetoothClient = bluetoothClient)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothClient.disconnect()
    }
}


package com.example.compyung

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bluetoothClient: BluetoothClient,
    onNavigateToModeSelection: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 블루투스 기기 목록 다이얼로그 상태
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectionStatus by remember { mutableStateOf("연결 안됨") }

    // 권한 요청 런처
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // 권한 허용되면 바로 페어링 목록 가져오고 다이얼로그 띄움
            pairedDevices = bluetoothClient.getPairedDevices()
            showBluetoothDialog = true
        } else {
            Toast.makeText(context, "블루투스 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "피사체 인식 앱",
            fontSize = 32.sp,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "상태: $connectionStatus",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        // 1. 블루투스 설정 (시스템 설정으로 이동)
        MenuCard(title = "블루투스 설정 (페어링)") {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            context.startActivity(intent)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 기기 연결 (앱 내 연결) - 새로 추가됨
        MenuCard(title = "기기 연결 (HC-05)") {
            // 권한 체크 및 요청 -> 다이얼로그
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            } else {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        //  3. 카메라 감지 (네비게이션 + 데이터 전송 테스트)
        MenuCard(title = "카메라 감지") {
            scope.launch {
                // 바로 모드 선택 화면으로 이동
                onNavigateToModeSelection()
            }
        }
    }

    // 블루투스 기기 선택 다이얼로그
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = { Text("블루투스 기기 선택") },
            text = {
                LazyColumn {
                    if (pairedDevices.isEmpty()) {
                        item { Text("페어링된 기기가 없습니다. 설정에서 먼저 페어링하세요.") }
                    } else {
                        items(pairedDevices) { device ->
                            BluetoothDeviceItem(device) {
                                scope.launch {
                                    connectionStatus = "연결 중..."
                                    showBluetoothDialog = false
                                    val success = bluetoothClient.connect(device)
                                    connectionStatus = if (success) {
                                        "연결됨: ${device.name}"
                                    } else {
                                        "연결 실패"
                                    }
                                    Toast.makeText(context, connectionStatus, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothDeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyLarge)
            Text(text = device.address, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCard(title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = title, fontSize = 20.sp)
        }
    }
}

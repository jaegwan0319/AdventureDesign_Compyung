package com.example.compyung

import android.Manifest
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

@Composable
fun CameraScreen(bluetoothClient: BluetoothClient) {
    val context = LocalContext.current
    
    // 카메라 권한 상태
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        CameraContent(bluetoothClient)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("카메라 권한이 필요합니다.")
        }
    }
}

@Composable
fun CameraContent(bluetoothClient: BluetoothClient) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 상태 관리
    var handLandmarkerResult by remember { mutableStateOf<HandLandmarkerResult?>(null) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    
    // 이미지 및 뷰 크기 상태
    var imageSize by remember { mutableStateOf(Size(480, 640)) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 추적 타겟 좌표 (0.0 ~ 1.0 정규화 좌표)
    var targetHandCenter by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    
    // [Smoothing] 이전 좌표 저장용 변수
    var prevX by remember { mutableFloatStateOf(-1f) }
    var prevY by remember { mutableFloatStateOf(-1f) }
    var prevZ by remember { mutableFloatStateOf(-1f) }
    val smoothingFactor = 0.8f // 반응성을 높이기 위해 상향 (기존 0.5 -> 0.8)

    var lastSendTime by remember { mutableLongStateOf(0L) }
    var statusText by remember { mutableStateOf("화면을 터치하여 손을 선택하세요") }

    // 데이터 전송 및 추적 로직
    LaunchedEffect(handLandmarkerResult, viewSize) {
        val currentTime = System.currentTimeMillis()
        
        // 0.1초(100ms)마다 실행
        if (currentTime - lastSendTime >= 100) {
            val result = handLandmarkerResult
            var message = "-1,-1,-1\n" // 기본값 (감지 안됨)

            if (result != null && result.landmarks().isNotEmpty()) {
                val hands = result.landmarks()
                
                // [수정] 타겟이 없으면 첫 번째 손을 자동으로 타겟으로 설정
                if (targetHandCenter == null) {
                    val firstHand = hands[0]
                    // 5번(검지 뿌리)과 13번(약지 뿌리)의 중간값을 중심으로 사용 (안정성 향상)
                    val indexMcp = firstHand[5]
                    val ringMcp = firstHand[13]
                    val centerX = (indexMcp.x() + ringMcp.x()) / 2
                    val centerY = (indexMcp.y() + ringMcp.y()) / 2
                    targetHandCenter = Pair(centerX, centerY)
                }
                
                // 타겟 추적 로직
                var minDistance = Float.MAX_VALUE
                var closestHandCenter: Pair<Float, Float>? = null
                // Z값 (거리) 추정을 위한 변수 (손목~중지 길이 이용)
                var closestHandDepth = 0f 
                
                for (hand in hands) {
                    // 5번(검지 뿌리)과 13번(약지 뿌리)의 중간값을 중심으로 사용
                    val indexMcp = hand[5]
                    val ringMcp = hand[13]
                    val cx = (indexMcp.x() + ringMcp.x()) / 2
                    val cy = (indexMcp.y() + ringMcp.y()) / 2
                    
                    val dx = cx - targetHandCenter!!.first
                    val dy = cy - targetHandCenter!!.second
                    val dist = dx*dx + dy*dy
                    
                    if (dist < minDistance) {
                        minDistance = dist
                        closestHandCenter = Pair(cx, cy)
                        
                        // Z값 계산: 손목(0)과 중지 끝(12) 사이의 거리로 추정
                        // 가까울수록(크게 보일수록) 값이 커짐
                        val wrist = hand[0]
                        val middleTip = hand[12]
                        val sizeDist = kotlin.math.sqrt(
                            (wrist.x() - middleTip.x()) * (wrist.x() - middleTip.x()) + 
                            (wrist.y() - middleTip.y()) * (wrist.y() - middleTip.y())
                        )
                        closestHandDepth = sizeDist
                    }
                }
                
                // 거리 임계값 이내라면 갱신
                if (minDistance < 0.2f && closestHandCenter != null) {
                    targetHandCenter = closestHandCenter // 갱신
                    
                    // 좌표 변환 적용 (뷰 크기가 유효할 때만)
                    if (viewSize.width > 0 && viewSize.height > 0) {
                        val (screenX, screenY) = transformCoordinate(
                            targetHandCenter!!.first, targetHandCenter!!.second,
                            imageSize.width, imageSize.height,
                            viewSize.width, viewSize.height
                        )
                        
                        // X, Y: 0~1000 범위로 변환
                        var rawX = (screenX / viewSize.width * 1000).toFloat()
                        var rawY = (screenY / viewSize.height * 1000).toFloat()
                        
                        // Z: 0~1000 범위로 변환
                        var rawZ = (closestHandDepth * 2000).toFloat()

                        // [Deadband] 미세 떨림 방지 (임계값: 10.0f)
                        val deadband = 10.0f
                        var shouldUpdate = false

                        if (prevX == -1f) {
                            shouldUpdate = true
                        } else {
                            val deltaX = kotlin.math.abs(rawX - prevX)
                            val deltaY = kotlin.math.abs(rawY - prevY)
                            val deltaZ = kotlin.math.abs(rawZ - prevZ)
                            
                            // 변화량이 임계값보다 클 때만 업데이트
                            if (deltaX > deadband || deltaY > deadband || deltaZ > deadband) {
                                shouldUpdate = true
                            }
                        }

                        if (shouldUpdate) {
                            // [Smoothing] 지수 이동 평균(EMA) 필터 적용
                            if (prevX == -1f) {
                                prevX = rawX
                                prevY = rawY
                                prevZ = rawZ
                            } else {
                                prevX = prevX * smoothingFactor + rawX * (1 - smoothingFactor)
                                prevY = prevY * smoothingFactor + rawY * (1 - smoothingFactor)
                                prevZ = prevZ * smoothingFactor + rawZ * (1 - smoothingFactor)
                            }
                        }
                        // else: 변화량이 적으면 이전 값(prevX, prevY, prevZ)을 그대로 유지

                        val finalX = prevX.toInt().coerceIn(0, 1000)
                        val finalY = prevY.toInt().coerceIn(0, 1000)
                        val finalZ = prevZ.toInt().coerceIn(0, 1000)
                        
                        message = "$finalX,$finalY,$finalZ\n"
                        statusText = "추적 중: $finalX, $finalY, Z:$finalZ"
                    }
                } else {
                    // 놓쳤으면 타겟 초기화 (다음 프레임에 자동으로 첫 번째 손 잡음)
                    statusText = "대상 놓침 -> 재탐색"
                    targetHandCenter = null
                    // 스무딩 초기화
                    prevX = -1f
                    prevY = -1f
                    prevZ = -1f
                }
            } else {
                statusText = "감지 안됨"
                targetHandCenter = null
                // 스무딩 초기화
                prevX = -1f
                prevY = -1f
                prevZ = -1f
            }

            // 블루투스 전송
            if (bluetoothClient.isConnected()) {
                bluetoothClient.sendData(message)
                Log.d("HandTracker", "Sent: $message")
            }
            lastSendTime = currentTime
        }
    }
    
    // Helper 초기화
    val helper = remember {
        HandLandmarkerHelper(context, object : HandLandmarkerHelper.LandmarkerListener {
            override fun onError(error: String) {
                Log.e("HandTracker", error)
            }

            override fun onResults(result: HandLandmarkerResult, imageHeight: Int, imageWidth: Int) {
                handLandmarkerResult = result
                if (imageSize.width != imageWidth || imageSize.height != imageHeight) {
                    imageSize = Size(imageWidth, imageHeight)
                }
            }
        })
    }
    
    // 리소스 정리
    DisposableEffect(Unit) {
        onDispose { helper.clear() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewSize = coordinates.size
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // 터치 시 가장 가까운 손을 타겟으로 설정
                    handLandmarkerResult?.let { result ->
                        var minDistance = Float.MAX_VALUE
                        var selectedHandCenter: Pair<Float, Float>? = null
                        
                        for (hand in result.landmarks()) {
                            // 5번(검지 뿌리)과 13번(약지 뿌리)의 중간값을 중심으로 사용
                            val indexMcp = hand[5]
                            val ringMcp = hand[13]
                            val cx = (indexMcp.x() + ringMcp.x()) / 2
                            val cy = (indexMcp.y() + ringMcp.y()) / 2
                            
                            // 화면 좌표로 변환하여 거리 계산
                            val (screenX, screenY) = transformCoordinate(
                                cx, cy,
                                imageSize.width, imageSize.height,
                                size.width, size.height
                            )
                            
                            val dx = screenX - offset.x
                            val dy = screenY - offset.y
                            val dist = dx*dx + dy*dy
                            
                            // 픽셀 거리 제곱 비교 (대략 100픽셀 반경 내)
                            if (dist < minDistance) {
                                minDistance = dist
                                selectedHandCenter = Pair(cx, cy)
                            }
                        }
                        
                        if (selectedHandCenter != null) {
                            targetHandCenter = selectedHandCenter
                            statusText = "타겟 설정됨"
                        }
                    }
                }
            }
    ) {
        // 1. 카메라 미리보기
        key(cameraSelector) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setTargetResolution(Size(640, 480)) // 해상도 낮춤 (과부하 방지)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also {
                                it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    try {
                                        val isFront = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                                        helper.detectLiveStream(imageProxy, isFront)
                                    } catch (e: Exception) {
                                        Log.e("Camera", "Analysis failed", e)
                                        imageProxy.close() // 안전하게 닫기
                                    }
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalyzer
                            )
                        } catch (e: Exception) {
                            Log.e("Camera", "Binding failed", e)
                        }
                    }, executor)
                    previewView
                }
            )
        }

        // 2. 오버레이
        OverlayView(
            result = handLandmarkerResult,
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
            modifier = Modifier.fillMaxSize()
        )
        
        // 3. 상태 텍스트
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Text(
                text = statusText,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }

        // 4. 카메라 전환 버튼
        FloatingActionButton(
            onClick = {
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera")
        }
    }
}


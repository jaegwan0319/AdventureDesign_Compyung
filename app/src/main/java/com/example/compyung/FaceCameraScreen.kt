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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Executors

@Composable
fun FaceCameraScreen(bluetoothClient: BluetoothClient) {
    val context = LocalContext.current
    
    // 카메라 권한 상태
    var hasCameraPermission by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        FaceCameraContent(bluetoothClient)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("카메라 권한이 필요합니다.")
        }
    }
}

@Composable
fun FaceCameraContent(bluetoothClient: BluetoothClient) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var faceLandmarkerResult by remember { mutableStateOf<FaceLandmarkerResult?>(null) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    
    // 이미지 및 뷰 크기 상태
    var imageSize by remember { mutableStateOf(Size(480, 640)) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 추적 타겟 좌표 (0.0 ~ 1.0 정규화 좌표)
    var targetFaceCenter by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var lastSendTime by remember { mutableLongStateOf(0L) }
    var statusText by remember { mutableStateOf("화면을 터치하여 얼굴을 선택하세요") }
    
    // Helper 초기화
    val helper = remember {
        FaceLandmarkerHelper(context, object : FaceLandmarkerHelper.LandmarkerListener {
            override fun onError(error: String) {
                Log.e("FaceTracker", error)
            }

            override fun onResults(result: FaceLandmarkerResult, imageHeight: Int, imageWidth: Int) {
                faceLandmarkerResult = result
                if (imageSize.width != imageWidth || imageSize.height != imageHeight) {
                    imageSize = Size(imageWidth, imageHeight)
                }
            }
        })
    }
    
    // 트래킹 및 전송 로직
    LaunchedEffect(faceLandmarkerResult, viewSize) {
        faceLandmarkerResult?.let { result ->
            val faces = result.faceLandmarks()
            if (faces.isEmpty()) {
                statusText = "얼굴 감지 안됨"
                return@let
            }

            // 1. 타겟이 이미 설정되어 있다면 -> 가장 가까운 얼굴 찾아서 갱신 (Tracking)
            // [수정] 타겟이 없으면 첫 번째 얼굴을 자동으로 타겟으로 설정
            if (targetFaceCenter == null) {
                val firstFace = faces[0]
                val nose = firstFace[1]
                targetFaceCenter = Pair(nose.x(), nose.y())
            }

            if (targetFaceCenter != null) {
                var minDistance = Float.MAX_VALUE
                var closestFaceCenter: Pair<Float, Float>? = null
                
                // 현재 프레임의 모든 얼굴 중, 이전 타겟 위치와 가장 가까운 놈 찾기
                for (face in faces) {
                    // 얼굴 중심 (코 끝: 인덱스 1)
                    val nose = face[1]
                    val cx = nose.x()
                    val cy = nose.y()
                    
                    val dx = cx - targetFaceCenter!!.first
                    val dy = cy - targetFaceCenter!!.second
                    val dist = dx*dx + dy*dy 
                    
                    if (dist < minDistance) {
                        minDistance = dist
                        closestFaceCenter = Pair(cx, cy)
                    }
                }
                
                // 일정 거리 이내라면 추적 계속
                if (minDistance < 0.2f && closestFaceCenter != null) { 
                    targetFaceCenter = closestFaceCenter // 타겟 위치 갱신
                    
                    // 좌표 전송 (0.1초 주기)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSendTime >= 100) {
                        // 좌표 변환 적용 (뷰 크기가 유효할 때만)
                        var percentX = -1
                        var percentY = -1
                        
                        if (viewSize.width > 0 && viewSize.height > 0) {
                            val (screenX, screenY) = transformCoordinate(
                                targetFaceCenter!!.first, targetFaceCenter!!.second,
                                imageSize.width, imageSize.height,
                                viewSize.width, viewSize.height
                            )
                            percentX = (screenX / viewSize.width * 100).toInt().coerceIn(0, 100)
                            percentY = (screenY / viewSize.height * 100).toInt().coerceIn(0, 100)
                        } else {
                            // 뷰 사이즈가 아직 없으면 기존 방식(정확하진 않음) 유지하거나 대기
                            percentX = (targetFaceCenter!!.first * 100).toInt().coerceIn(0, 100)
                            percentY = (targetFaceCenter!!.second * 100).toInt().coerceIn(0, 100)
                        }
                        
                        val message = "$percentX,$percentY\n"
                        if (bluetoothClient.isConnected()) {
                            bluetoothClient.sendData(message)
                            lastSendTime = currentTime
                            Log.d("FaceTracker", "Sent: $message")
                        }
                        statusText = "추적 중: $percentX, $percentY"
                    }
                } else {
                    // 놓쳤으면 타겟 초기화 (다음 프레임에 자동으로 첫 번째 얼굴 잡음)
                    statusText = "추적 대상 놓침 -> 재탐색"
                    targetFaceCenter = null
                }
            } else {
                statusText = "얼굴 감지 안됨"
            }
        }
    }
    
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
                    val normX = offset.x / size.width.toFloat()
                    val normY = offset.y / size.height.toFloat()
                    
                    // 현재 감지된 얼굴 중 터치 위치와 가장 가까운 얼굴 찾기
                    faceLandmarkerResult?.let { result ->
                        var minDistance = Float.MAX_VALUE
                        var selectedFaceCenter: Pair<Float, Float>? = null
                        
                        for (face in result.faceLandmarks()) {
                            val nose = face[1]
                            val cx = nose.x()
                            val cy = nose.y()
                            
                            // 화면 좌표로 변환하여 거리 계산
                            val (screenX, screenY) = transformCoordinate(
                                cx, cy,
                                imageSize.width, imageSize.height,
                                size.width, size.height
                            )
                            
                            val dx = screenX - offset.x
                            val dy = screenY - offset.y
                            val dist = dx*dx + dy*dy
                            
                            if (dist < minDistance) {
                                minDistance = dist
                                selectedFaceCenter = Pair(cx, cy)
                            }
                        }
                        
                        if (selectedFaceCenter != null) {
                            targetFaceCenter = selectedFaceCenter
                            statusText = "타겟 설정됨"
                        }
                    }
                }
            }
    ) {
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
                            .setTargetResolution(Size(640, 480))
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
                                        imageProxy.close()
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

        // 얼굴 오버레이
        FaceOverlayView(
            result = faceLandmarkerResult,
            imageWidth = imageSize.width,
            imageHeight = imageSize.height,
            modifier = Modifier.fillMaxSize()
        )
        
        // 상태 텍스트
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

        // 카메라 전환 버튼
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




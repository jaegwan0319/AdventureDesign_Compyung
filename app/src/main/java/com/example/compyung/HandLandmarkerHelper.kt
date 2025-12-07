package com.example.compyung

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    val context: Context,
    val listener: LandmarkerListener
) {
    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.GPU) // 성능 향상을 위해 GPU로 변경

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)
            .setNumHands(5) // 여러 손(5개) 감지 가능하도록 변경

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            listener.onError("HandLandmarker failed to initialize: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (handLandmarker == null) {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()

        // 1. 이미지 정보를 미리 변수에 추출 (이미지가 닫히기 전에 해야 함!)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height
        
        // 2. 비트맵 생성
        val bitmapBuffer = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
        )

        // 3. 픽셀 복사 및 ImageProxy 닫기
        // .use 블록이 끝나면 imageProxy.close()가 자동으로 호출됩니다.
        try {
            imageProxy.use { proxy ->
                bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
            }
        } catch (e: Exception) {
            listener.onError("Bitmap conversion failed: ${e.message}")
            return
        }
        
        // 4. Matrix 설정 (이제 imageProxy 객체 대신 아까 추출한 변수를 사용)
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat()) // 저장해둔 각도 사용
            if (isFrontCamera) {
                // 전면 카메라는 좌우 반전 필요
                postScale(-1f, 1f, width.toFloat(), height.toFloat())
            }
        }
        
        // 5. 회전된 비트맵 생성
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, width, height, matrix, true
        )

        // 6. MediaPipe 감지 요청
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        try {
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
             listener.onError("Detection failed: ${e.message}")
        }
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, input: MPImage) {
        listener.onResults(result, input.height, input.width)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        listener.onError(error.message ?: "Unknown error")
    }

    fun clear() {
        handLandmarker?.close()
        handLandmarker = null
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: HandLandmarkerResult, imageHeight: Int, imageWidth: Int)
    }
}


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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class FaceLandmarkerHelper(
    val context: Context,
    val listener: LandmarkerListener
) {
    private var faceLandmarker: FaceLandmarker? = null

    init {
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(Delegate.GPU) // GPU 권장

        val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)
            .setNumFaces(5) // 여러 명(5명) 감지 가능

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            listener.onError("FaceLandmarker failed to initialize: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height
        
        val bitmapBuffer = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888
        )

        try {
            imageProxy.use { proxy ->
                bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
            }
        } catch (e: Exception) {
            listener.onError("Bitmap conversion failed: ${e.message}")
            return
        }
        
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, width.toFloat(), height.toFloat())
            }
        }
        
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, width, height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        try {
            faceLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
             listener.onError("Detection failed: ${e.message}")
        }
    }

    private fun returnLivestreamResult(result: FaceLandmarkerResult, input: MPImage) {
        listener.onResults(result, input.height, input.width)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        listener.onError(error.message ?: "Unknown error")
    }

    fun clear() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(result: FaceLandmarkerResult, imageHeight: Int, imageWidth: Int)
    }
}



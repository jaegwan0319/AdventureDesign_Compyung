package com.example.compyung

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

@Composable
fun FaceOverlayView(
    result: FaceLandmarkerResult?,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. Scale 계산 (FILL_CENTER 방식)
        val scale = kotlin.math.max(size.width / imageWidth, size.height / imageHeight)
        
        // 2. 스케일링된 이미지 크기
        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        
        // 3. 중앙 정렬 Offset
        val offsetX = (size.width - scaledWidth) / 2
        val offsetY = (size.height - scaledHeight) / 2

        result?.let { faceResult ->
            for (landmarkList in faceResult.faceLandmarks()) {
                // 점의 수 줄이기 (5개마다 하나씩 그림 -> 전체의 약 20%)
                landmarkList.forEachIndexed { index, landmark ->
                    if (index % 5 == 0) {
                        drawCircle(
                            color = Color.Red, // 잘 보이는 빨간색 (불투명)
                            radius = 4f, // 두께(반지름) 조금 키움
                            center = Offset(
                                x = landmark.x() * scaledWidth + offsetX,
                                y = landmark.y() * scaledHeight + offsetY
                            )
                        )
                    }
                }
            }
        }
    }
}



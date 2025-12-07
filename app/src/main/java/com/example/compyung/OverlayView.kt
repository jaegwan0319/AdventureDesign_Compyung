package com.example.compyung

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

@Composable
fun OverlayView(
    result: HandLandmarkerResult?,
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

        result?.let { handResult ->
            for (landmarkList in handResult.landmarks()) {
                // 1. 관절 점(Point) 그리기
                for (landmark in landmarkList) {
                    drawCircle(
                        color = Color.Cyan,
                        radius = 8f,
                        center = Offset(
                            x = landmark.x() * scaledWidth + offsetX,
                            y = landmark.y() * scaledHeight + offsetY
                        )
                    )
                }

                // 2. 뼈대(Line) 그리기
                 HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                     val start = landmarkList[connection.start()]
                     val end = landmarkList[connection.end()]
                     
                     drawLine(
                         color = Color.White,
                         strokeWidth = 4f,
                         start = Offset(
                             start.x() * scaledWidth + offsetX, 
                             start.y() * scaledHeight + offsetY
                         ),
                         end = Offset(
                             end.x() * scaledWidth + offsetX, 
                             end.y() * scaledHeight + offsetY
                         )
                     )
                 }
            }
        }
    }
}




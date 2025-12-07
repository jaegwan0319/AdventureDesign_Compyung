package com.example.compyung

fun transformCoordinate(
    x: Float, y: Float,
    imageWidth: Int, imageHeight: Int,
    viewWidth: Int, viewHeight: Int
): Pair<Float, Float> {
    // 1. 화면 비율 (View Aspect Ratio)
    val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()
    // 2. 이미지 비율 (Image Aspect Ratio)
    val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()

    val scale: Float
    val offsetX: Float
    val offsetY: Float

    // PreviewView의 FILL_CENTER(또는 FIT_CENTER) 로직과 유사하게 동작하도록 수정
    // 일반적으로 PreviewView는 꽉 채우기(Zoom)를 하므로 max 스케일을 사용해야 함
    // 하지만 "위아래 짤림" 현상을 정확히 반영하려면 기준 축을 명확히 해야 함

    // 화면이 이미지보다 "더 세로로 길다" (viewAspectRatio < imageAspectRatio)
    // -> 높이에 맞춰야 꽉 참 (너비는 잘림) -> scale = viewHeight / imageHeight
    // 반대라면 -> 너비에 맞춰야 꽉 참 (높이는 잘림) -> scale = viewWidth / imageWidth
    
    // PreviewView의 기본 동작(ScaleType.FILL_CENTER)은 '더 큰 비율'을 따라가서 꽉 채우고 넘치는 부분을 자름
    // 1. Scale 계산: 화면을 꽉 채우기 위해 더 큰 스케일을 선택 (max)
    scale = kotlin.math.max(viewWidth.toFloat() / imageWidth.toFloat(), viewHeight.toFloat() / imageHeight.toFloat())

    // 2. 스케일링된 이미지 크기
    val scaledWidth = imageWidth * scale
    val scaledHeight = imageHeight * scale

    // 3. 중앙 정렬을 위한 Offset (이미지가 화면보다 크므로 음수 값이 나올 수 있음 -> 잘려나간 부분 보정)
    // 화면 크기에서 이미지를 빼고 2로 나누면, 이미지가 화면 중앙에 오도록 하는 시작 좌표가 됨
    offsetX = (viewWidth - scaledWidth) / 2
    offsetY = (viewHeight - scaledHeight) / 2

    return Pair(x * scaledWidth + offsetX, y * scaledHeight + offsetY)
}


package com.example.magneticcamera.domain.model

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class PhotoOverlayArea(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint
) {
    companion object {
        val FullFrame = PhotoOverlayArea(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(1f, 1f),
            bottomLeft = NormalizedPoint(0f, 1f)
        )
    }
}

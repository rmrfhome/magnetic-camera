package com.example.magneticcamera.core.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import kotlin.math.max
import kotlin.math.min

class OverlayRenderer {
    fun renderRectangularOverlay(
        photo: Bitmap,
        heatmap: Bitmap,
        area: PhotoOverlayArea,
        opacity: Float
    ): Bitmap {
        val output = photo.copy(Bitmap.Config.ARGB_8888, true)
        val rect = area.toRect(photo.width.toFloat(), photo.height.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        Canvas(output).drawBitmap(heatmap, null, rect, paint)
        return output
    }

    private fun PhotoOverlayArea.toRect(width: Float, height: Float): RectF {
        val xs = listOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
        val ys = listOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
        val left = xs.fold(1f) { acc, value -> min(acc, value) }.coerceIn(0f, 1f) * width
        val right = xs.fold(0f) { acc, value -> max(acc, value) }.coerceIn(0f, 1f) * width
        val top = ys.fold(1f) { acc, value -> min(acc, value) }.coerceIn(0f, 1f) * height
        val bottom = ys.fold(0f) { acc, value -> max(acc, value) }.coerceIn(0f, 1f) * height
        return RectF(left, top, right, bottom)
    }
}

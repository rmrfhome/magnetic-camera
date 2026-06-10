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
        opacity: Float,
        showGrid: Boolean = false,
        gridWidth: Int = 0,
        gridHeight: Int = 0
    ): Bitmap {
        val output = photo.copy(Bitmap.Config.ARGB_8888, true)
        val rect = area.toRect(photo.width.toFloat(), photo.height.toFloat())
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (opacity.coerceIn(0f, 1f) * 255).toInt()
        }
        canvas.drawBitmap(heatmap, null, rect, paint)
        if (showGrid) {
            drawGrid(canvas, rect, gridWidth, gridHeight)
        }
        return output
    }

    private fun drawGrid(canvas: Canvas, rect: RectF, cols: Int, rows: Int) {
        if (cols <= 1 && rows <= 1) return
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(130, 0, 0, 0)
            strokeWidth = 5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(190, 255, 255, 255)
            strokeWidth = 2f
        }
        listOf(shadowPaint, linePaint).forEach { paint ->
            for (col in 1 until cols) {
                val x = rect.left + rect.width() * col / cols
                canvas.drawLine(x, rect.top, x, rect.bottom, paint)
            }
            for (row in 1 until rows) {
                val y = rect.top + rect.height() * row / rows
                canvas.drawLine(rect.left, y, rect.right, y, paint)
            }
        }
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

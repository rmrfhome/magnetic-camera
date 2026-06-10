package com.example.magneticcamera.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.magneticcamera.core.graphics.HeatmapRender
import com.example.magneticcamera.core.graphics.HeatmapLegend
import java.io.File
import java.io.FileOutputStream

class PngExporter {
    fun saveHeatmap(
        render: HeatmapRender,
        file: File,
        includeLegend: Boolean = true,
        includeGrid: Boolean = false,
        gridWidth: Int = 0,
        gridHeight: Int = 0
    ): String {
        val bitmap = if (includeGrid) {
            render.toBitmap().withGrid(gridWidth, gridHeight)
        } else {
            render.toBitmap()
        }
        return saveBitmap(bitmap, file, render.legend.takeIf { includeLegend })
    }

    fun saveBitmap(bitmap: Bitmap, file: File, legend: HeatmapLegend? = null): String {
        file.parentFile?.mkdirs()
        val outputBitmap = if (legend == null) bitmap else bitmap.withLegend(legend)
        FileOutputStream(file).use { output ->
            check(outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode PNG export at ${file.absolutePath}."
            }
        }
        return file.absolutePath
    }

    private fun Bitmap.withLegend(legend: HeatmapLegend): Bitmap {
        val legendHeight = (height * 0.10f).toInt().coerceIn(96, 220)
        val output = Bitmap.createBitmap(width, height + legendHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(8, 11, 13))
        canvas.drawBitmap(this, 0f, 0f, null)

        val stripTop = height
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(234, 241, 244)
            textSize = (legendHeight * 0.22f).coerceAtLeast(22f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(184, 198, 204)
            textSize = (legendHeight * 0.16f).coerceAtLeast(18f)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val left = (width * 0.05f).toInt()
        val right = (width * 0.95f).toInt()
        val barTop = stripTop + (legendHeight * 0.50f).toInt()
        val barBottom = stripTop + (legendHeight * 0.68f).toInt()
        val segments = 160
        for (index in 0 until segments) {
            val fraction = index / (segments - 1).toFloat()
            val gray = (fraction * 255).toInt().coerceIn(0, 255)
            barPaint.color = Color.rgb(gray, gray, gray)
            val segmentLeft = left + ((right - left) * index / segments.toFloat()).toInt()
            val segmentRight = left + ((right - left) * (index + 1) / segments.toFloat()).toInt()
            canvas.drawRect(segmentLeft.toFloat(), barTop.toFloat(), segmentRight.toFloat(), barBottom.toFloat(), barPaint)
        }
        val title = "${legend.metricName} (${legend.unit})"
        canvas.drawText(title, left.toFloat(), stripTop + legendHeight * 0.30f, textPaint)

        val minText = "%.2f".format(legend.minValue)
        val maxText = "%.2f".format(legend.maxValue)
        canvas.drawText(minText, left.toFloat(), stripTop + legendHeight * 0.90f, labelPaint)
        val maxBounds = Rect()
        labelPaint.getTextBounds(maxText, 0, maxText.length, maxBounds)
        canvas.drawText(maxText, (right - maxBounds.width()).toFloat(), stripTop + legendHeight * 0.90f, labelPaint)
        return output
    }

    private fun Bitmap.withGrid(cols: Int, rows: Int): Bitmap {
        if (cols <= 1 && rows <= 1) return this
        val output = copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        drawGrid(canvas, rect, cols, rows)
        return output
    }

    private fun drawGrid(canvas: Canvas, rect: RectF, cols: Int, rows: Int) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 0, 0, 0)
            strokeWidth = 5f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
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
}

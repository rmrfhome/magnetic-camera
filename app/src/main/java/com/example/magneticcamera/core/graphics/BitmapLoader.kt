package com.example.magneticcamera.core.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object BitmapLoader {
    fun decode(
        context: Context,
        uriString: String,
        maxDimension: Int = 2_048
    ): Bitmap? {
        return runCatching {
            val uri = Uri.parse(uriString)
            val orientation = readExifOrientation(context, uri)
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return@runCatching null
                BitmapFactory.decodeStream(input, null, bounds)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxDimension = maxDimension
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    null
                } else {
                    BitmapFactory.decodeStream(input, null, decodeOptions)
                        ?.applyExifOrientation(orientation)
                }
            }
        }.getOrNull()
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return@runCatching ExifInterface.ORIENTATION_NORMAL
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return this
        }

        val transformed = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (transformed != this) recycle()
        return transformed
    }

    internal fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int
    ): Int {
        val largestDimension = maxOf(width, height)
        if (largestDimension <= 0) return 1

        val targetDimension = maxDimension.coerceAtLeast(1)
        var sampleSize = 1
        while (largestDimension / sampleSize > targetDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

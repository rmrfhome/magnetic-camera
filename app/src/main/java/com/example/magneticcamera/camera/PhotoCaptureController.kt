package com.example.magneticcamera.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.File

class PhotoCaptureController {
    fun capture(
        context: Context,
        imageCapture: ImageCapture,
        outputFile: File,
        onResult: (Result<String>) -> Unit
    ) {
        outputFile.parentFile?.mkdirs()
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onResult(Result.success(Uri.fromFile(outputFile).toString()))
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            }
        )
    }
}

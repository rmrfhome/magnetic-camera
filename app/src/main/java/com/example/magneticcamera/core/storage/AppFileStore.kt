package com.example.magneticcamera.core.storage

import android.content.Context
import android.net.Uri
import java.io.File

class AppFileStore(
    context: Context
) {
    private val root = context.applicationContext.filesDir

    fun photoFile(sessionId: String): File = fileIn("photos", "$sessionId.jpg")
    fun heatmapFile(sessionId: String): File = fileIn("heatmaps", "$sessionId.png")
    fun overlayFile(sessionId: String): File = fileIn("overlays", "$sessionId.png")
    fun rawJsonFile(sessionId: String): File = fileIn("raw", "$sessionId.json")
    fun csvExportFile(sessionId: String): File = fileIn("exports", "$sessionId.csv")

    fun uriString(file: File): String = Uri.fromFile(file).toString()

    fun writeText(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
    }

    private fun fileIn(directory: String, name: String): File {
        val dir = File(root, directory)
        dir.mkdirs()
        return File(dir, name)
    }
}

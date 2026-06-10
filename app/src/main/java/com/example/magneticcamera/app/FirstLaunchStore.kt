package com.example.magneticcamera.app

import android.content.Context

class FirstLaunchStore(
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences("magnetic-camera", Context.MODE_PRIVATE)

    fun shouldShowExplanation(): Boolean = !prefs.getBoolean(KEY_SEEN_EXPLANATION, false)

    fun markExplanationSeen() {
        prefs.edit().putBoolean(KEY_SEEN_EXPLANATION, true).apply()
    }

    private companion object {
        const val KEY_SEEN_EXPLANATION = "seen_explanation"
    }
}

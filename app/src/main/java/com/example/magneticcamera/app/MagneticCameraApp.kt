package com.example.magneticcamera.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun MagneticCameraApp() {
    val context = LocalContext.current
    val container = remember { AppContainer(context) }
    AppTheme {
        AppNavHost(container = container)
    }
}

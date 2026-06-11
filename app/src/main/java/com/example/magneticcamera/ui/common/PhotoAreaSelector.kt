package com.example.magneticcamera.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.core.graphics.BitmapLoader
import com.example.magneticcamera.domain.model.NormalizedPoint
import com.example.magneticcamera.domain.model.PhotoOverlayArea
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoAreaSelector(
    photoUri: String?,
    area: PhotoOverlayArea,
    onAreaChange: (PhotoOverlayArea) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberBitmap(photoUri, maxDimension = 1_600)
    val currentArea by rememberUpdatedState(area)
    val currentOnAreaChange by rememberUpdatedState(onAreaChange)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var activeCorner by remember { mutableIntStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .onSizeChanged { size = it }
            .pointerInput(size) {
                detectDragGestures(
                    onDragStart = { start ->
                        activeCorner = nearestCorner(start, currentArea, size)
                    },
                    onDragEnd = { activeCorner = -1 },
                    onDragCancel = { activeCorner = -1 },
                    onDrag = { change, _ ->
                        val corner = activeCorner
                        if (corner < 0 || size.width == 0 || size.height == 0) return@detectDragGestures
                        val point = NormalizedPoint(
                            x = (change.position.x / size.width).coerceIn(0f, 1f),
                            y = (change.position.y / size.height).coerceIn(0f, 1f)
                        )
                        currentOnAreaChange(currentArea.withCorner(corner, point))
                    }
                )
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Reference photo",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(Color.Black.copy(alpha = 0.28f))
            val points = area.points().map {
                Offset(it.x.coerceIn(0f, 1f) * this.size.width, it.y.coerceIn(0f, 1f) * this.size.height)
            }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(path, Color(0xFF44F2C7).copy(alpha = 0.22f))
            drawPath(path, Color(0xFF44F2C7), style = Stroke(width = 3.dp.toPx()))
            points.forEach { point ->
                drawCircle(Color(0xFFFFD447), radius = 10.dp.toPx(), center = point)
                drawCircle(Color.Black.copy(alpha = 0.55f), radius = 5.dp.toPx(), center = point)
            }
        }
    }
}

@Composable
fun rememberBitmap(
    uriString: String?,
    maxDimension: Int = 2_048
): Bitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(context, uriString, maxDimension) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(context, uriString, maxDimension) {
        bitmap = if (uriString == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                BitmapLoader.decode(context, uriString, maxDimension)
            }
        }
    }
    return bitmap
}

private fun PhotoOverlayArea.points(): List<NormalizedPoint> {
    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}

private fun PhotoOverlayArea.withCorner(index: Int, point: NormalizedPoint): PhotoOverlayArea {
    return when (index) {
        0 -> copy(topLeft = point)
        1 -> copy(topRight = point)
        2 -> copy(bottomRight = point)
        3 -> copy(bottomLeft = point)
        else -> this
    }
}

private fun nearestCorner(offset: Offset, area: PhotoOverlayArea, size: IntSize): Int {
    if (size.width == 0 || size.height == 0) return -1
    return area.points()
        .mapIndexed { index, point ->
            val x = point.x * size.width
            val y = point.y * size.height
            index to hypot((offset.x - x).toDouble(), (offset.y - y).toDouble())
        }
        .minByOrNull { it.second }
        ?.first ?: -1
}

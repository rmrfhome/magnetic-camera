package com.example.magneticcamera.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.magneticcamera.core.graphics.HeatmapRender
import com.example.magneticcamera.core.sensors.MagneticSample

@Composable
fun InstrumentPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
fun MetricReadout(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = if (emphasized) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                text = unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun StatusText(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alert: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.42f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            modifier = Modifier
                .weight(0.58f)
                .padding(start = 10.dp),
            text = value,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun MagneticGraph(
    samples: List<MagneticSample>,
    modifier: Modifier = Modifier,
    valueSelector: (MagneticSample) -> Float = { it.baselineVectorDeltaMicroTesla }
) {
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        repeat(5) { index ->
            val y = height * index / 4f
            drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        }
        repeat(6) { index ->
            val x = width * index / 5f
            drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
        }
        val values = samples.map(valueSelector).filter { it.isFinite() }
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = width * index / (values.lastIndex.coerceAtLeast(1)).toFloat()
            val y = height - ((value - min) / span).coerceIn(0f, 1f) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 3f))
    }
}

@Composable
fun HeatmapPreview(
    render: HeatmapRender,
    modifier: Modifier = Modifier
) {
    val image = remember(render) { render.toBitmap().asImageBitmap() }
    Image(
        modifier = modifier,
        bitmap = image,
        contentDescription = "Magnetic heatmap",
        contentScale = ContentScale.FillBounds
    )
}

@Composable
fun MessagePanel(
    message: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val text = errorMessage ?: message ?: return
    val isError = errorMessage != null
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

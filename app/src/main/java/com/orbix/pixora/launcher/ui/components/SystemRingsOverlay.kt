package com.orbix.pixora.launcher.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun SystemRingsOverlay(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF7C4DFF),
) {
    val context = LocalContext.current
    var ramPercent by remember { mutableFloatStateOf(0f) }
    var diskPercent by remember { mutableFloatStateOf(0f) }
    var animTime by remember { mutableLongStateOf(0L) }

    // Read system info periodically
    LaunchedEffect(Unit) {
        while (true) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            ramPercent = 1f - (memInfo.availMem.toFloat() / memInfo.totalMem.toFloat())

            try {
                val stat = StatFs(android.os.Environment.getDataDirectory().path)
                val total = stat.totalBytes.toFloat()
                val free = stat.availableBytes.toFloat()
                diskPercent = 1f - (free / total)
            } catch (_: Exception) { }

            kotlinx.coroutines.delay(5000)
        }
    }

    // Animation tick
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33)
            animTime += 33
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // RAM ring
        Canvas(modifier = Modifier.size(52.dp)) {
            drawMiniRing(
                label = "RAM",
                percent = ramPercent,
                glowColor = glowColor,
                animTime = animTime,
                textMeasurer = textMeasurer,
            )
        }

        // Disk ring
        Canvas(modifier = Modifier.size(52.dp)) {
            drawMiniRing(
                label = "DISK",
                percent = diskPercent,
                glowColor = glowColor,
                animTime = animTime,
                textMeasurer = textMeasurer,
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiniRing(
    label: String,
    percent: Float,
    glowColor: Color,
    animTime: Long,
    textMeasurer: TextMeasurer,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f - 6f
    val strokeWidth = 5f
    val sweepAngle = percent * 360f

    val ringColor = when {
        percent > 0.9f -> Color(0xFFFF1744)
        percent > 0.75f -> Color(0xFFFF9800)
        else -> glowColor
    }

    // Breathing animation
    val breathAlpha = 0.8f + 0.2f * sin(animTime / 1500f).toFloat()

    // Background ring
    drawArc(
        color = Color.White.copy(alpha = 0.06f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )

    // Active arc
    drawArc(
        color = ringColor.copy(alpha = breathAlpha),
        startAngle = -90f,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )

    // Percentage text
    val pctText = "${(percent * 100).toInt()}%"
    val pctResult = textMeasurer.measure(
        AnnotatedString(pctText),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    )
    drawText(
        pctResult,
        topLeft = Offset(
            center.x - pctResult.size.width / 2f,
            center.y - pctResult.size.height / 2f - 2f,
        )
    )

    // Label
    val labelResult = textMeasurer.measure(
        AnnotatedString(label),
        style = TextStyle(fontSize = 7.sp, color = Color.White.copy(alpha = 0.4f))
    )
    drawText(
        labelResult,
        topLeft = Offset(
            center.x - labelResult.size.width / 2f,
            center.y + pctResult.size.height / 2f - 2f,
        )
    )
}

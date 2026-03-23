package com.orbix.pixora.launcher.ui.components

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
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
    var tempCelsius by remember { mutableFloatStateOf(0f) }
    var animTime by remember { mutableLongStateOf(0L) }

    // Read RAM + disk periodically
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

    // Read battery temperature via sticky broadcast
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    // EXTRA_TEMPERATURE is in tenths of °C (e.g. 315 = 31.5°C)
                    val raw = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    tempCelsius = raw / 10f
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Animation tick
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33)
            animTime += 33
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // Temperature ring
        Canvas(modifier = Modifier.size(52.dp)) {
            drawTempRing(
                tempCelsius = tempCelsius,
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

/**
 * Temperature ring: maps 20°C–50°C to the arc.
 * Colors: cool (theme) → warm (orange) → hot (red).
 */
@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTempRing(
    tempCelsius: Float,
    glowColor: Color,
    animTime: Long,
    textMeasurer: TextMeasurer,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f - 6f
    val strokeWidth = 5f

    // Normalize: 20°C = 0%, 50°C = 100%
    val percent = ((tempCelsius - 20f) / 30f).coerceIn(0f, 1f)
    val sweepAngle = percent * 360f

    val ringColor = when {
        tempCelsius >= 45f -> Color(0xFFFF1744) // Hot — red
        tempCelsius >= 38f -> Color(0xFFFF9800) // Warm — orange
        tempCelsius >= 32f -> Color(0xFFFFEB3B) // Mild — yellow
        else -> glowColor                       // Cool — theme color
    }

    // Heat pulse: faster when hotter
    val pulseSpeed = if (tempCelsius >= 40f) 800f else 1500f
    val breathAlpha = 0.8f + 0.2f * sin(animTime / pulseSpeed).toFloat()

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

    // Temperature text
    val tempText = "${tempCelsius.toInt()}°"
    val tempResult = textMeasurer.measure(
        AnnotatedString(tempText),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    )
    drawText(
        tempResult,
        topLeft = Offset(
            center.x - tempResult.size.width / 2f,
            center.y - tempResult.size.height / 2f - 2f,
        )
    )

    // Label
    val labelResult = textMeasurer.measure(
        AnnotatedString("TEMP"),
        style = TextStyle(fontSize = 7.sp, color = Color.White.copy(alpha = 0.4f))
    )
    drawText(
        labelResult,
        topLeft = Offset(
            center.x - labelResult.size.width / 2f,
            center.y + tempResult.size.height / 2f - 2f,
        )
    )
}

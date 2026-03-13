package com.orbix.pixora.launcher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@OptIn(ExperimentalTextApi::class)
@Composable
fun BatteryRingOverlay(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF7C4DFF),
) {
    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(50) }
    var isCharging by remember { mutableStateOf(false) }
    var animTime by remember { mutableLongStateOf(0L) }

    // Battery broadcast
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    batteryLevel = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 50)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Animation
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33)
            animTime += 33
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.size(100.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 8f
        val strokeWidth = 8f

        val arcColor = when {
            batteryLevel < 15 -> Color(0xFFFF1744)
            batteryLevel < 30 -> Color(0xFFFF9800)
            else -> glowColor
        }

        val sweepAngle = batteryLevel * 360f / 100f

        // Charging pulse
        val alpha = if (isCharging) {
            0.6f + 0.4f * sin(animTime / 500f).toFloat()
        } else 1f

        // Background ring
        drawArc(
            color = Color.White.copy(alpha = 0.08f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Shadow glow
        drawArc(
            color = arcColor.copy(alpha = 0.2f * alpha),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth + 6f, cap = StrokeCap.Round),
        )

        // Main arc
        drawArc(
            color = arcColor.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Percentage text
        val percentText = "$batteryLevel"
        val percentResult = textMeasurer.measure(
            AnnotatedString(percentText),
            style = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        )
        drawText(
            percentResult,
            topLeft = Offset(
                center.x - percentResult.size.width / 2f,
                center.y - percentResult.size.height / 2f - 4f,
            )
        )

        // "%" label
        val labelResult = textMeasurer.measure(
            AnnotatedString(if (isCharging) "%" else "%"),
            style = TextStyle(
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        )
        drawText(
            labelResult,
            topLeft = Offset(
                center.x - labelResult.size.width / 2f,
                center.y + percentResult.size.height / 2f - 6f,
            )
        )

        // Bolt icon when charging
        if (isCharging) {
            val boltResult = textMeasurer.measure(
                AnnotatedString("⚡"),
                style = TextStyle(fontSize = 10.sp)
            )
            drawText(
                boltResult,
                topLeft = Offset(
                    center.x - boltResult.size.width / 2f,
                    center.y + percentResult.size.height / 2f + 6f,
                )
            )
        }
    }
}

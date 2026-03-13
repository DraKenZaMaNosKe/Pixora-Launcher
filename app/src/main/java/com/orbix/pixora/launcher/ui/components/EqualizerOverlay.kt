package com.orbix.pixora.launcher.ui.components

import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orbix.pixora.launcher.audio.AudioCaptureService
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val BAR_COUNT = 12
private const val SEGMENTS = 20

@Composable
fun EqualizerOverlay(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF7C4DFF),
) {
    val context = LocalContext.current
    val barLevels = remember { FloatArray(BAR_COUNT) }
    val peakLevels = remember { FloatArray(BAR_COUNT) }
    var frameCount by remember { mutableLongStateOf(0L) }

    val isCapturing by AudioCaptureService.isCapturing.collectAsState()
    val realBands by AudioCaptureService.bandLevels.collectAsState()
    val realPeaks by AudioCaptureService.peakLevels.collectAsState()

    // Real capture mode
    LaunchedEffect(isCapturing) {
        if (!isCapturing) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(33)
            val bands = realBands
            val peaks = realPeaks
            for (i in 0 until BAR_COUNT) {
                barLevels[i] = bands[i]
                peakLevels[i] = peaks[i]
            }
            frameCount++
        }
    }

    // Simulation fallback
    LaunchedEffect(isCapturing) {
        if (isCapturing) return@LaunchedEffect
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val random = Random(System.currentTimeMillis())
        var time = 0.0

        while (true) {
            kotlinx.coroutines.delay(50)
            time += 0.05
            val musicActive = audioManager.isMusicActive

            if (musicActive) {
                for (i in barLevels.indices) {
                    val freq1 = 1.5 + i * 0.2
                    val freq2 = 2.8 + i * 0.35
                    val freq3 = 0.7 + i * 0.15
                    val base = (sin(time * freq1) * 0.3 +
                            sin(time * freq2 + i) * 0.25 +
                            sin(time * freq3 + i * 2.0) * 0.2 +
                            0.35).toFloat()
                    val spike = if (random.nextFloat() < 0.08f) random.nextFloat() * 0.3f else 0f
                    val target = (base + spike).coerceIn(0.05f, 0.95f)
                    barLevels[i] = if (target > barLevels[i]) {
                        barLevels[i] + (target - barLevels[i]) * 0.35f
                    } else {
                        barLevels[i] + (target - barLevels[i]) * 0.15f
                    }
                    if (barLevels[i] > peakLevels[i]) peakLevels[i] = barLevels[i]
                }
            } else {
                for (i in barLevels.indices) barLevels[i] = max(0f, barLevels[i] - 0.03f)
            }
            for (i in peakLevels.indices) peakLevels[i] = max(barLevels[i], peakLevels[i] - 0.01f)
            frameCount++
        }
    }

    // Use frameCount to force recomposition
    @Suppress("UNUSED_EXPRESSION") frameCount

    Canvas(modifier = modifier.height(100.dp).fillMaxWidth()) {
        val totalWidth = size.width
        val totalHeight = size.height
        val halfHeight = totalHeight / 2f
        val barWidth = totalWidth / (BAR_COUNT * 1.8f)
        val gap = barWidth * 0.35f
        val totalBarsWidth = BAR_COUNT * barWidth + (BAR_COUNT - 1) * gap
        val startX = (totalWidth - totalBarsWidth) / 2f

        for (i in 0 until BAR_COUNT) {
            val x = startX + i * (barWidth + gap)
            val level = barLevels[i].coerceIn(0f, 1f)
            val peak = peakLevels[i].coerceIn(0f, 1f)
            val barColor = barGradientColor(i, BAR_COUNT)

            // Draw glow behind bar (wider, semi-transparent)
            val glowAlpha = (level * 0.3f).coerceIn(0f, 0.3f)
            val glowHeight = level * halfHeight
            // Upward glow
            drawRoundRect(
                color = barColor.copy(alpha = glowAlpha),
                topLeft = Offset(x - barWidth * 0.3f, halfHeight - glowHeight),
                size = Size(barWidth * 1.6f, glowHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )
            // Downward glow (mirror)
            drawRoundRect(
                color = barColor.copy(alpha = glowAlpha * 0.5f),
                topLeft = Offset(x - barWidth * 0.3f, halfHeight),
                size = Size(barWidth * 1.6f, glowHeight * 0.6f),
                cornerRadius = CornerRadius(4f, 4f),
            )

            // Draw main bars upward
            drawBarSegments(x, halfHeight, barWidth, level, barColor, goUp = true)

            // Draw mirror bars downward (dimmer, shorter)
            drawBarSegments(x, halfHeight, barWidth, level * 0.5f, barColor.copy(alpha = 0.35f), goUp = false)

        }
    }
}

private fun DrawScope.drawBarSegments(
    x: Float, centerY: Float, barWidth: Float,
    level: Float, color: Color, goUp: Boolean,
) {
    val maxHeight = centerY
    val segCount = 16
    val segHeight = maxHeight / segCount
    val segGap = segHeight * 0.2f
    val activeSegs = (level * segCount).toInt()

    for (seg in 0 until activeSegs) {
        val segY = if (goUp) {
            centerY - (seg + 1) * segHeight
        } else {
            centerY + seg * segHeight
        }
        // Intensity fades toward the tips
        val intensity = if (goUp) {
            1f - (seg.toFloat() / segCount) * 0.3f
        } else {
            1f - (seg.toFloat() / segCount) * 0.6f
        }
        drawRoundRect(
            color = color.copy(alpha = intensity),
            topLeft = Offset(x, segY + segGap / 2),
            size = Size(barWidth, segHeight - segGap),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}

/** Color gradient across bars: cyan → green → yellow → orange → magenta */
private fun barGradientColor(index: Int, total: Int): Color {
    val ratio = index.toFloat() / (total - 1).coerceAtLeast(1)
    return when {
        ratio < 0.25f -> lerpColor(Color(0xFF00E5FF), Color(0xFF00E676), ratio / 0.25f)
        ratio < 0.5f -> lerpColor(Color(0xFF00E676), Color(0xFFFFEB3B), (ratio - 0.25f) / 0.25f)
        ratio < 0.75f -> lerpColor(Color(0xFFFFEB3B), Color(0xFFFF9800), (ratio - 0.5f) / 0.25f)
        else -> lerpColor(Color(0xFFFF9800), Color(0xFFFF1744), (ratio - 0.75f) / 0.25f)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = 1f,
    )
}

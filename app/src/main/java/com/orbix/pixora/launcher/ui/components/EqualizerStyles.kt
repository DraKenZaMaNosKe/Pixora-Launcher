package com.orbix.pixora.launcher.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Available equalizer visual styles.
 */
enum class EqualizerStyle(val label: String, val emoji: String) {
    CLASSIC("Classic", "\uD83D\uDCCA"),    // 📊
    WAVE("Wave", "\uD83C\uDF0A"),          // 🌊
    CIRCULAR("Circular", "\uD83D\uDD35"),  // 🔵
    DOTS("Dots", "\u2B50"),                // ⭐
}

/**
 * Draw the equalizer based on the selected style.
 */
fun DrawScope.drawEqualizer(
    style: EqualizerStyle,
    barLevels: FloatArray,
    peakLevels: FloatArray,
    barCount: Int,
    colors: List<Color>,
    glowColor: Color,
) {
    when (style) {
        EqualizerStyle.CLASSIC -> drawClassicBars(barLevels, peakLevels, barCount, colors, glowColor)
        EqualizerStyle.WAVE -> drawWaveStyle(barLevels, barCount, colors, glowColor)
        EqualizerStyle.CIRCULAR -> drawCircularStyle(barLevels, barCount, colors, glowColor)
        EqualizerStyle.DOTS -> drawDotsStyle(barLevels, peakLevels, barCount, colors, glowColor)
    }
}

// ── Classic Bars (existing style) ──────────────────────

private fun DrawScope.drawClassicBars(
    barLevels: FloatArray, peakLevels: FloatArray,
    barCount: Int, colors: List<Color>, glowColor: Color,
) {
    val totalWidth = size.width
    val totalHeight = size.height
    val halfHeight = totalHeight / 2f
    val barWidth = totalWidth / (barCount * 1.8f)
    val gap = barWidth * 0.35f
    val totalBarsWidth = barCount * barWidth + (barCount - 1) * gap
    val startX = (totalWidth - totalBarsWidth) / 2f

    for (i in 0 until barCount) {
        val x = startX + i * (barWidth + gap)
        val level = barLevels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        val barColor = gradientColor(i, barCount, colors)

        // Glow
        val glowAlpha = (level * 0.3f).coerceIn(0f, 0.3f)
        val glowHeight = level * halfHeight
        drawRoundRect(
            color = barColor.copy(alpha = glowAlpha),
            topLeft = Offset(x - barWidth * 0.3f, halfHeight - glowHeight),
            size = Size(barWidth * 1.6f, glowHeight),
            cornerRadius = CornerRadius(4f, 4f),
        )
        drawRoundRect(
            color = barColor.copy(alpha = glowAlpha * 0.5f),
            topLeft = Offset(x - barWidth * 0.3f, halfHeight),
            size = Size(barWidth * 1.6f, glowHeight * 0.6f),
            cornerRadius = CornerRadius(4f, 4f),
        )

        // Main bars up
        drawBarSegments(x, halfHeight, barWidth, level, barColor, goUp = true)
        // Mirror bars down
        drawBarSegments(x, halfHeight, barWidth, level * 0.5f, barColor.copy(alpha = 0.35f), goUp = false)
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
        val segY = if (goUp) centerY - (seg + 1) * segHeight else centerY + seg * segHeight
        val intensity = if (goUp) 1f - (seg.toFloat() / segCount) * 0.3f else 1f - (seg.toFloat() / segCount) * 0.6f
        drawRoundRect(
            color = color.copy(alpha = intensity),
            topLeft = Offset(x, segY + segGap / 2),
            size = Size(barWidth, segHeight - segGap),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}

// ── Wave Style ──────────────────────────────────────────

private fun DrawScope.drawWaveStyle(
    barLevels: FloatArray, barCount: Int, colors: List<Color>, glowColor: Color,
) {
    val w = size.width
    val h = size.height
    val centerY = h / 2f

    // Upper wave
    val upperPath = Path()
    val lowerPath = Path()
    upperPath.moveTo(0f, centerY)
    lowerPath.moveTo(0f, centerY)

    val step = w / (barCount - 1).coerceAtLeast(1)
    for (i in 0 until barCount) {
        val x = i * step
        val level = barLevels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        val yUp = centerY - level * centerY * 0.85f
        val yDown = centerY + level * centerY * 0.4f

        if (i == 0) {
            upperPath.moveTo(x, yUp)
            lowerPath.moveTo(x, yDown)
        } else {
            val prevX = (i - 1) * step
            val cpX = (prevX + x) / 2f
            upperPath.cubicTo(cpX, upperPath.lastY(), cpX, yUp, x, yUp)
            lowerPath.cubicTo(cpX, lowerPath.lastY(), cpX, yDown, x, yDown)
        }
    }

    // Glow behind upper wave
    drawPath(
        path = upperPath,
        color = glowColor.copy(alpha = 0.15f),
        style = Stroke(width = 12f, cap = StrokeCap.Round),
    )
    // Main upper wave
    drawPath(
        path = upperPath,
        color = colors.firstOrNull() ?: glowColor,
        style = Stroke(width = 3f, cap = StrokeCap.Round),
    )
    // Lower wave (mirror, dimmer)
    drawPath(
        path = lowerPath,
        color = (colors.lastOrNull() ?: glowColor).copy(alpha = 0.3f),
        style = Stroke(width = 2f, cap = StrokeCap.Round),
    )

    // Dots at peaks
    for (i in 0 until barCount) {
        val x = i * step
        val level = barLevels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        if (level > 0.1f) {
            val y = centerY - level * centerY * 0.85f
            val color = gradientColor(i, barCount, colors)
            drawCircle(color = color.copy(alpha = 0.8f), radius = 3f, center = Offset(x, y))
            drawCircle(color = color.copy(alpha = 0.2f), radius = 8f, center = Offset(x, y))
        }
    }
}

// Helper to get last Y of a Path (approximate)
private fun Path.lastY(): Float = 0f // Compose Path doesn't expose last point; use cubic for smoothness

// ── Circular Style ──────────────────────────────────────

private fun DrawScope.drawCircularStyle(
    barLevels: FloatArray, barCount: Int, colors: List<Color>, glowColor: Color,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val baseRadius = minOf(cx, cy) * 0.35f
    val maxBarLen = minOf(cx, cy) * 0.55f

    // Background circle
    drawCircle(
        color = glowColor.copy(alpha = 0.06f),
        radius = baseRadius,
        center = Offset(cx, cy),
    )

    val angleStep = 360f / barCount
    for (i in 0 until barCount) {
        val level = barLevels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        val angle = (i * angleStep - 90f) * PI.toFloat() / 180f
        val barLen = level * maxBarLen
        val color = gradientColor(i, barCount, colors)

        val innerX = cx + cos(angle) * baseRadius
        val innerY = cy + sin(angle) * baseRadius
        val outerX = cx + cos(angle) * (baseRadius + barLen)
        val outerY = cy + sin(angle) * (baseRadius + barLen)

        // Glow
        if (level > 0.05f) {
            drawLine(
                color = color.copy(alpha = level * 0.2f),
                start = Offset(innerX, innerY),
                end = Offset(outerX, outerY),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        // Main bar
        drawLine(
            color = color.copy(alpha = 0.8f),
            start = Offset(innerX, innerY),
            end = Offset(outerX, outerY),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
        // Tip dot
        if (level > 0.15f) {
            drawCircle(
                color = Color.White.copy(alpha = level * 0.6f),
                radius = 2f,
                center = Offset(outerX, outerY),
            )
        }
    }

    // Inner glow circle
    drawCircle(
        color = glowColor.copy(alpha = 0.1f),
        radius = baseRadius * 0.6f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.5f),
    )
}

// ── Dots Style ──────────────────────────────────────────

private fun DrawScope.drawDotsStyle(
    barLevels: FloatArray, peakLevels: FloatArray,
    barCount: Int, colors: List<Color>, glowColor: Color,
) {
    val w = size.width
    val h = size.height
    val centerY = h / 2f
    val dotSpacing = w / (barCount + 1)

    for (i in 0 until barCount) {
        val x = dotSpacing * (i + 1)
        val level = barLevels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        val peak = peakLevels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
        val color = gradientColor(i, barCount, colors)

        val mainY = centerY - level * centerY * 0.75f
        val mirrorY = centerY + level * centerY * 0.35f
        val dotSize = 4f + level * 6f

        // Vertical line (faint)
        if (level > 0.05f) {
            drawLine(
                color = color.copy(alpha = 0.1f),
                start = Offset(x, centerY),
                end = Offset(x, mainY),
                strokeWidth = 1f,
            )
        }

        // Main dot with glow
        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = dotSize * 2.5f,
            center = Offset(x, mainY),
        )
        drawCircle(
            color = color.copy(alpha = 0.7f),
            radius = dotSize,
            center = Offset(x, mainY),
        )
        drawCircle(
            color = Color.White.copy(alpha = level * 0.5f),
            radius = dotSize * 0.4f,
            center = Offset(x, mainY),
        )

        // Mirror dot
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = dotSize * 0.6f,
            center = Offset(x, mirrorY),
        )

        // Peak indicator
        if (peak > level + 0.05f) {
            val peakY = centerY - peak * centerY * 0.75f
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = 2f,
                center = Offset(x, peakY),
            )
        }
    }
}

// ── Shared ──────────────────────────────────────────────

fun gradientColor(index: Int, total: Int, colors: List<Color>): Color {
    if (colors.isEmpty()) return Color.White
    if (colors.size == 1) return colors[0]
    val ratio = index.toFloat() / (total - 1).coerceAtLeast(1)
    val segment = ratio * (colors.size - 1)
    val from = segment.toInt().coerceIn(0, colors.size - 2)
    val to = (from + 1).coerceAtMost(colors.size - 1)
    val t = segment - from
    return lerpColor(colors[from], colors[to], t)
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

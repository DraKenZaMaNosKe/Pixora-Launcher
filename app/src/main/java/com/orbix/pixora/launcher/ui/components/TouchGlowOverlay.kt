package com.orbix.pixora.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class GlowDot(
    val position: Offset,
    val startTime: Long,
    val color: Color,
    val secondaryColor: Color = deriveSecondaryColor(color),
    val particles: List<ParticleData> = generateParticles(),
)

data class ParticleData(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val delay: Float,
)

private fun generateParticles(): List<ParticleData> {
    val rng = Random(System.nanoTime())
    return List(10) {
        ParticleData(
            angle = rng.nextFloat() * 360f,
            speed = 80f + rng.nextFloat() * 180f,
            size = 1.5f + rng.nextFloat() * 3f,
            delay = rng.nextFloat() * 0.1f,
        )
    }
}

private fun deriveSecondaryColor(primary: Color): Color {
    // Shift hue by rotating RGB channels for a complementary feel
    return Color(
        red = (primary.blue * 0.6f + primary.red * 0.4f).coerceIn(0f, 1f),
        green = (primary.red * 0.3f + primary.green * 0.7f).coerceIn(0f, 1f),
        blue = (primary.green * 0.5f + primary.blue * 0.5f).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/**
 * Premium touch glow renderer with multi-ring ripples, particle bursts,
 * central flash, and dual-tone bloom effect.
 */
@Composable
fun TouchGlowRenderer(
    dots: List<GlowDot>,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            now = System.currentTimeMillis()
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        for (dot in dots) {
            val elapsed = (now - dot.startTime).toFloat()
            val progress = (elapsed / 900f).coerceIn(0f, 1f)
            if (progress >= 1f) continue

            drawGlowEffect(dot, progress)
        }
    }
}

private fun DrawScope.drawGlowEffect(dot: GlowDot, progress: Float) {
    val center = dot.position
    val primary = dot.color
    val secondary = dot.secondaryColor

    // ── 1. Soft bloom (large, slow, fades early) ──
    val bloomProgress = (progress * 1.2f).coerceIn(0f, 1f)
    val bloomRadius = 30f + bloomProgress * 200f
    val bloomAlpha = (1f - bloomProgress) * 0.18f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondary.copy(alpha = bloomAlpha),
                primary.copy(alpha = bloomAlpha * 0.3f),
                Color.Transparent,
            ),
            center = center,
            radius = bloomRadius,
        ),
        center = center,
        radius = bloomRadius,
    )

    // ── 2. Central flash (bright, quick) ──
    val flashProgress = (progress * 4f).coerceIn(0f, 1f)
    val flashAlpha = (1f - flashProgress) * 0.9f
    val flashRadius = 6f + flashProgress * 20f
    if (flashAlpha > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = flashAlpha),
                    primary.copy(alpha = flashAlpha * 0.6f),
                    Color.Transparent,
                ),
                center = center,
                radius = flashRadius,
            ),
            center = center,
            radius = flashRadius,
        )
    }

    // ── 3. Expanding rings (3 concentric, staggered) ──
    for (ring in 0..2) {
        val ringDelay = ring * 0.08f
        val ringProgress = ((progress - ringDelay) / (1f - ringDelay)).coerceIn(0f, 1f)
        if (ringProgress <= 0f) continue

        val eased = 1f - (1f - ringProgress) * (1f - ringProgress) // ease-out quad
        val ringRadius = 15f + eased * (100f + ring * 50f)
        val ringAlpha = (1f - ringProgress) * (0.5f - ring * 0.12f)
        val strokeW = (3f - ring * 0.8f).coerceAtLeast(0.5f) * (1f - ringProgress * 0.5f)

        if (ringAlpha > 0.01f) {
            val ringColor = if (ring % 2 == 0) primary else secondary
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = center,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
        }
    }

    // ── 4. Inner glow ring (tight, bright) ──
    val innerProgress = (progress * 2.5f).coerceIn(0f, 1f)
    val innerRadius = 8f + innerProgress * 35f
    val innerAlpha = (1f - innerProgress) * 0.4f
    if (innerAlpha > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    primary.copy(alpha = innerAlpha),
                    Color.Transparent,
                ),
                center = center,
                radius = innerRadius,
            ),
            center = center,
            radius = innerRadius,
        )
    }

    // ── 5. Particle burst ──
    for (p in dot.particles) {
        val pProgress = ((progress - p.delay) / (0.7f - p.delay)).coerceIn(0f, 1f)
        if (pProgress <= 0f || pProgress >= 1f) continue

        val eased = 1f - (1f - pProgress) * (1f - pProgress)
        val dist = eased * p.speed
        val pAlpha = (1f - pProgress) * 0.7f
        val rad = Math.toRadians(p.angle.toDouble())
        val px = center.x + cos(rad).toFloat() * dist
        val py = center.y + sin(rad).toFloat() * dist

        val pCenter = Offset(px, py)
        val pColor = if (p.angle.toInt() % 2 == 0) primary else secondary

        // Particle with soft glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    pColor.copy(alpha = pAlpha),
                    pColor.copy(alpha = pAlpha * 0.2f),
                    Color.Transparent,
                ),
                center = pCenter,
                radius = p.size * 4f,
            ),
            center = pCenter,
            radius = p.size * 4f,
        )
        // Bright core
        drawCircle(
            color = Color.White.copy(alpha = pAlpha * 0.8f),
            radius = p.size * 0.8f,
            center = pCenter,
        )
    }
}

package com.orbix.pixora.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLE_COUNT = 35

private data class Particle(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speedX: Float,
    val speedY: Float,
    val driftFreq: Float,
    val alpha: Float,
    val color: Color,
)

@Composable
fun AmbientParticlesOverlay(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF7C4DFF),
) {
    var frameCount by remember { mutableLongStateOf(0L) }
    val particles = remember {
        val rng = Random(System.nanoTime())
        val colors = listOf(
            glowColor.copy(alpha = 0.6f),
            Color(0xFF00E5FF).copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.3f),
        )
        Array(PARTICLE_COUNT) {
            Particle(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = 1.5f + rng.nextFloat() * 3f,
                speedX = (rng.nextFloat() - 0.5f) * 0.0004f,
                speedY = -0.0002f - rng.nextFloat() * 0.0005f,
                driftFreq = 0.3f + rng.nextFloat() * 0.7f,
                alpha = 0.15f + rng.nextFloat() * 0.35f,
                color = colors[rng.nextInt(colors.size)],
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(33)
            val time = frameCount * 0.033f
            for (p in particles) {
                p.x += p.speedX + sin(time * p.driftFreq).toFloat() * 0.0002f
                p.y += p.speedY
                if (p.y < -0.02f) { p.y = 1.02f; p.x = Random.nextFloat() }
                if (p.x < -0.02f) p.x = 1.02f
                if (p.x > 1.02f) p.x = -0.02f
            }
            frameCount++
        }
    }

    @Suppress("UNUSED_EXPRESSION") frameCount

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val time = frameCount * 0.033f

        for (p in particles) {
            val breathe = 0.7f + 0.3f * sin(time * p.driftFreq + p.x * 10f).toFloat()
            val r = p.radius * breathe
            val alpha = (p.alpha * breathe).coerceIn(0f, 1f)
            val center = Offset(p.x * w, p.y * h)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        p.color.copy(alpha = alpha),
                        p.color.copy(alpha = alpha * 0.3f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = r * 4f,
                ),
                center = center,
                radius = r * 4f,
            )
            drawCircle(
                color = p.color.copy(alpha = (alpha * 1.5f).coerceAtMost(1f)),
                radius = r,
                center = center,
            )
        }
    }
}

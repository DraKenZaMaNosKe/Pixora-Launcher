package com.orbix.pixora.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

data class GlowDot(
    val position: Offset,
    val startTime: Long,
    val color: Color,
)

@Composable
fun TouchGlowOverlay(
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF7C4DFF),
) {
    val dots = remember { mutableStateListOf<GlowDot>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Animation tick
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            now = System.currentTimeMillis()
            // Remove expired dots (> 800ms)
            dots.removeAll { now - it.startTime > 800 }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    dots.add(
                        GlowDot(
                            position = offset,
                            startTime = System.currentTimeMillis(),
                            color = glowColor,
                        )
                    )
                }
            }
    ) {
        for (dot in dots) {
            val elapsed = (now - dot.startTime).toFloat()
            val progress = (elapsed / 800f).coerceIn(0f, 1f)

            // Expand and fade
            val radius = 40f + progress * 120f
            val alpha = (1f - progress) * 0.5f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dot.color.copy(alpha = alpha),
                        dot.color.copy(alpha = alpha * 0.4f),
                        Color.Transparent,
                    ),
                    center = dot.position,
                    radius = radius,
                ),
                center = dot.position,
                radius = radius,
            )
        }
    }
}

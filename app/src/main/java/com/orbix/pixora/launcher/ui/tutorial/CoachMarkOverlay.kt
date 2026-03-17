package com.orbix.pixora.launcher.ui.tutorial

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Defines where the tooltip should appear relative to the spotlight.
 */
enum class TooltipPosition { ABOVE, BELOW, LEFT, RIGHT }

/**
 * A single step in a coach mark tutorial.
 * @param spotlightX center X as fraction of screen width (0..1)
 * @param spotlightY center Y as fraction of screen height (0..1)
 * @param spotlightRadius radius of the spotlight circle in dp
 * @param title short bold title
 * @param description explanation text
 * @param tooltipPosition where to show the tooltip relative to spotlight
 */
data class TutorialStep(
    val spotlightX: Float,
    val spotlightY: Float,
    val spotlightRadius: Dp = 50.dp,
    val title: String,
    val description: String,
    val tooltipPosition: TooltipPosition = TooltipPosition.BELOW,
)

/**
 * Full-screen coach mark overlay with spotlight cutout and tooltip bubble.
 * Blocks all touches except "Next" / "Got it" button.
 */
@Composable
fun CoachMarkOverlay(
    steps: List<TutorialStep>,
    onComplete: () -> Unit,
    accentColor: Color = Color(0xFF7C4DFF),
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps.getOrNull(currentStep) ?: run { onComplete(); return }
    val isLastStep = currentStep == steps.size - 1

    // Pulsing spotlight animation
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val density = LocalDensity.current

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(300)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* block touches */ }
        ) {
            // Dark overlay with spotlight cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spotlightRadiusPx = with(density) { step.spotlightRadius.toPx() } * pulseScale
                val centerX = size.width * step.spotlightX
                val centerY = size.height * step.spotlightY

                // Draw dark overlay
                drawRect(Color.Black.copy(alpha = 0.75f))

                // Cut out the spotlight with BlendMode.Clear
                drawCircle(
                    color = Color.Transparent,
                    radius = spotlightRadiusPx + 8f,
                    center = Offset(centerX, centerY),
                    blendMode = BlendMode.Clear,
                )

                // Spotlight ring glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.3f),
                            accentColor.copy(alpha = 0.1f),
                            Color.Transparent,
                        ),
                        center = Offset(centerX, centerY),
                        radius = spotlightRadiusPx + 30f,
                    ),
                    center = Offset(centerX, centerY),
                    radius = spotlightRadiusPx + 30f,
                )

                // Spotlight border ring
                drawCircle(
                    color = accentColor.copy(alpha = 0.6f),
                    radius = spotlightRadiusPx,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }

            // Tooltip bubble
            val tooltipAlignment = when (step.tooltipPosition) {
                TooltipPosition.ABOVE -> Alignment.TopCenter
                TooltipPosition.BELOW -> Alignment.BottomCenter
                TooltipPosition.LEFT -> Alignment.CenterStart
                TooltipPosition.RIGHT -> Alignment.CenterEnd
            }

            // Calculate tooltip position based on spotlight
            val tooltipTopPadding = when (step.tooltipPosition) {
                TooltipPosition.BELOW -> {
                    val spotY = step.spotlightY
                    with(density) { ((spotY * 1920f) + step.spotlightRadius.toPx() + 40f).toDp() }
                        .coerceAtMost(600.dp)
                }
                TooltipPosition.ABOVE -> 0.dp
                else -> 0.dp
            }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Position tooltip based on spotlight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .let { mod ->
                            when (step.tooltipPosition) {
                                TooltipPosition.BELOW -> mod.padding(
                                    top = (step.spotlightY * 800).dp.coerceIn(200.dp, 550.dp)
                                )
                                TooltipPosition.ABOVE -> mod.padding(
                                    top = ((step.spotlightY * 800) - 200).dp.coerceIn(40.dp, 400.dp)
                                )
                                else -> mod.padding(top = (step.spotlightY * 800).dp.coerceIn(100.dp, 500.dp))
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Tooltip card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF1A1A2E),
                                        Color(0xFF141420),
                                    )
                                )
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = step.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = step.description,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Progress dots + button row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Progress dots
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(steps.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (index == currentStep) 8.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (index <= currentStep) accentColor
                                                else Color.White.copy(alpha = 0.2f)
                                            ),
                                    )
                                }
                            }

                            // Skip / Next button
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!isLastStep) {
                                    Text(
                                        text = "Skip",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.clickable { onComplete() },
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(accentColor, Color(0xFF651FFF))
                                            )
                                        )
                                        .clickable {
                                            if (isLastStep) onComplete()
                                            else currentStep++
                                        }
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = if (isLastStep) "Got it!" else "Next",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

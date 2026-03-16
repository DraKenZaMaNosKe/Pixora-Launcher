package com.orbix.pixora.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    homePage: Int = 0,
    modifier: Modifier = Modifier,
) {
    val homeGlow = Color(0xFF7C4DFF)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isHome = index == homePage
            val isCurrent = index == currentPage

            if (isHome) {
                // Home page: house icon with glow
                val tint by animateColorAsState(
                    targetValue = if (isCurrent) Color.White else homeGlow.copy(alpha = 0.7f),
                    label = "homeTint",
                )
                val glowAlpha = if (isCurrent) 0.4f else 0.15f
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .drawBehind {
                            drawCircle(
                                color = homeGlow.copy(alpha = glowAlpha),
                                radius = size.maxDimension * 0.7f,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home page",
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                // Regular dot
                val dotSize by animateDpAsState(
                    targetValue = if (isCurrent) 10.dp else 6.dp,
                    animationSpec = spring(),
                    label = "dotSize",
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                    label = "dotColor",
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}

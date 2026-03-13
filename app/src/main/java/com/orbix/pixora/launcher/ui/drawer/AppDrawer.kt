package com.orbix.pixora.launcher.ui.drawer

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbix.pixora.launcher.data.models.AppInfo

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    onAppClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = if (searchQuery.isBlank()) apps
    else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }

    // Staggered entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val panelOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "panel"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.3f),
                        Color(0xFF0A0A0F).copy(alpha = 0.95f),
                    )
                )
            )
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = panelOffset * 800f
                }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF141420))
                .clickable(enabled = false) {}
                .padding(top = 12.dp),
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // App grid with staggered fan animation
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(filteredApps, key = { _, app -> app.packageName }) { index, app ->
                    FanAppGridItem(
                        app = app,
                        index = index,
                        isVisible = visible,
                        onClick = { onAppClick(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FanAppGridItem(
    app: AppInfo,
    index: Int,
    isVisible: Boolean,
    onClick: () -> Unit,
) {
    val delay = (index * 25).coerceAtMost(600) // stagger up to 600ms
    val animProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = delay,
            easing = FastOutSlowInEasing,
        ),
        label = "fan_$index"
    )

    val bitmap = remember(app.packageName) {
        BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
    }

    // Fan effect: each item rotates in from a different angle based on column
    val col = index % 4
    val startRotation = when (col) {
        0 -> -35f
        1 -> -15f
        2 -> 15f
        3 -> 35f
        else -> 0f
    }

    Column(
        modifier = Modifier
            .graphicsLayer {
                val progress = animProgress
                scaleX = 0.3f + (0.7f * progress)
                scaleY = 0.3f + (0.7f * progress)
                rotationZ = startRotation * (1f - progress)
                alpha = progress
                translationY = (1f - progress) * 120f
            }
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(52.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package com.orbix.pixora.launcher.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.orbix.pixora.launcher.data.models.AppInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableAppGrid(
    slots: List<String?>,
    appsMap: Map<String, AppInfo>,
    isEditMode: Boolean,
    onAppClick: (String) -> Unit,
    onLongPress: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddToDock: (String) -> Unit,
    onDragToNextPage: (fromIndex: Int) -> Unit,
    onDragToPrevPage: (fromIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 4
    val rows = 4
    val pageSize = columns * rows
    val displaySlots = slots.take(pageSize)

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val cellPositions = remember { mutableStateMapOf<Int, Offset>() }
    val cellSizes = remember { mutableStateMapOf<Int, androidx.compose.ui.unit.IntSize>() }

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    var gridLeft by remember { mutableFloatStateOf(0f) }
    var gridRight by remember { mutableFloatStateOf(0f) }

    var contextMenuIndex by remember { mutableIntStateOf(-1) }

    // Track how close the dragged icon is to each edge (0 = not near, 1 = at edge)
    var nearRightEdge by remember { mutableFloatStateOf(0f) }
    var nearLeftEdge by remember { mutableFloatStateOf(0f) }

    // Compute edge proximity during drag
    val edgeFraction = 0.20f // 20% of grid width = edge zone
    LaunchedEffect(draggedIndex, dragOffset) {
        if (draggedIndex < 0) {
            nearRightEdge = 0f
            nearLeftEdge = 0f
            return@LaunchedEffect
        }
        val cellPos = cellPositions[draggedIndex]
        val cellSize = cellSizes[draggedIndex]
        if (cellPos != null && cellSize != null) {
            val centerX = cellPos.x + cellSize.width / 2f + dragOffset.x
            val gridWidth = gridRight - gridLeft
            val edgeZone = gridWidth * edgeFraction

            // Right edge proximity: 0..1
            val distFromRight = gridRight - centerX
            nearRightEdge = if (distFromRight < edgeZone) {
                ((edgeZone - distFromRight) / edgeZone).coerceIn(0f, 1f)
            } else 0f

            // Left edge proximity: 0..1
            val distFromLeft = centerX - gridLeft
            nearLeftEdge = if (distFromLeft < edgeZone) {
                ((edgeZone - distFromLeft) / edgeZone).coerceIn(0f, 1f)
            } else 0f
        }
    }

    val wobbleAngle by animateFloatAsState(
        targetValue = if (isEditMode) 1.5f else 0f,
        animationSpec = spring(dampingRatio = 0.3f),
        label = "wobble"
    )

    // Animated glow alpha for edge indicators
    val glowPulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_pulse"
    )

    // Animated arrow offset for "sliding" effect
    val arrowSlide by rememberInfiniteTransition(label = "arrow").animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrow_slide"
    )

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            val rootPos = coords.positionInRoot()
            gridLeft = rootPos.x
            gridRight = rootPos.x + coords.size.width
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        val pkg = displaySlots.getOrNull(index)
                        val app = pkg?.let { appsMap[it] }
                        val isDragged = index == draggedIndex

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    cellPositions[index] = coords.positionInRoot()
                                    cellSizes[index] = coords.size
                                }
                                .zIndex(if (isDragged) 10f else 0f)
                                .graphicsLayer {
                                    if (isDragged) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.15f
                                        scaleY = 1.15f
                                        shadowElevation = 16f
                                        // Tint towards purple when near edge
                                        val edgeProximity = maxOf(nearRightEdge, nearLeftEdge)
                                        alpha = 1f - (edgeProximity * 0.3f)
                                    } else if (isEditMode && app != null) {
                                        val wobbleDir = if (index % 2 == 0) wobbleAngle else -wobbleAngle
                                        rotationZ = wobbleDir
                                    }
                                }
                                .then(
                                    if (isEditMode && app != null) {
                                        Modifier.pointerInput(index) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggedIndex = index
                                                    dragOffset = Offset.Zero
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount
                                                },
                                                onDragEnd = {
                                                    val cellPos = cellPositions[draggedIndex]
                                                    val cellSize = cellSizes[draggedIndex]
                                                    if (cellPos != null && cellSize != null) {
                                                        val centerX = cellPos.x + cellSize.width / 2f + dragOffset.x
                                                        val centerY = cellPos.y + cellSize.height / 2f + dragOffset.y
                                                        val gridWidth = gridRight - gridLeft
                                                        val edgeZone = gridWidth * edgeFraction

                                                        when {
                                                            centerX > gridRight - edgeZone -> {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                onDragToNextPage(draggedIndex)
                                                            }
                                                            centerX < gridLeft + edgeZone -> {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                onDragToPrevPage(draggedIndex)
                                                            }
                                                            else -> {
                                                                val target = findTargetCell(
                                                                    Offset(centerX, centerY),
                                                                    cellPositions,
                                                                    cellSizes,
                                                                    pageSize,
                                                                )
                                                                if (target != -1 && target != draggedIndex) {
                                                                    onMove(draggedIndex, target)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    draggedIndex = -1
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    draggedIndex = -1
                                                    dragOffset = Offset.Zero
                                                },
                                            )
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (app != null) {
                                AppSlotIcon(
                                    app = app,
                                    isDragged = isDragged,
                                    onClick = {
                                        if (isEditMode) {
                                            contextMenuIndex = index
                                        } else {
                                            onAppClick(app.packageName)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isEditMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onLongPress()
                                        }
                                    },
                                )

                                DropdownMenu(
                                    expanded = contextMenuIndex == index,
                                    onDismissRequest = { contextMenuIndex = -1 },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Dock") },
                                        leadingIcon = { Icon(Icons.Default.Apps, contentDescription = null) },
                                        onClick = { onAddToDock(app.packageName); contextMenuIndex = -1 },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("App Info") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            contextMenuIndex = -1
                                            try {
                                                context.startActivity(
                                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.parse("package:${app.packageName}")
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            } catch (_: Exception) {}
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Right edge glow indicator ──
        AnimatedVisibility(
            visible = nearRightEdge > 0.05f,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF7C4DFF).copy(alpha = nearRightEdge * glowPulse * 0.6f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = nearRightEdge * glowPulse),
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { translationX = arrowSlide * nearRightEdge },
                )
            }
        }

        // ── Left edge glow indicator ──
        AnimatedVisibility(
            visible = nearLeftEdge > 0.05f,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF).copy(alpha = nearLeftEdge * glowPulse * 0.6f),
                                Color.Transparent,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = nearLeftEdge * glowPulse),
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { translationX = -arrowSlide * nearLeftEdge },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppSlotIcon(
    app: AppInfo,
    isDragged: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = remember(app.iconBytes) {
        BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
    }

    val scale by animateFloatAsState(
        targetValue = if (isDragged) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "icon_scale"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .scale(scale),
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = app.label,
            fontSize = 10.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 64.dp),
        )
    }
}

/** Hit-test: find which cell contains the given point (all in ROOT coordinates) */
private fun findTargetCell(
    point: Offset,
    positions: Map<Int, Offset>,
    sizes: Map<Int, androidx.compose.ui.unit.IntSize>,
    maxCells: Int,
): Int {
    for (i in 0 until maxCells) {
        val pos = positions[i] ?: continue
        val size = sizes[i] ?: continue
        if (point.x >= pos.x && point.x <= pos.x + size.width &&
            point.y >= pos.y && point.y <= pos.y + size.height
        ) {
            return i
        }
    }
    return -1
}

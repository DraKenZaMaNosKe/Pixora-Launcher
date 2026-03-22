package com.orbix.pixora.launcher.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.AlarmClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.orbix.pixora.launcher.LauncherActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.orbix.pixora.launcher.audio.SoundEngine
import com.orbix.pixora.launcher.ui.tutorial.CoachMarkOverlay
import com.orbix.pixora.launcher.ui.tutorial.TutorialManager
import com.orbix.pixora.launcher.ui.tutorial.TutorialSteps
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.service.DayCycleManager
import com.orbix.pixora.launcher.service.IconRoomRepository
import com.orbix.pixora.launcher.service.StoryManager
import com.orbix.pixora.launcher.ui.theme.ThemeManager
import com.orbix.pixora.launcher.ui.components.*
import com.orbix.pixora.launcher.ui.drawer.AppDrawer
import com.orbix.pixora.launcher.ui.home.HomeViewModel.Companion.APPS_PER_PAGE
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenCatalog: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val apps by viewModel.installedApps.collectAsState()
    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val dockPackages by viewModel.dockApps.collectAsState()
    val showTouchGlow by viewModel.showTouchGlow.collectAsState()
    val showEqualizer by viewModel.showEqualizer.collectAsState()
    val showBatteryRing by viewModel.showBatteryRing.collectAsState()
    val showSystemRings by viewModel.showSystemRings.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val gridSlots by viewModel.gridSlots.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val showAmbientParticles by viewModel.showAmbientParticles.collectAsState()
    val eqStyleName by viewModel.equalizerStyle.collectAsState()
    val eqStyle = try { EqualizerStyle.valueOf(eqStyleName) } catch (_: Exception) { EqualizerStyle.CLASSIC }
    val recentApps by viewModel.recentApps.collectAsState()
    val isReloading by viewModel.isReloading.collectAsState()

    // Story state
    val activeStory by StoryManager.activeStory.collectAsState()
    val storyCaption by StoryManager.currentCaption.collectAsState()
    val storyFramePath by StoryManager.currentFramePath.collectAsState()

    // When story frame changes, update background to panoramic
    LaunchedEffect(storyFramePath) {
        if (activeStory != null && storyFramePath != null) {
            viewModel.setBackgroundFile("pano:$storyFramePath")
        }
    }

    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    val dockApps = remember(dockPackages, apps) {
        dockPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    }

    val uiTheme by ThemeManager.current.collectAsState()
    val glowColor = uiTheme.glowColor

    val appsMap = remember(apps) { apps.associateBy { it.packageName } }

    // Pages from grid slots (16 per page, nullable = empty cell)
    val appPages = remember(gridSlots) {
        if (gridSlots.isEmpty()) emptyList()
        else gridSlots.chunked(APPS_PER_PAGE)
    }
    // All pages are app pages, plus 1 extra empty page at the end
    val extraEmptyPages = 1
    val pageCount = appPages.size + extraEmptyPages
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // Scroll to home page on first load
    LaunchedEffect(homePage, appPages.size) {
        if (homePage in 0 until pageCount && pagerState.currentPage == 0 && homePage > 0) {
            pagerState.scrollToPage(homePage)
        }
    }

    // System home button pressed — scroll to home page, close drawer/edit mode
    val homeButtonEvent by LauncherActivity.homeButtonPressed.collectAsState()
    LaunchedEffect(homeButtonEvent) {
        if (homeButtonEvent > 0 && homePage in 0 until pageCount) {
            viewModel.triggerGoHome()
            pagerState.animateScrollToPage(homePage)
        }
    }

    // Page swipe sound
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0 || appPages.isNotEmpty()) {
            SoundEngine.playPageSwipe()
        }
    }

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // Clock with seconds + milliseconds progress
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var secondsFraction by remember { mutableFloatStateOf(0f) }
    var millisFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            currentDate = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now.time)
            val sec = now.get(Calendar.SECOND)
            val millis = now.get(Calendar.MILLISECOND)
            secondsFraction = (sec + millis / 1000f) / 60f
            millisFraction = millis / 1000f
            kotlinx.coroutines.delay(32) // ~30 FPS for smooth ms bar
        }
    }

    // Resolve background — icon rooms use IconRoomRepository for cached/Supabase loading
    val bgContext = LocalContext.current
    val bgImageData: Any = remember(backgroundUri, bgContext) {
        when {
            backgroundUri.startsWith("asset:") -> {
                val assetName = backgroundUri.removePrefix("asset:")
                val room = IconRoom.ALL.find { it.assetName == assetName }
                if (room != null) {
                    IconRoomRepository.getRoomImageSource(bgContext, room)
                } else {
                    "file:///android_asset/icon_rooms/icon_room_01.webp"
                }
            }
            backgroundUri.startsWith("pano:") -> java.io.File(backgroundUri.removePrefix("pano:"))
            backgroundUri.startsWith("file:") -> java.io.File(backgroundUri.removePrefix("file:"))
            else -> "file:///android_asset/icon_rooms/icon_room_01.webp"
        }
    }
    val isPanoramic = backgroundUri.startsWith("asset:") || backgroundUri.startsWith("pano:")

    // Day cycle: check every minute and update wallpaper if period changed
    val dayCycleTheme by DayCycleManager.activeTheme.collectAsState()
    val dayCycleImagePath by DayCycleManager.currentImagePath.collectAsState()
    LaunchedEffect(dayCycleTheme) {
        if (dayCycleTheme != null) {
            while (true) {
                DayCycleManager.checkAndUpdate(bgContext)
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    LaunchedEffect(dayCycleImagePath) {
        if (dayCycleTheme != null && dayCycleImagePath != null) {
            viewModel.setBackgroundFile("pano:$dayCycleImagePath")
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            if (pagerState.pageCount > 1) {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction) /
                    (pagerState.pageCount - 1).toFloat()
            } else 0f
        }
    }

    // Glow dots state — managed here so touch observation doesn't block the pager
    val glowDots = remember { mutableStateListOf<GlowDot>() }

    // Cleanup expired dots
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(100)
            val now = System.currentTimeMillis()
            glowDots.removeAll { now - it.startTime > 900 }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) { SoundEngine.playDrawerOpen(); viewModel.openDrawer() }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { SoundEngine.playEditModeEnter(); viewModel.enterEditMode() }
                )
            }
            // Observe ALL touches at Initial pass for glow effect — does NOT consume
            .pointerInput(showTouchGlow) {
                if (!showTouchGlow) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { change ->
                            if (change.pressed && !change.previousPressed) {
                                glowDots.add(
                                    GlowDot(
                                        position = change.position,
                                        startTime = System.currentTimeMillis(),
                                        color = glowColor,
                                    )
                                )
                            }
                        }
                    }
                }
            }
    ) {
        val context = LocalContext.current

        val bgPainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(bgImageData)
                .crossfade(true)
                .size(CoilSize.ORIGINAL)
                .build()
        )

        if (isPanoramic) {
            PanoramicBackground(painter = bgPainter, scrollFraction = scrollFraction, modifier = Modifier.fillMaxSize())
        } else {
            Image(painter = bgPainter, contentDescription = "Background", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }

        if (showAmbientParticles) { AmbientParticlesOverlay(glowColor = glowColor) }

        // Home screen pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isEditMode,
        ) { page ->
            if (page in appPages.indices) {
                val pageSlots = appPages[page]
                val pageOffset = page * APPS_PER_PAGE
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 140.dp, bottom = 160.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    DraggableAppGrid(
                        slots = pageSlots,
                        appsMap = appsMap,
                        isEditMode = isEditMode,
                        onAppClick = { pkg ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            SoundEngine.playTap()
                            viewModel.launchApp(pkg)
                        },
                        onLongPress = { SoundEngine.playLongPress(); viewModel.enterEditMode() },
                        onMove = { from, to ->
                            viewModel.moveApp(pageOffset + from, pageOffset + to)
                        },
                        onAddToDock = { pkg -> viewModel.addDockApp(pkg) },
                        onRemoveFromHome = { pkg -> viewModel.removeAppFromHome(pkg) },
                        onDragToNextPage = { fromLocal ->
                            val targetPage = viewModel.moveAppToNextPage(pageOffset + fromLocal)
                            if (targetPage >= 0) {
                                scope.launch {
                                    snapshotFlow { pagerState.pageCount }
                                        .first { it > targetPage }
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        onDragToPrevPage = { fromLocal ->
                            val wasPagerPage = pagerState.currentPage
                            val targetPage = viewModel.moveAppToPrevPage(pageOffset + fromLocal)
                            if (targetPage == 0 && wasPagerPage == 0) {
                                // Prepended new page: snap to shifted old page, then animate left
                                scope.launch {
                                    snapshotFlow { pagerState.pageCount }
                                        .first { it > 1 }
                                    pagerState.scrollToPage(1)
                                    pagerState.animateScrollToPage(0)
                                }
                            } else if (targetPage >= 0) {
                                scope.launch {
                                    snapshotFlow { pagerState.pageCount }
                                        .first { it > targetPage }
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Touch glow renderer ON TOP of pager — pure renderer, no touch handling
        if (showTouchGlow && glowDots.isNotEmpty()) {
            TouchGlowRenderer(dots = glowDots)
        }

        // Edit mode toolbar
        var showClearDialog by remember { mutableStateOf(false) }
        if (isEditMode) {
            // Glassmorphism edit toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top row: action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF0A0A0F).copy(alpha = 0.85f),
                                    Color(0xFF141420).copy(alpha = 0.85f),
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    uiTheme.primary.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0.08f),
                                )
                            ),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Clear
                    EditToolbarButton(
                        icon = Icons.Default.DeleteSweep,
                        label = "Clear",
                        tintColor = Color(0xFFFF5252),
                        onClick = { showClearDialog = true },
                    )

                    // Auto-sort
                    EditToolbarButton(
                        icon = Icons.Default.Refresh,
                        label = "Sort",
                        tintColor = uiTheme.secondary,
                        onClick = { viewModel.resetAppOrder() },
                    )

                    // Set Home
                    val isCurrentHome = pagerState.currentPage == homePage
                    EditToolbarButton(
                        icon = Icons.Default.Home,
                        label = if (isCurrentHome) "Home" else "Set Home",
                        tintColor = if (isCurrentHome) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.6f),
                        onClick = { viewModel.setHomePage(pagerState.currentPage) },
                    )

                    // Done button — prominent
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(uiTheme.primary, uiTheme.secondary)
                                )
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                SoundEngine.playEditModeExit()
                                viewModel.exitEditMode()
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Done",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }

        // Clear all confirmation dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear All Pages") },
                text = { Text("Remove all icons from the home screen? You can add them back from the app drawer.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllPages()
                            showClearDialog = false
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                    ) { Text("Clear All") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
                },
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.7f),
            )
        }

        // Hide overlays & buttons in edit mode for a clean editing experience
        if (!isEditMode) {
            // Clock overlay — double-tap opens alarm/clock app
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .statusBarsPadding()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                try {
                                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            }
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = currentTime, fontSize = 56.sp, fontWeight = FontWeight.Light, color = Color.White)
                Text(text = currentDate, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))

                // Dual HUD: seconds + milliseconds progress
                Spacer(modifier = Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .width(180.dp)
                        .height(28.dp)
                ) {
                    val w = size.width
                    val labelSpace = 28f // space for label text
                    val barStart = labelSpace
                    val barWidth = w - labelSpace - 4f
                    val barH = 4f
                    val cr = androidx.compose.ui.geometry.CornerRadius(barH / 2, barH / 2)

                    // ── SEC bar (top) ──
                    val secY = 4f
                    // Track
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f),
                        topLeft = Offset(barStart, secY),
                        size = Size(barWidth, barH),
                        cornerRadius = cr,
                    )
                    // Fill
                    val secFill = barWidth * secondsFraction
                    if (secFill > 0f) {
                        // Glow behind
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    uiTheme.primary.copy(alpha = 0.0f),
                                    uiTheme.primary.copy(alpha = 0.25f),
                                ),
                                startX = barStart,
                                endX = barStart + secFill,
                            ),
                            topLeft = Offset(barStart, secY - 3f),
                            size = Size(secFill, barH + 6f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f),
                        )
                        // Main bar
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(uiTheme.secondary, uiTheme.primary),
                                startX = barStart,
                                endX = barStart + secFill,
                            ),
                            topLeft = Offset(barStart, secY),
                            size = Size(secFill, barH),
                            cornerRadius = cr,
                        )
                        // Endpoint dot
                        drawCircle(
                            color = Color.White,
                            radius = 3.5f,
                            center = Offset(barStart + secFill, secY + barH / 2),
                        )
                        drawCircle(
                            color = uiTheme.primary.copy(alpha = 0.4f),
                            radius = 7f,
                            center = Offset(barStart + secFill, secY + barH / 2),
                        )
                    }

                    // ── MS bar (bottom) ──
                    val msY = secY + barH + 8f
                    // Track
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f),
                        topLeft = Offset(barStart, msY),
                        size = Size(barWidth, barH),
                        cornerRadius = cr,
                    )
                    // Fill
                    val msFill = barWidth * millisFraction
                    if (msFill > 0f) {
                        // Glow behind
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E676).copy(alpha = 0.0f),
                                    Color(0xFF00E676).copy(alpha = 0.2f),
                                ),
                                startX = barStart,
                                endX = barStart + msFill,
                            ),
                            topLeft = Offset(barStart, msY - 3f),
                            size = Size(msFill, barH + 6f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f),
                        )
                        // Main bar
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00E676), uiTheme.secondary),
                                startX = barStart,
                                endX = barStart + msFill,
                            ),
                            topLeft = Offset(barStart, msY),
                            size = Size(msFill, barH),
                            cornerRadius = cr,
                        )
                        // Endpoint dot
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = Offset(barStart + msFill, msY + barH / 2),
                        )
                        drawCircle(
                            color = uiTheme.secondary.copy(alpha = 0.35f),
                            radius = 6f,
                            center = Offset(barStart + msFill, msY + barH / 2),
                        )
                    }

                    // ── Labels ──
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(100, 255, 255, 255)
                        textSize = 18f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText("SEC", 0f, secY + barH + 1f, labelPaint)
                        labelPaint.textSize = 16f
                        canvas.nativeCanvas.drawText("MS", 2f, msY + barH + 1f, labelPaint)
                    }
                }

                // Story caption — timed: 8s visible every 3 minutes
                if (activeStory != null && storyCaption.isNotBlank()) {
                    var captionVisible by remember { mutableStateOf(true) }
                    LaunchedEffect(storyCaption) {
                        // Show caption on each new caption / frame change
                        captionVisible = true
                        kotlinx.coroutines.delay(8000)
                        captionVisible = false
                    }
                    // Also show periodically (every 3 minutes)
                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(180_000) // 3 min
                            if (storyCaption.isNotBlank()) {
                                captionVisible = true
                                kotlinx.coroutines.delay(8000)
                                captionVisible = false
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    AnimatedVisibility(
                        visible = captionVisible,
                        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(800)),
                        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(1200)),
                    ) {
                        val storyGlow = activeStory?.let { story ->
                            try { Color(android.graphics.Color.parseColor(story.glowColor)) }
                            catch (_: Exception) { uiTheme.primary }
                        } ?: uiTheme.primary

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color(0xFF0A0A0F).copy(alpha = 0.7f),
                                        )
                                    )
                                )
                                .border(
                                    1.dp,
                                    storyGlow.copy(alpha = 0.2f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            // Rich text — highlight capitalized proper nouns
                            Text(
                                text = buildStoryAnnotatedString(storyCaption, storyGlow),
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (showBatteryRing) {
                BatteryRingOverlay(glowColor = glowColor, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 48.dp, end = 12.dp))
            }
            if (showSystemRings) {
                SystemRingsOverlay(glowColor = glowColor, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 155.dp, end = 8.dp))
            }
            if (showEqualizer) {
                EqualizerOverlay(glowColor = glowColor, eqColors = uiTheme.eqGradient, style = eqStyle, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp).navigationBarsPadding().padding(horizontal = 24.dp))
            }

            // Catalog + Settings buttons
            Column(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 52.dp, start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { onOpenCatalog() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Palette, contentDescription = "Explore wallpapers", tint = Color.White, modifier = Modifier.size(24.dp)) }
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp)) }
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(uiTheme.primary.copy(alpha = 0.5f)).clickable { SoundEngine.playDrawerOpen(); viewModel.openDrawer() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Apps, contentDescription = "All apps", tint = Color.White, modifier = Modifier.size(24.dp)) }
            }
        }

        // Dock bar
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                // Remove page button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.5f))
                        .clickable {
                            val appPageIndex = pagerState.currentPage
                            val removed = viewModel.removePage(appPageIndex)
                            if (removed) {
                                val newPage = (appPageIndex - 1).coerceAtLeast(0)
                                val prevCount = pagerState.pageCount
                                scope.launch {
                                    // Wait for pagerState to reflect the reduced page count
                                    snapshotFlow { pagerState.pageCount }
                                        .first { it < prevCount }
                                    pagerState.animateScrollToPage(newPage)
                                }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Move icons first to delete this page") }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Remove page", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))

                PageIndicator(pageCount = pageCount, currentPage = pagerState.currentPage, homePage = homePage)

                Spacer(modifier = Modifier.width(10.dp))
                // Add page button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(uiTheme.primary.copy(alpha = 0.6f))
                        .clickable {
                            val newPage = viewModel.addPageAfter(pagerState.currentPage)
                            scope.launch {
                                // Wait for pagerState to reflect the new page count
                                snapshotFlow { pagerState.pageCount }
                                    .first { it > newPage }
                                pagerState.animateScrollToPage(newPage)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add page", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            if (dockApps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dockApps.forEach { app ->
                        DockAppIcon(app = app, onClick = { viewModel.launchApp(app.packageName) }, onLongClick = { viewModel.removeDockApp(app.packageName) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Swipe up for apps", fontSize = 11.sp, color = Color.White.copy(alpha = 0.25f))
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 100.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1A1A2E),
                contentColor = Color.White,
            )
        }

        // ── Reload overlay ──
        // Shows a smooth loading animation while layouts reload from disk
        ReloadOverlay(
            isReloading = isReloading,
            bgImageData = bgImageData,
            primaryColor = uiTheme.primary,
            secondaryColor = uiTheme.secondary,
        )

        // ── Tutorials ──
        // Home tour (first time only)
        var showHomeTour by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!TutorialManager.isHomeTourDone(context)) {
                kotlinx.coroutines.delay(1500) // Let the home screen render first
                showHomeTour = true
            }
        }
        if (showHomeTour && !isEditMode && !isDrawerOpen) {
            CoachMarkOverlay(
                steps = TutorialSteps.homeTour,
                onComplete = {
                    showHomeTour = false
                    scope.launch { TutorialManager.markHomeTourDone(context) }
                },
            )
        }

        // Edit mode tour (first time entering edit mode)
        var showEditTour by remember { mutableStateOf(false) }
        LaunchedEffect(isEditMode) {
            if (isEditMode && !TutorialManager.isEditTourDone(context)) {
                kotlinx.coroutines.delay(500)
                showEditTour = true
            }
        }
        if (showEditTour && isEditMode) {
            CoachMarkOverlay(
                steps = TutorialSteps.editModeTour,
                onComplete = {
                    showEditTour = false
                    scope.launch { TutorialManager.markEditTourDone(context) }
                },
            )
        }

        // App drawer overlay
        if (isDrawerOpen) {
            val homeAppSet = remember(gridSlots) { gridSlots.filterNotNull().toSet() }
            AppDrawer(
                apps = apps,
                onAppClick = { packageName -> viewModel.closeDrawer(); viewModel.launchApp(packageName) },
                onDismiss = { viewModel.closeDrawer() },
                homeApps = homeAppSet,
                onAddToHome = { pkg -> viewModel.addAppToHome(pkg) },
                onRemoveFromHome = { pkg -> viewModel.removeAppFromHome(pkg) },
                recentApps = recentApps,
            )
        }
    }
}

@Composable
private fun PanoramicBackground(painter: Painter, scrollFraction: Float, modifier: Modifier = Modifier) {
    val intrinsicSize = painter.intrinsicSize
    Box(
        modifier = modifier.drawBehind {
            if (intrinsicSize == Size.Unspecified || intrinsicSize.width <= 0 || intrinsicSize.height <= 0) return@drawBehind
            val imageAspect = intrinsicSize.width / intrinsicSize.height
            val renderWidth = size.height * imageAspect
            val maxScroll = (renderWidth - size.width).coerceAtLeast(0f)
            translate(left = -scrollFraction * maxScroll, top = 0f) {
                with(painter) { draw(size = Size(renderWidth, size.height)) }
            }
        }
    )
}

@Composable
private fun EditToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tintColor: Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tintColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockAppIcon(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val bitmap = remember(app.iconBytes) { BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size) }
    Column(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(48.dp).clip(CircleShape))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = app.label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Build rich AnnotatedString for story captions.
 * Highlights proper nouns (capitalized words 2+ chars), quoted text, and ALL CAPS words.
 */
private fun buildStoryAnnotatedString(text: String, accentColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val words = text.split(" ")
        val secondaryColor = Color(
            red = (accentColor.blue * 0.5f + accentColor.red * 0.5f).coerceIn(0f, 1f),
            green = (accentColor.green * 0.8f + 0.2f).coerceIn(0f, 1f),
            blue = (accentColor.red * 0.3f + accentColor.blue * 0.7f).coerceIn(0f, 1f),
        )

        for ((i, word) in words.withIndex()) {
            if (i > 0) append(" ")

            val cleanWord = word.trimEnd('.', ',', '!', '?', ';', ':', '"', '\'')

            when {
                // ALL CAPS words (like "GOKU", "SPIRIT BOMB") — bold + accent
                cleanWord.length >= 2 && cleanWord.all { it.isUpperCase() || !it.isLetter() } && cleanWord.any { it.isLetter() } -> {
                    withStyle(SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                    )) { append(word) }
                }
                // Proper nouns: capitalized, 2+ letters, not start of sentence indicator
                cleanWord.length >= 2 && cleanWord[0].isUpperCase() && cleanWord.drop(1).any { it.isLowerCase() } -> {
                    withStyle(SpanStyle(
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold,
                    )) { append(word) }
                }
                // Quoted text
                word.startsWith('"') || word.startsWith('\'') || word.startsWith('\u201C') -> {
                    withStyle(SpanStyle(
                        color = accentColor.copy(alpha = 0.9f),
                        fontStyle = FontStyle.Italic,
                    )) { append(word) }
                }
                // Normal text
                else -> {
                    withStyle(SpanStyle(
                        color = Color.White.copy(alpha = 0.85f),
                    )) { append(word) }
                }
            }
        }
    }
}

/**
 * Full-screen reload overlay with progressive background fade-in and spinning dots.
 * Appears while the ViewModel reloads layouts from disk on resume.
 */
@Composable
private fun ReloadOverlay(
    isReloading: Boolean,
    bgImageData: Any,
    primaryColor: Color,
    secondaryColor: Color,
) {
    // Animate overlay alpha: fade in when reloading, fade out when done
    val overlayAlpha by animateFloatAsState(
        targetValue = if (isReloading) 1f else 0f,
        animationSpec = tween(durationMillis = if (isReloading) 150 else 500),
        label = "reload_overlay_alpha",
    )

    if (overlayAlpha <= 0f) return

    val context = LocalContext.current

    // Background image fade-in: starts dim, progressively reveals
    val bgReveal by animateFloatAsState(
        targetValue = if (isReloading) 0.85f else 1f,
        animationSpec = tween(durationMillis = 800),
        label = "bg_reveal",
    )

    // Spinning dots animation
    val infiniteTransition = rememberInfiniteTransition(label = "reload_spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinner_rotation",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f * overlayAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        // Background image fading in
        val bgPainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(bgImageData)
                .crossfade(false)
                .size(CoilSize.ORIGINAL)
                .build()
        )
        Image(
            painter = bgPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgReveal * overlayAlpha },
        )

        // Dark scrim over image for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * overlayAlpha)),
        )

        // Spinning dots (Windows-style ring spinner)
        val dotCount = 8
        val ringRadius = 20.dp
        Canvas(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { alpha = overlayAlpha },
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = ringRadius.toPx()

            for (i in 0 until dotCount) {
                val angle = Math.toRadians((rotation + i * (360.0 / dotCount)).toDouble())
                val x = cx + r * kotlin.math.cos(angle).toFloat()
                val y = cy + r * kotlin.math.sin(angle).toFloat()

                // Each dot fades: leading dots are bright, trailing dots are dim
                val dotAlpha = (1f - i.toFloat() / dotCount).coerceIn(0.15f, 1f)
                val dotRadius = (3.5f - i * 0.25f).coerceAtLeast(1.5f)

                // Gradient color from primary to secondary
                val blend = i.toFloat() / dotCount
                val dotColor = Color(
                    red = primaryColor.red * (1 - blend) + secondaryColor.red * blend,
                    green = primaryColor.green * (1 - blend) + secondaryColor.green * blend,
                    blue = primaryColor.blue * (1 - blend) + secondaryColor.blue * blend,
                )

                drawCircle(
                    color = dotColor.copy(alpha = dotAlpha),
                    radius = dotRadius,
                    center = Offset(x, y),
                )
                // Glow around leading dots
                if (i < 3) {
                    drawCircle(
                        color = dotColor.copy(alpha = dotAlpha * 0.3f),
                        radius = dotRadius * 2.5f,
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}

package com.orbix.pixora.launcher.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.orbix.pixora.launcher.data.models.AppInfo
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

    val scope = rememberCoroutineScope()

    val dockApps = remember(dockPackages, apps) {
        dockPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    }

    val glowColor = Color(0xFF7C4DFF)

    val appsMap = remember(apps) { apps.associateBy { it.packageName } }

    // Pages from grid slots (16 per page, nullable = empty cell)
    val appPages = remember(gridSlots) {
        if (gridSlots.isEmpty()) emptyList()
        else gridSlots.chunked(APPS_PER_PAGE)
    }
    // Page 0 = clean home (clock only), then app pages, then 1 extra empty page
    val extraEmptyPages = 1
    val pageCount = 1 + appPages.size + extraEmptyPages
    val pagerState = rememberPagerState(pageCount = { pageCount })

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // Clock
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            currentDate = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(now.time)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Resolve background
    val bgImageData: Any = remember(backgroundUri) {
        when {
            backgroundUri.startsWith("asset:") -> {
                val roomId = backgroundUri.removePrefix("asset:")
                "file:///android_asset/icon_rooms/$roomId.png"
            }
            backgroundUri.startsWith("pano:") -> java.io.File(backgroundUri.removePrefix("pano:"))
            backgroundUri.startsWith("file:") -> java.io.File(backgroundUri.removePrefix("file:"))
            else -> "file:///android_asset/icon_rooms/icon_room_01.png"
        }
    }
    val isPanoramic = backgroundUri.startsWith("asset:") || backgroundUri.startsWith("pano:")

    val scrollFraction by remember {
        derivedStateOf {
            if (pagerState.pageCount > 1) {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction) /
                    (pagerState.pageCount - 1).toFloat()
            } else 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) viewModel.openDrawer()
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

        if (showTouchGlow) { TouchGlowOverlay(glowColor = glowColor) }

        // Home screen pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isEditMode,
        ) { page ->
            val appPageIndex = page - 1
            if (appPageIndex in appPages.indices) {
                val pageSlots = appPages[appPageIndex]
                val pageOffset = appPageIndex * APPS_PER_PAGE
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
                        onAppClick = { pkg -> viewModel.launchApp(pkg) },
                        onLongPress = { viewModel.enterEditMode() },
                        onMove = { from, to ->
                            viewModel.moveApp(pageOffset + from, pageOffset + to)
                        },
                        onAddToDock = { pkg -> viewModel.addDockApp(pkg) },
                        onDragToNextPage = { fromLocal ->
                            val targetPage = viewModel.moveAppToNextPage(pageOffset + fromLocal)
                            if (targetPage >= 0) {
                                scope.launch {
                                    pagerState.animateScrollToPage(targetPage + 1) // +1 for clock page
                                }
                            }
                        },
                        onDragToPrevPage = { fromLocal ->
                            val targetPage = viewModel.moveAppToPrevPage(pageOffset + fromLocal)
                            if (targetPage >= 0) {
                                scope.launch {
                                    pagerState.animateScrollToPage(targetPage + 1)
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

        // Edit mode toolbar
        if (isEditMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Auto-reorganize button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { viewModel.resetAppOrder() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Auto-sort",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Done button
                Text(
                    text = "Done",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF7C4DFF).copy(alpha = 0.7f))
                        .clickable { viewModel.exitEditMode() }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        // Clock overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = currentTime, fontSize = 56.sp, fontWeight = FontWeight.Light, color = Color.White)
            Text(text = currentDate, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
        }

        if (showBatteryRing) {
            BatteryRingOverlay(glowColor = glowColor, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 48.dp, end = 12.dp))
        }
        if (showSystemRings) {
            SystemRingsOverlay(glowColor = glowColor, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 155.dp, end = 8.dp))
        }
        if (showEqualizer) {
            EqualizerOverlay(glowColor = glowColor, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp).navigationBarsPadding().padding(horizontal = 24.dp))
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
        }

        // Dock bar
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageIndicator(pageCount = pageCount, currentPage = pagerState.currentPage, modifier = Modifier.padding(bottom = 12.dp))

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

        // App drawer overlay
        if (isDrawerOpen) {
            AppDrawer(
                apps = apps,
                onAppClick = { packageName -> viewModel.closeDrawer(); viewModel.launchApp(packageName) },
                onDismiss = { viewModel.closeDrawer() },
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

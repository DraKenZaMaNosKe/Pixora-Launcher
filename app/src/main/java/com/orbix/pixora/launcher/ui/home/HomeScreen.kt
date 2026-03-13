package com.orbix.pixora.launcher.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.ui.components.*
import com.orbix.pixora.launcher.ui.drawer.AppDrawer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenCatalog: () -> Unit = {},
) {
    val apps by viewModel.installedApps.collectAsState()
    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val dockPackages by viewModel.dockApps.collectAsState()

    val dockApps = remember(dockPackages, apps) {
        dockPackages.mapNotNull { pkg -> apps.find { it.packageName == pkg } }
    }

    val glowColor = Color(0xFF7C4DFF)

    // App grid: 4 columns x 5 rows = 20 apps per page
    val appsPerPage = 20
    val appPages = remember(apps) {
        if (apps.isEmpty()) emptyList()
        else apps.chunked(appsPerPage)
    }
    // Page 0 = clean home (clock only), then app pages, then 2 extra empty pages
    val extraEmptyPages = 2
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
            backgroundUri.startsWith("pano:") -> {
                java.io.File(backgroundUri.removePrefix("pano:"))
            }
            backgroundUri.startsWith("file:") -> {
                java.io.File(backgroundUri.removePrefix("file:"))
            }
            else -> "file:///android_asset/icon_rooms/icon_room_01.png"
        }
    }
    val isPanoramic = backgroundUri.startsWith("asset:") || backgroundUri.startsWith("pano:")

    // Scroll fraction derived from pager state (reactive to swipe)
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

        // Background image
        val bgPainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(bgImageData)
                .crossfade(true)
                .size(CoilSize.ORIGINAL)
                .build()
        )

        if (isPanoramic) {
            // Panoramic: draw manually with scroll control
            PanoramicBackground(
                painter = bgPainter,
                scrollFraction = scrollFraction,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Static: normal Image composable
            Image(
                painter = bgPainter,
                contentDescription = "Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Touch glow layer
        TouchGlowOverlay(glowColor = glowColor)

        // Home screen pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val appPageIndex = page - 1 // page 0 is clean home
            if (appPageIndex in appPages.indices) {
                val pageApps = appPages[appPageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 140.dp, bottom = 160.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        userScrollEnabled = false,
                    ) {
                        items(pageApps, key = { it.packageName }) { app ->
                            AppGridIcon(
                                app = app,
                                onClick = { viewModel.launchApp(app.packageName) },
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize())
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
            Text(
                text = currentTime,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
            )
            Text(
                text = currentDate,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        // Battery ring
        BatteryRingOverlay(
            glowColor = glowColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 48.dp, end = 12.dp),
        )

        // System rings
        SystemRingsOverlay(
            glowColor = glowColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 155.dp, end = 8.dp),
        )

        // Equalizer
        EqualizerOverlay(
            glowColor = glowColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        )

        // Catalog button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 52.dp, start = 12.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { onOpenCatalog() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = "Explore wallpapers",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        // Dock bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (dockApps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dockApps.forEach { app ->
                        DockAppIcon(
                            app = app,
                            onClick = { viewModel.launchApp(app.packageName) },
                            onLongClick = { viewModel.removeDockApp(app.packageName) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Swipe up for apps",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.25f),
            )
        }

        // App drawer overlay
        if (isDrawerOpen) {
            AppDrawer(
                apps = apps,
                onAppClick = { packageName ->
                    viewModel.closeDrawer()
                    viewModel.launchApp(packageName)
                },
                onDismiss = { viewModel.closeDrawer() },
            )
        }
    }
}

/**
 * Draws a panoramic image that scrolls horizontally based on scrollFraction (0..1).
 * Uses drawBehind to bypass composable clipping and render the full wide image.
 */
@Composable
private fun PanoramicBackground(
    painter: Painter,
    scrollFraction: Float,
    modifier: Modifier = Modifier,
) {
    val intrinsicSize = painter.intrinsicSize

    Box(
        modifier = modifier.drawBehind {
            if (intrinsicSize == Size.Unspecified ||
                intrinsicSize.width <= 0 || intrinsicSize.height <= 0
            ) {
                // Image not loaded yet, draw nothing
                return@drawBehind
            }

            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate rendered dimensions: fill height, scale width proportionally
            val imageAspect = intrinsicSize.width / intrinsicSize.height
            val renderWidth = canvasHeight * imageAspect
            val renderHeight = canvasHeight

            // How far we can scroll (image wider than screen)
            val maxScroll = (renderWidth - canvasWidth).coerceAtLeast(0f)
            val scrollX = -scrollFraction * maxScroll

            translate(left = scrollX, top = 0f) {
                with(painter) {
                    draw(size = Size(renderWidth, renderHeight))
                }
            }
        }
    )
}

@Composable
private fun AppGridIcon(
    app: AppInfo,
    onClick: () -> Unit,
) {
    val bitmap = remember(app.iconBytes) {
        BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockAppIcon(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = remember(app.iconBytes) {
        BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
    }

    Column(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = app.label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

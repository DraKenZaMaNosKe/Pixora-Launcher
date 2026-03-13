package com.orbix.pixora.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbix.pixora.launcher.ui.components.PageIndicator
import com.orbix.pixora.launcher.ui.drawer.AppDrawer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
) {
    val apps by viewModel.installedApps.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()

    val pageCount = 4
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) { // Swipe up
                        viewModel.openDrawer()
                    }
                }
            }
    ) {
        // Panoramic background - scrolls with pager
        val context = LocalContext.current
        val assetPath = "file:///android_asset/icon_rooms/$selectedRoom.png"

        // The panoramic image fills full width based on page offset
        val pageOffset = if (pagerState.pageCount > 1) {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction) / (pagerState.pageCount - 1).toFloat()
        } else 0f

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(assetPath)
                .crossfade(true)
                .build(),
            contentDescription = "Icon Room Background",
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    // Calculate how much wider the image is vs the screen
                    // Panoramic images are ~5:1, screen is ~9:20
                    // So image width ≈ 4.4x screen width when fit to height
                    val imageWidthRatio = 4.0f // approximate
                    val maxTranslateX = size.width * (imageWidthRatio - 1f)
                    translationX = -pageOffset * maxTranslateX
                }
        )

        // Home screen pages (transparent, icons go here)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // Each page is a transparent layer for icons
            Box(modifier = Modifier.fillMaxSize()) {
                // TODO Phase 3: App icons grid per page
            }
        }

        // Clock overlay at top
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

        // Page indicator at bottom
        PageIndicator(
            pageCount = pageCount,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .navigationBarsPadding(),
        )

        // Swipe up hint
        Text(
            text = "Swipe up for apps",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        )

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

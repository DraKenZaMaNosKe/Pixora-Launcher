package com.orbix.pixora.launcher.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.models.Wallpaper
import com.orbix.pixora.launcher.ui.catalog.CatalogScreen
import com.orbix.pixora.launcher.ui.catalog.CatalogViewModel
import com.orbix.pixora.launcher.ui.catalog.WallpaperPreviewScreen
import com.orbix.pixora.launcher.ui.home.HomeScreen
import com.orbix.pixora.launcher.ui.home.HomeViewModel
import com.orbix.pixora.launcher.ui.setup.SetupWizard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun PixoraLauncherApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalogViewModel: CatalogViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()

    var setupDone by remember { mutableStateOf<Boolean?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var showWallpaperPreview by remember { mutableStateOf(false) }
    var showStoryDetail by remember { mutableStateOf(false) }
    var showIconRoomPreview by remember { mutableStateOf(false) }

    var selectedWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var selectedStory by remember { mutableStateOf<Story?>(null) }
    var selectedIconRoom by remember { mutableStateOf<IconRoom?>(null) }

    LaunchedEffect(Unit) {
        val done = context.pixoraDataStore.data.map { prefs ->
            prefs[booleanPreferencesKey("setup_completed")] ?: false
        }.first()
        setupDone = done
    }

    if (setupDone == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0F)))
        return
    }

    if (setupDone == false) {
        SetupWizard(
            onComplete = {
                scope.launch {
                    context.pixoraDataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("setup_completed")] = true
                    }
                    setupDone = true
                }
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            viewModel = homeViewModel,
            onOpenCatalog = { showCatalog = true }
        )

        AnimatedVisibility(
            visible = showCatalog && !showWallpaperPreview && !showStoryDetail && !showIconRoomPreview,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            CatalogScreen(
                viewModel = catalogViewModel,
                onClose = { showCatalog = false },
                onWallpaperSelected = { wp ->
                    selectedWallpaper = wp
                    showWallpaperPreview = true
                },
                onStorySelected = { story ->
                    selectedStory = story
                    showStoryDetail = true
                },
                onIconRoomSelected = { room ->
                    selectedIconRoom = room
                    showIconRoomPreview = true
                },
            )
        }

        AnimatedVisibility(
            visible = showWallpaperPreview,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            selectedWallpaper?.let { wp ->
                WallpaperPreviewScreen(
                    wallpaper = wp,
                    catalogViewModel = catalogViewModel,
                    onBack = { showWallpaperPreview = false },
                    onSetAsLauncherBg = { path ->
                        homeViewModel.setBackgroundFile(path)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showStoryDetail,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            selectedStory?.let { story ->
                StoryDetailScreen(
                    story = story,
                    catalogViewModel = catalogViewModel,
                    onBack = { showStoryDetail = false },
                    onSetAsLauncherBg = { path ->
                        homeViewModel.setBackgroundFile(path)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showIconRoomPreview,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            selectedIconRoom?.let { room ->
                IconRoomPreviewScreen(
                    room = room,
                    catalogViewModel = catalogViewModel,
                    onBack = { showIconRoomPreview = false },
                    onSetAsLauncherBg = { uri ->
                        homeViewModel.setBackgroundFile(uri)
                    },
                )
            }
        }
    }
}

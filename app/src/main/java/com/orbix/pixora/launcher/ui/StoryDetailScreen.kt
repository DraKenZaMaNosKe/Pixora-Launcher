package com.orbix.pixora.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import com.orbix.pixora.launcher.service.WallpaperInstallService
import com.orbix.pixora.launcher.ui.catalog.CatalogViewModel
import com.orbix.pixora.launcher.ui.components.InstallProgressOverlay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryDetailScreen(
    story: Story,
    catalogViewModel: CatalogViewModel,
    onBack: () -> Unit,
    onSetAsLauncherBg: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()

    val glowColor = try {
        Color(android.graphics.Color.parseColor(story.glowColor))
    } catch (_: Exception) { Color(0xFF7C4DFF) }

    // Progress overlay state
    var showProgress by remember { mutableStateOf(false) }
    var progressValue by remember { mutableStateOf<Float?>(null) }
    var progressText by remember { mutableStateOf("") }
    var progressSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    fun doInstall(target: WallpaperInstallService.Target) {
        if (isWorking) return
        isWorking = true
        scope.launch {
            showProgress = true
            progressValue = 0f
            progressText = "Downloading ${story.title}..."
            progressSuccess = null

            val path = catalogViewModel.downloadWallpaper(story.coverImage) { progress ->
                progressValue = progress
            }

            if (path == null) {
                progressText = "Download failed"
                progressSuccess = false
                kotlinx.coroutines.delay(2000)
                showProgress = false
                isWorking = false
                return@launch
            }

            progressValue = null
            progressText = "Setting wallpaper..."

            val success = catalogViewModel.installService.setWallpaper(path, target)

            if (success) {
                progressText = "Applying to launcher..."
                onSetAsLauncherBg?.invoke("file:$path")
                progressText = "Wallpaper applied!"
                progressSuccess = true
            } else {
                progressText = "Failed to set wallpaper"
                progressSuccess = false
            }

            kotlinx.coroutines.delay(1500)
            showProgress = false
            isWorking = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
        // Cover image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(SupabaseConfig.imageUrl(story.coverImage))
                .crossfade(true)
                .build(),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
        )

        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0A0A0F), Color(0xFF0A0A0F))
                    )
                )
                .padding(24.dp),
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(story.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(story.description, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${story.frames.size} frames • ${story.intervalMinutes}min interval",
                fontSize = 12.sp,
                color = glowColor,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Frame previews
            if (story.frames.isNotEmpty()) {
                Text("Frames", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(story.frames) { frame ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(SupabaseConfig.imageUrl(frame.imageFile))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 100.dp, height = 160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Main button: apply cover as wallpaper (both screens + launcher)
            Button(
                onClick = { doInstall(WallpaperInstallService.Target.BOTH) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = glowColor),
            ) {
                Icon(Icons.Default.Wallpaper, "Set", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply Wallpaper", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Save cover to gallery
                OutlinedButton(
                    onClick = {
                        if (isWorking) return@OutlinedButton
                        isWorking = true
                        scope.launch {
                            showProgress = true
                            progressValue = 0f
                            progressText = "Downloading..."
                            progressSuccess = null
                            val path = catalogViewModel.downloadWallpaper(story.coverImage) { p ->
                                progressValue = p
                            }
                            if (path != null) {
                                progressValue = null
                                progressText = "Saving to gallery..."
                                val saved = catalogViewModel.installService.saveToGallery(path, story.title + ".png")
                                progressText = if (saved) "Saved to gallery!" else "Save failed"
                                progressSuccess = saved
                            } else {
                                progressText = "Download failed"
                                progressSuccess = false
                            }
                            kotlinx.coroutines.delay(1500)
                            showProgress = false
                            isWorking = false
                        }
                    },
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Download, "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery", fontSize = 13.sp, color = Color.White)
                }
            }
        }

        // Progress overlay
        InstallProgressOverlay(
            isVisible = showProgress,
            progress = progressValue,
            statusText = progressText,
            isSuccess = progressSuccess,
        )
    }
}

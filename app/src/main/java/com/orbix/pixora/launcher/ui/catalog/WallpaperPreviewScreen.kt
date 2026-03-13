package com.orbix.pixora.launcher.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.orbix.pixora.launcher.data.models.Wallpaper
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import com.orbix.pixora.launcher.service.WallpaperInstallService
import com.orbix.pixora.launcher.ui.components.InstallProgressOverlay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPreviewScreen(
    wallpaper: Wallpaper,
    catalogViewModel: CatalogViewModel,
    onBack: () -> Unit,
    onSetAsLauncherBg: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }

    // Progress overlay state
    var showProgress by remember { mutableStateOf(false) }
    var progressValue by remember { mutableStateOf<Float?>(null) }
    var progressText by remember { mutableStateOf("") }
    var progressSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isWorking by remember { mutableStateOf(false) } // prevents double-tap

    val glowColor = try {
        Color(android.graphics.Color.parseColor(wallpaper.glowColor))
    } catch (_: Exception) { Color(0xFF7C4DFF) }

    /** Shared install logic */
    fun doInstall(target: WallpaperInstallService.Target) {
        if (isWorking) return
        isWorking = true
        scope.launch {
            // Step 1: Download
            showProgress = true
            progressValue = 0f
            progressText = "Downloading ${wallpaper.name}..."
            progressSuccess = null

            val path = catalogViewModel.downloadWallpaper(wallpaper.imageFile) { progress ->
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

            // Step 2: Install
            progressValue = null // indeterminate
            progressText = "Setting wallpaper..."

            val success = catalogViewModel.installService.setWallpaper(path, target)

            if (success) {
                // Step 3: Apply to launcher
                progressText = "Applying to launcher..."
                if (wallpaper.isPanoramic) {
                    onSetAsLauncherBg?.invoke("pano:$path")
                } else {
                    onSetAsLauncherBg?.invoke(path)
                }
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Full screen preview
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(SupabaseConfig.imageUrl(wallpaper.imageFile))
                .crossfade(true)
                .build(),
            contentDescription = wallpaper.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }

        // Bottom area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(wallpaper.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (wallpaper.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(wallpaper.description, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }

            if (wallpaper.badge != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = wallpaper.badge,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = glowColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(glowColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Apply wallpaper button
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
                OutlinedButton(
                    onClick = { showSheet = true },
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder,
                ) {
                    Text("Choose Screen", fontSize = 13.sp, color = Color.White)
                }
                OutlinedButton(
                    onClick = {
                        if (isWorking) return@OutlinedButton
                        isWorking = true
                        scope.launch {
                            showProgress = true
                            progressValue = 0f
                            progressText = "Downloading..."
                            progressSuccess = null
                            val path = catalogViewModel.downloadWallpaper(wallpaper.imageFile) { p ->
                                progressValue = p
                            }
                            if (path != null) {
                                progressValue = null
                                progressText = "Saving to gallery..."
                                val saved = catalogViewModel.installService.saveToGallery(path, wallpaper.name + ".png")
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
                    border = ButtonDefaults.outlinedButtonBorder,
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

    // Bottom sheet for target selection
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Color(0xFF141420),
        ) {
            WallpaperTargetSheet(
                glowColor = glowColor,
                isPanoramic = wallpaper.isPanoramic,
                onSelect = { target ->
                    showSheet = false
                    doInstall(target)
                }
            )
        }
    }
}

@Composable
private fun WallpaperTargetSheet(
    glowColor: Color,
    isPanoramic: Boolean,
    onSelect: (WallpaperInstallService.Target) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)
    ) {
        Text("Apply wallpaper to", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(20.dp))

        TargetOption(Icons.Default.Home, "Home Screen",
            if (isPanoramic) "Panoramic scroll enabled" else "Static wallpaper",
            glowColor) { onSelect(WallpaperInstallService.Target.HOME) }
        TargetOption(Icons.Default.Lock, "Lock Screen", "Shows on lock screen",
            glowColor) { onSelect(WallpaperInstallService.Target.LOCK) }
        TargetOption(Icons.Default.Smartphone, "Both", "Home and lock screen",
            glowColor) { onSelect(WallpaperInstallService.Target.BOTH) }
    }
}

@Composable
private fun TargetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, subtitle: String, glowColor: Color, onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A2E),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, title, tint = glowColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
        }
    }
}

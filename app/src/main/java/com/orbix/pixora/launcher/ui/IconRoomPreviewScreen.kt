package com.orbix.pixora.launcher.ui

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
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.service.WallpaperInstallService
import com.orbix.pixora.launcher.ui.catalog.CatalogViewModel
import com.orbix.pixora.launcher.ui.components.InstallProgressOverlay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconRoomPreviewScreen(
    room: IconRoom,
    catalogViewModel: CatalogViewModel,
    onBack: () -> Unit,
    onSetAsLauncherBg: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val glowColor = Color(0xFF7C4DFF)

    // Progress overlay state
    var showProgress by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    fun doInstall(target: WallpaperInstallService.Target, alsoSetLauncher: Boolean) {
        if (isWorking) return
        isWorking = true
        scope.launch {
            showProgress = true
            progressText = "Preparing ${room.title}..."
            progressSuccess = null

            val path = catalogViewModel.downloadIconRoom(room.assetName)
            if (path == null) {
                progressText = "Failed to prepare image"
                progressSuccess = false
                kotlinx.coroutines.delay(2000)
                showProgress = false
                isWorking = false
                return@launch
            }

            progressText = "Setting wallpaper..."
            val ok = catalogViewModel.installService.setWallpaper(path, target)

            if (ok) {
                if (alsoSetLauncher) {
                    progressText = "Applying to launcher..."
                    onSetAsLauncherBg?.invoke("asset:${room.assetName}")
                }
                progressText = "Applied!"
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
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("file:///android_asset/icon_rooms/${room.assetName}.png")
                .crossfade(true)
                .build(),
            contentDescription = room.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(room.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Icon Room • Panoramic",
                fontSize = 12.sp, color = glowColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(glowColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main button: apply to launcher + system wallpaper
            Button(
                onClick = { doInstall(WallpaperInstallService.Target.BOTH, alsoSetLauncher = true) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = glowColor),
            ) {
                Icon(Icons.Default.Wallpaper, "Set", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply Room", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Just launcher (no system wallpaper)
                OutlinedButton(
                    onClick = {
                        if (isWorking) return@OutlinedButton
                        onSetAsLauncherBg?.invoke("asset:${room.assetName}")
                        // Quick feedback without overlay
                    },
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Launcher Only", fontSize = 12.sp, color = Color.White)
                }

                // Save to gallery
                OutlinedButton(
                    onClick = {
                        if (isWorking) return@OutlinedButton
                        isWorking = true
                        scope.launch {
                            showProgress = true
                            progressText = "Saving to gallery..."
                            progressSuccess = null
                            val path = catalogViewModel.downloadIconRoom(room.assetName)
                            if (path != null) {
                                val ok = catalogViewModel.installService.saveToGallery(path, "${room.assetName}.png")
                                progressText = if (ok) "Saved!" else "Save failed"
                                progressSuccess = ok
                            } else {
                                progressText = "Failed"
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
                    Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        // Progress overlay
        InstallProgressOverlay(
            isVisible = showProgress,
            progress = null, // icon rooms are local assets, no download progress
            statusText = progressText,
            isSuccess = progressSuccess,
        )
    }
}

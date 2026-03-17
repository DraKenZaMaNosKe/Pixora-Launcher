package com.orbix.pixora.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.remote.SupabaseConfig
import com.orbix.pixora.launcher.service.DownloadService
import com.orbix.pixora.launcher.service.StoryManager
import com.orbix.pixora.launcher.service.WallpaperInstallService
import com.orbix.pixora.launcher.ui.catalog.CatalogViewModel
import com.orbix.pixora.launcher.ui.components.InstallProgressOverlay
import kotlinx.coroutines.launch

@Composable
fun StoryDetailScreen(
    story: Story,
    catalogViewModel: CatalogViewModel,
    onBack: () -> Unit,
    onSetAsLauncherBg: ((String) -> Unit)? = null,
    onApplyCinematicLayout: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val glowColor = try {
        Color(android.graphics.Color.parseColor(story.glowColor))
    } catch (_: Exception) { Color(0xFF7C4DFF) }

    val activeStory by StoryManager.activeStory.collectAsState()
    val currentFrame by StoryManager.currentFrameIndex.collectAsState()
    val isThisStoryActive = activeStory?.id == story.id

    // Selected frame for preview
    var selectedFrameIndex by remember { mutableIntStateOf(0) }

    // Progress state
    var showProgress by remember { mutableStateOf(false) }
    var progressValue by remember { mutableStateOf<Float?>(null) }
    var progressText by remember { mutableStateOf("") }
    var progressSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    // Language for captions (0=ES, 1=EN, 2=JA)
    var langIndex by remember { mutableIntStateOf(1) }
    val langLabels = listOf("ES", "EN", "JA")

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
        // Cover / selected frame image
        val previewUrl = if (story.frames.isNotEmpty() && selectedFrameIndex in story.frames.indices) {
            SupabaseConfig.imageUrl(story.frames[selectedFrameIndex].imageFile)
        } else {
            SupabaseConfig.imageUrl(story.coverImage)
        }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(previewUrl)
                .crossfade(300)
                .build(),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.48f),
        )

        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }

        // Active story badge
        if (isThisStoryActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(glowColor.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("• Frame ${currentFrame + 1}/${story.frames.size}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }

        // Frame counter on image
        if (story.frames.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = if (isThisStoryActive) 52.dp else 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "${selectedFrameIndex + 1} / ${story.frames.size}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        // Content below image
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.57f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0A0A0F), Color(0xFF0A0A0F))
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Title
            Text(story.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))

            // Description
            if (story.description.isNotBlank()) {
                Text(story.description, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Story info chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(icon = Icons.Default.Image, text = "${story.frames.size} frames", color = glowColor)
                InfoChip(icon = Icons.Default.Schedule, text = "${story.intervalMinutes} min", color = glowColor)
                InfoChip(icon = Icons.Default.Category, text = story.category, color = glowColor)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Frame carousel
            if (story.frames.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Frames", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

                    // Language selector
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        langLabels.forEachIndexed { i, label ->
                            val isSelected = i == langIndex
                            val bgColor by animateColorAsState(
                                if (isSelected) glowColor else Color(0xFF1A1A2E),
                                tween(200), label = "lang"
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .clickable { langIndex = i }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(label, fontSize = 11.sp, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(story.frames) { index, frame ->
                        val isSelected = index == selectedFrameIndex
                        val borderColor by animateColorAsState(
                            if (isSelected) glowColor else Color.Transparent,
                            tween(200), label = "frame_border"
                        )
                        Column(
                            modifier = Modifier
                                .width(110.dp)
                                .clickable { selectedFrameIndex = index },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(SupabaseConfig.imageUrl(frame.imageFile))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Frame ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                // Frame number
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) glowColor else Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${index + 1}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                // Active frame indicator
                                if (isThisStoryActive && index == currentFrame) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(glowColor.copy(alpha = 0.85f))
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("NOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            // Caption
                            val caption = frame.captionForLang(langIndex)
                            if (caption.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    caption,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            if (isThisStoryActive) {
                // Deactivate button
                Button(
                    onClick = {
                        scope.launch {
                            StoryManager.deactivate(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deactivate Story", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Activate button
                Button(
                    onClick = {
                        if (isWorking || story.frames.isEmpty()) return@Button
                        isWorking = true
                        scope.launch {
                            showProgress = true
                            progressValue = 0f
                            progressText = "Downloading frames..."
                            progressSuccess = null

                            val downloadService = DownloadService(context)
                            val success = StoryManager.activate(
                                context = context,
                                story = story,
                                downloadService = downloadService,
                            ) { current, total ->
                                progressValue = current.toFloat() / total
                                progressText = "Downloading frame $current of $total..."
                            }

                            if (success) {
                                progressText = "Story activated!"
                                progressSuccess = true
                                // Set as panoramic background
                                val panoUri = StoryManager.getCurrentPanoUri()
                                if (panoUri != null) {
                                    onSetAsLauncherBg?.invoke(panoUri)
                                }
                                // Auto-arrange icons cinematically
                                onApplyCinematicLayout?.invoke()
                            } else {
                                progressText = "Failed to download all frames"
                                progressSuccess = false
                            }

                            kotlinx.coroutines.delay(1500)
                            showProgress = false
                            isWorking = false
                        }
                    },
                    enabled = !isWorking && story.frames.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = glowColor,
                    ),
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Activate Story", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Apply cover only
                OutlinedButton(
                    onClick = {
                        if (isWorking) return@OutlinedButton
                        isWorking = true
                        scope.launch {
                            showProgress = true
                            progressValue = 0f
                            progressText = "Downloading cover..."
                            progressSuccess = null
                            val path = catalogViewModel.downloadWallpaper(story.coverImage) { p ->
                                progressValue = p
                            }
                            if (path != null) {
                                progressValue = null
                                progressText = "Setting wallpaper..."
                                val ok = catalogViewModel.installService.setWallpaper(path, WallpaperInstallService.Target.BOTH)
                                if (ok) {
                                    onSetAsLauncherBg?.invoke("file:$path")
                                    progressText = "Cover applied!"
                                    progressSuccess = true
                                } else {
                                    progressText = "Failed"
                                    progressSuccess = false
                                }
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
                    Icon(Icons.Default.Wallpaper, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cover Only", fontSize = 12.sp, color = Color.White)
                }

                // Save to gallery
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
                                val saved = catalogViewModel.installService.saveToGallery(path, "${story.title}.png")
                                progressText = if (saved) "Saved!" else "Failed"
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
                    Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery", fontSize = 12.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

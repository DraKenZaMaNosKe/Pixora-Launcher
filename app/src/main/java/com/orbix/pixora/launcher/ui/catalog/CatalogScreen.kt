package com.orbix.pixora.launcher.ui.catalog

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.data.models.Story
import com.orbix.pixora.launcher.data.models.Wallpaper
import com.orbix.pixora.launcher.data.remote.SupabaseConfig

@Composable
fun CatalogScreen(
    onClose: () -> Unit,
    onWallpaperSelected: (Wallpaper) -> Unit,
    onStorySelected: (Story) -> Unit,
    onIconRoomSelected: (IconRoom) -> Unit,
    viewModel: CatalogViewModel = viewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Wallpapers", "Stories", "Icon Rooms")

    val wallpapers by viewModel.wallpapers.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            CatalogTopBar(onClose = onClose)

            // Tab bar
            CatalogTabBar(
                tabs = tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            // Content
            when {
                isLoading -> LoadingContent()
                else -> {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        },
                        label = "tab_content"
                    ) { tab ->
                        when (tab) {
                            0 -> WallpapersTab(
                                wallpapers = viewModel.filteredWallpapers(),
                                categories = viewModel.categories,
                                selectedCategory = selectedCategory,
                                onCategorySelected = viewModel::selectCategory,
                                onWallpaperClick = onWallpaperSelected,
                            )
                            1 -> StoriesTab(
                                stories = stories,
                                onStoryClick = onStorySelected,
                            )
                            2 -> IconRoomsTab(
                                rooms = viewModel.iconRooms,
                                onRoomClick = onIconRoomSelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Explore",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CatalogTabBar(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selectedTab
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF7C4DFF) else Color.Transparent,
                animationSpec = tween(200),
                label = "tab_bg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

// ── Wallpapers Tab ──────────────────────────────────────────

@Composable
private fun WallpapersTab(
    wallpapers: List<Wallpaper>,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onWallpaperClick: (Wallpaper) -> Unit,
) {
    Column {
        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories) { cat ->
                CategoryChip(
                    label = cat.lowercase().replaceFirstChar { it.uppercase() },
                    isSelected = cat == selectedCategory,
                    onClick = { onCategorySelected(cat) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (wallpapers.isEmpty()) {
            EmptyState("No wallpapers found", "Pull down to refresh")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(wallpapers, key = { it.id }) { wallpaper ->
                    WallpaperCard(wallpaper = wallpaper, onClick = { onWallpaperClick(wallpaper) })
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF7C4DFF) else Color(0xFF1A1A2E),
        animationSpec = tween(200),
        label = "chip_bg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun WallpaperCard(wallpaper: Wallpaper, onClick: () -> Unit) {
    val glowColor = try {
        Color(android.graphics.Color.parseColor(wallpaper.glowColor))
    } catch (_: Exception) { Color(0xFF7C4DFF) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(SupabaseConfig.imageUrl(wallpaper.previewUrl))
                .crossfade(true)
                .build(),
            contentDescription = wallpaper.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Name + badge
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            if (wallpaper.badge != null) {
                Text(
                    text = wallpaper.badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = glowColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(glowColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = wallpaper.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Panoramic indicator
        if (wallpaper.isPanoramic) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00E5FF).copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("PANO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Stories Tab ──────────────────────────────────────────────

@Composable
private fun StoriesTab(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit,
) {
    if (stories.isEmpty()) {
        EmptyState("No stories yet", "Stories will appear here")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(stories, key = { it.id }) { story ->
                StoryCard(story = story, onClick = { onStoryClick(story) })
            }
        }
    }
}

@Composable
private fun StoryCard(story: Story, onClick: () -> Unit) {
    val glowColor = try {
        Color(android.graphics.Color.parseColor(story.glowColor))
    } catch (_: Exception) { Color(0xFF7C4DFF) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(SupabaseConfig.imageUrl(story.coverImage))
                .crossfade(true)
                .build(),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Glow border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, glowColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        )

        // Bottom info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = story.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${story.frames.size} frames",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }

        // Story badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(glowColor.copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("STORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ── Icon Rooms Tab ──────────────────────────────────────────

@Composable
private fun IconRoomsTab(
    rooms: List<IconRoom>,
    onRoomClick: (IconRoom) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(rooms, key = { it.id }) { room ->
            IconRoomCard(room = room, onClick = { onRoomClick(room) })
        }
    }
}

@Composable
private fun IconRoomCard(room: IconRoom, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
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
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        Text(
            text = room.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Room badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF7C4DFF).copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("ROOM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ── Shared ──────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color(0xFF7C4DFF))
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}

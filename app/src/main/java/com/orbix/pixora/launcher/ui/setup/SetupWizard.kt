package com.orbix.pixora.launcher.ui.setup

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.data.models.IconRoom
import com.orbix.pixora.launcher.service.AppsRepository
import com.orbix.pixora.launcher.ui.EffectKeys
import com.orbix.pixora.launcher.ui.pixoraDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch

@Composable
fun SetupWizard(onComplete: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf("welcome", "room", "apps", "effects", "activate")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        androidx.compose.animation.Crossfade(
            targetState = currentStep,
            animationSpec = tween(300),
            label = "setup_step"
        ) { step ->
            when (step) {
                0 -> WelcomeStep(onNext = { currentStep = 1 })
                1 -> RoomPickerStep(onNext = { currentStep = 2 })
                2 -> AppsStep(onNext = { currentStep = 3 })
                3 -> EffectsStep(onNext = { currentStep = 4 })
                4 -> ActivateStep(onComplete = onComplete)
            }
        }

        // Step indicator at bottom
        StepIndicator(
            totalSteps = steps.size,
            currentStep = currentStep,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(logoScale)
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF).copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
            Text(
                text = "P",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Pixora Launcher",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your apps live inside the art",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Panoramic wallpapers \u2022 Equalizer \u2022 Touch glow\nBattery & system rings \u2022 Beautiful app drawer",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C4DFF)
            ),
        ) {
            Text(
                text = "Get Started",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RoomPickerStep(onNext: () -> Unit) {
    val rooms = IconRoom.ALL
    var selectedIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Choose your world",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your apps will live inside this panoramic room",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Main preview
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/icon_rooms/${rooms[selectedIndex].assetName}.png")
                    .crossfade(true)
                    .build(),
                contentDescription = rooms[selectedIndex].title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Title overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = rooms[selectedIndex].title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal thumbnail carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rooms.size) { index ->
                val room = rooms[index]
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) Color(0xFF7C4DFF) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { selectedIndex = index }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/icon_rooms/${room.assetName}.png")
                            .crossfade(true)
                            .build(),
                        contentDescription = room.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C4DFF)
            ),
        ) {
            Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(56.dp))
    }
}

@Composable
private fun AppsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val repo = AppsRepository(context)
        apps = repo.getInstalledApps().take(24) // Show top 24
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Your apps",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "We found ${apps.size} apps on your device",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color(0xFF7C4DFF))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(apps) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val bitmap = remember(app.packageName) {
                            BitmapFactory.decodeByteArray(app.iconBytes, 0, app.iconBytes.size)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(56.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C4DFF)
            ),
        ) {
            Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun EffectsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Request RECORD_AUDIO permission for equalizer
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, continue */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var touchGlow by remember { mutableStateOf(true) }
    var equalizer by remember { mutableStateOf(true) }
    var batteryRing by remember { mutableStateOf(true) }
    var systemRings by remember { mutableStateOf(true) }
    var ambientParticles by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Make it yours",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose the effects for your home screen",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        EffectToggle("Touch Glow", "Light ripple when you touch", touchGlow) { touchGlow = it }
        EffectToggle("Equalizer", "Music visualizer bars", equalizer) { equalizer = it }
        EffectToggle("Battery Ring", "Circular battery indicator", batteryRing) { batteryRing = it }
        EffectToggle("System Rings", "RAM & Storage indicators", systemRings) { systemRings = it }
        EffectToggle("Ambient Particles", "Floating particles on home", ambientParticles) { ambientParticles = it }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                scope.launch {
                    context.pixoraDataStore.edit { prefs ->
                        prefs[EffectKeys.TOUCH_GLOW] = touchGlow
                        prefs[EffectKeys.EQUALIZER] = equalizer
                        prefs[EffectKeys.BATTERY_RING] = batteryRing
                        prefs[EffectKeys.SYSTEM_RINGS] = systemRings
                        prefs[EffectKeys.AMBIENT_PARTICLES] = ambientParticles
                    }
                    onNext()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C4DFF)
            ),
        ) {
            Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EffectToggle(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = Color.White)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF7C4DFF),
                checkedTrackColor = Color(0xFF7C4DFF).copy(alpha = 0.4f),
            )
        )
    }
}

@Composable
private fun ActivateStep(onComplete: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Almost there!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Set Pixora as your default launcher to get the full experience",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                // Mark setup done FIRST, then open settings
                // So when activity restarts we go to HOME
                onComplete()
                try {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    context.startActivity(intent)
                } catch (_: Exception) { }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF7C4DFF)
            ),
        ) {
            Text(
                "Set as Default Launcher",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onComplete) {
            Text(
                "Skip for now",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        InfoCard("\uD83C\uDFE0", "Your new home", "Pixora replaces your home screen with a panoramic art experience")
        Spacer(modifier = Modifier.height(12.dp))
        InfoCard("\uD83C\uDFB5", "Music visualizer", "See your music come alive with the equalizer on your home screen")
        Spacer(modifier = Modifier.height(12.dp))
        InfoCard("\uD83D\uDD04", "Easy to revert", "You can always switch back to your previous launcher in Settings")
    }
}

@Composable
private fun InfoCard(emoji: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141420))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun StepIndicator(totalSteps: Int, currentStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(totalSteps) { index ->
            val width by animateDpAsState(
                targetValue = if (index == currentStep) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.7f),
                label = "indicator_width"
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (index <= currentStep) Color(0xFF7C4DFF)
                        else Color.White.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

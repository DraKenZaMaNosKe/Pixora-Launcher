package com.orbix.pixora.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.orbix.pixora.launcher.audio.AudioCaptureService
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.orbix.pixora.launcher.audio.SoundEngine
import com.orbix.pixora.launcher.ui.theme.ThemeManager
import com.orbix.pixora.launcher.ui.theme.Themes
import com.orbix.pixora.launcher.service.BackupService
import com.orbix.pixora.launcher.ui.EffectKeys
import com.orbix.pixora.launcher.ui.home.HomeViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onReconnectEqualizer: () -> Unit = {},
    onSetDefaultLauncher: () -> Unit = {},
) {
    val showTouchGlow by viewModel.showTouchGlow.collectAsState()
    val showEqualizer by viewModel.showEqualizer.collectAsState()
    val showBatteryRing by viewModel.showBatteryRing.collectAsState()
    val showSystemRings by viewModel.showSystemRings.collectAsState()
    val showAmbientParticles by viewModel.showAmbientParticles.collectAsState()
    val isCapturing by AudioCaptureService.isCapturing.collectAsState()
    val soundsEnabled by SoundEngine.enabled.collectAsState()
    val soundTheme by SoundEngine.currentTheme.collectAsState()
    val uiTheme by ThemeManager.current.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showImportDialog by remember { mutableStateOf(false) }
    var backupList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingBackups by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme section
            SectionHeader("App Theme")
            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(Themes.ALL) { theme ->
                    val isSelected = theme.id == uiTheme.id
                    val borderColor by animateColorAsState(
                        if (isSelected) theme.primary else Color.Transparent,
                        tween(200), label = "theme_border"
                    )
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) theme.surface else Color(0xFF1A1A2E))
                            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                ThemeManager.setTheme(context, theme)
                                SoundEngine.playTap()
                            }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Color preview circles
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(16.dp).clip(CircleShape).background(theme.primary))
                            Box(Modifier.size(16.dp).clip(CircleShape).background(theme.secondary))
                            Box(Modifier.size(16.dp).clip(CircleShape).background(theme.background))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = theme.emoji,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = theme.name,
                            fontSize = 9.sp,
                            color = if (isSelected) theme.primary else Color.White.copy(alpha = 0.5f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Effects section
            SectionHeader("Effects")

            SettingsToggle(
                title = "Touch Glow",
                subtitle = "Light ripple when you touch the screen",
                checked = showTouchGlow,
                onCheckedChange = { viewModel.setEffect(EffectKeys.TOUCH_GLOW, it) },
            )
            SettingsToggle(
                title = "Equalizer",
                subtitle = "Real-time music visualizer bars",
                checked = showEqualizer,
                onCheckedChange = { viewModel.setEffect(EffectKeys.EQUALIZER, it) },
            )
            SettingsToggle(
                title = "Battery Ring",
                subtitle = "Circular battery percentage indicator",
                checked = showBatteryRing,
                onCheckedChange = { viewModel.setEffect(EffectKeys.BATTERY_RING, it) },
            )
            SettingsToggle(
                title = "System Rings",
                subtitle = "RAM & Storage usage indicators",
                checked = showSystemRings,
                onCheckedChange = { viewModel.setEffect(EffectKeys.SYSTEM_RINGS, it) },
            )
            SettingsToggle(
                title = "Ambient Particles",
                subtitle = "Floating particles on home screen",
                checked = showAmbientParticles,
                onCheckedChange = { viewModel.setEffect(EffectKeys.AMBIENT_PARTICLES, it) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sound section
            SectionHeader("Sounds")

            SettingsToggle(
                title = "UI Sounds",
                subtitle = "Play sounds on interactions",
                checked = soundsEnabled,
                onCheckedChange = { SoundEngine.setEnabled(context, it) },
            )

            if (soundsEnabled) {
                Text(
                    text = "Sound Theme",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SoundEngine.availableThemes.forEach { theme ->
                        val isSelected = theme == soundTheme
                        val bgColor by animateColorAsState(
                            if (isSelected) Color(0xFF7C4DFF) else Color(0xFF1A1A2E),
                            tween(200), label = "theme_bg"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .clickable {
                                    SoundEngine.setTheme(context, theme)
                                    SoundEngine.playTap()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = theme.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick actions section
            SectionHeader("Quick Actions")

            SettingsActionButton(
                icon = Icons.Default.MusicNote,
                title = "Reconnect Equalizer",
                subtitle = if (isCapturing) "Capturing audio — tap to reconnect" else "Not capturing — tap to reconnect",
                accentColor = if (isCapturing) Color(0xFF4CAF50) else Color(0xFFFF9800),
                onClick = onReconnectEqualizer,
            )

            SettingsActionButton(
                icon = Icons.Default.Home,
                title = "Set as Default Launcher",
                subtitle = "Make Pixora your home screen",
                accentColor = Color(0xFF7C4DFF),
                onClick = onSetDefaultLauncher,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Backup & Restore section
            SectionHeader("Backup & Restore")

            SettingsActionButton(
                icon = Icons.Default.Upload,
                title = "Export Backup",
                subtitle = "Save layouts, dock, effects to Downloads/Pixora",
                accentColor = Color(0xFF00E5FF),
                onClick = {
                    scope.launch {
                        val result = BackupService.exportBackup(context)
                        if (result != null) {
                            snackbarHostState.showSnackbar("Backup saved: $result")
                        } else {
                            snackbarHostState.showSnackbar("Backup failed")
                        }
                    }
                },
            )

            SettingsActionButton(
                icon = Icons.Default.Download,
                title = "Import Backup",
                subtitle = "Restore from a previous backup file",
                accentColor = Color(0xFF69F0AE),
                onClick = {
                    scope.launch {
                        isLoadingBackups = true
                        backupList = BackupService.listBackups(context)
                        isLoadingBackups = false
                        showImportDialog = true
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info section
            SettingsActionButton(
                icon = Icons.Default.Replay,
                title = "Replay Tutorials",
                subtitle = "Show the guided tour again on each screen",
                accentColor = Color(0xFFFFAB40),
                onClick = {
                    scope.launch {
                        com.orbix.pixora.launcher.ui.tutorial.TutorialManager.resetAll(context)
                        snackbarHostState.showSnackbar("Tutorials will show again next time")
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("About")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141420))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "Pixora Launcher",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your apps live inside the art",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // Settings tutorial (first time)
        var showSettingsTour by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!com.orbix.pixora.launcher.ui.tutorial.TutorialManager.isSettingsTourDone(context)) {
                kotlinx.coroutines.delay(600)
                showSettingsTour = true
            }
        }
        if (showSettingsTour) {
            com.orbix.pixora.launcher.ui.tutorial.CoachMarkOverlay(
                steps = com.orbix.pixora.launcher.ui.tutorial.TutorialSteps.settingsTour,
                onComplete = {
                    showSettingsTour = false
                    scope.launch {
                        com.orbix.pixora.launcher.ui.tutorial.TutorialManager.markSettingsTourDone(context)
                    }
                },
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1A1A2E),
                contentColor = Color.White,
            )
        }

        // Import backup dialog
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Backup") },
                text = {
                    if (isLoadingBackups) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color(0xFF7C4DFF))
                        }
                    } else if (backupList.isEmpty()) {
                        Text(
                            text = "No backups found in Downloads/Pixora",
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    } else {
                        Column {
                            Text(
                                text = "Select a backup to restore:",
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            backupList.forEach { filename ->
                                Text(
                                    text = filename,
                                    color = Color(0xFF69F0AE),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            showImportDialog = false
                                            scope.launch {
                                                val json = BackupService.readBackup(context, filename)
                                                if (json != null) {
                                                    val success = BackupService.importBackup(context, json)
                                                    snackbarHostState.showSnackbar(
                                                        if (success) "Backup restored — restart launcher to apply"
                                                        else "Import failed"
                                                    )
                                                } else {
                                                    snackbarHostState.showSnackbar("Could not read backup file")
                                                }
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
                },
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF7C4DFF),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color.White,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF7C4DFF),
                checkedTrackColor = Color(0xFF7C4DFF).copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun SettingsActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141420))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
            )
        }
    }
}

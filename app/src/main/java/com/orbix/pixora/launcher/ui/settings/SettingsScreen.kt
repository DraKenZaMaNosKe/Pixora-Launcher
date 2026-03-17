package com.orbix.pixora.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbix.pixora.launcher.audio.AudioCaptureService
import com.orbix.pixora.launcher.ui.EffectKeys
import com.orbix.pixora.launcher.ui.home.HomeViewModel

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

            // Info section
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

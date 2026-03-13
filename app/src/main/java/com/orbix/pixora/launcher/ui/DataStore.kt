package com.orbix.pixora.launcher.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.pixoraDataStore by preferencesDataStore(name = "pixora_prefs")

/** Effect visibility keys — default all true except ambient particles */
object EffectKeys {
    val TOUCH_GLOW = booleanPreferencesKey("effect_touch_glow")
    val EQUALIZER = booleanPreferencesKey("effect_equalizer")
    val BATTERY_RING = booleanPreferencesKey("effect_battery_ring")
    val SYSTEM_RINGS = booleanPreferencesKey("effect_system_rings")
    val AMBIENT_PARTICLES = booleanPreferencesKey("effect_ambient_particles")
}

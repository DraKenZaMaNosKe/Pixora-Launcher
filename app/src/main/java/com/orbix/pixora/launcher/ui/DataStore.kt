package com.orbix.pixora.launcher.ui

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.pixoraDataStore by preferencesDataStore(name = "pixora_prefs")

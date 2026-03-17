package com.orbix.pixora.launcher.ui.tutorial

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.orbix.pixora.launcher.ui.pixoraDataStore
import kotlinx.coroutines.flow.first

/**
 * Tracks which tutorials have been completed. Each tutorial is shown only once.
 */
object TutorialManager {

    private val KEY_HOME_TOUR = booleanPreferencesKey("tutorial_home_done")
    private val KEY_CATALOG_TOUR = booleanPreferencesKey("tutorial_catalog_done")
    private val KEY_EDIT_TOUR = booleanPreferencesKey("tutorial_edit_done")
    private val KEY_SETTINGS_TOUR = booleanPreferencesKey("tutorial_settings_done")

    suspend fun isHomeTourDone(context: Context): Boolean =
        context.pixoraDataStore.data.first()[KEY_HOME_TOUR] ?: false

    suspend fun isCatalogTourDone(context: Context): Boolean =
        context.pixoraDataStore.data.first()[KEY_CATALOG_TOUR] ?: false

    suspend fun isEditTourDone(context: Context): Boolean =
        context.pixoraDataStore.data.first()[KEY_EDIT_TOUR] ?: false

    suspend fun isSettingsTourDone(context: Context): Boolean =
        context.pixoraDataStore.data.first()[KEY_SETTINGS_TOUR] ?: false

    suspend fun markHomeTourDone(context: Context) {
        context.pixoraDataStore.edit { it[KEY_HOME_TOUR] = true }
    }

    suspend fun markCatalogTourDone(context: Context) {
        context.pixoraDataStore.edit { it[KEY_CATALOG_TOUR] = true }
    }

    suspend fun markEditTourDone(context: Context) {
        context.pixoraDataStore.edit { it[KEY_EDIT_TOUR] = true }
    }

    suspend fun markSettingsTourDone(context: Context) {
        context.pixoraDataStore.edit { it[KEY_SETTINGS_TOUR] = true }
    }

    /** Reset all tutorials (for testing or settings) */
    suspend fun resetAll(context: Context) {
        context.pixoraDataStore.edit {
            it.remove(KEY_HOME_TOUR)
            it.remove(KEY_CATALOG_TOUR)
            it.remove(KEY_EDIT_TOUR)
            it.remove(KEY_SETTINGS_TOUR)
        }
    }
}

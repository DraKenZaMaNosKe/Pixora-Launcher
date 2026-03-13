package com.orbix.pixora.launcher.ui.home

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.service.AppsRepository
import com.orbix.pixora.launcher.ui.pixoraDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepo = AppsRepository(application)
    private val dataStore = application.pixoraDataStore

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    // Background can be: "asset:icon_room_01" or "file:/path/to/image.webp"
    private val _backgroundUri = MutableStateFlow("asset:icon_room_01")
    val backgroundUri: StateFlow<String> = _backgroundUri

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen

    // Dock apps (bottom bar favorites)
    private val _dockApps = MutableStateFlow<List<String>>(emptyList())
    val dockApps: StateFlow<List<String>> = _dockApps

    companion object {
        val KEY_BACKGROUND = stringPreferencesKey("home_background")
        val KEY_DOCK_APPS = stringPreferencesKey("dock_apps")

        // Default dock apps by package name (common Android apps)
        val DEFAULT_DOCK = listOf(
            "com.android.dialer",          // Phone
            "com.samsung.android.dialer",   // Samsung Phone
            "com.google.android.dialer",    // Google Phone
            "com.whatsapp",                 // WhatsApp
            "com.android.mms",              // Messages
            "com.samsung.android.messaging", // Samsung Messages
            "com.google.android.apps.messaging", // Google Messages
            "com.android.chrome",           // Chrome
            "com.sec.android.app.camera",   // Samsung Camera
            "com.google.android.camera",    // Google Camera
        )
    }

    init {
        loadApps()
        loadBackground()
        loadDockApps()
    }

    private fun loadBackground() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_BACKGROUND] }.first()
            if (saved != null) {
                _backgroundUri.value = saved
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = appsRepo.getInstalledApps()
        }
    }

    fun launchApp(packageName: String) {
        appsRepo.launchApp(packageName)
    }

    fun setPage(page: Int) {
        _currentPage.value = page
    }

    /** Set an icon room asset as background */
    fun selectRoom(roomId: String) {
        val uri = "asset:$roomId"
        _backgroundUri.value = uri
        viewModelScope.launch {
            dataStore.edit { it[KEY_BACKGROUND] = uri }
        }
    }

    /** Set a downloaded file or asset URI as background.
     *  Accepts "asset:room_name", "file:/path", or just "/path" */
    fun setBackgroundFile(uriOrPath: String) {
        val uri = when {
            uriOrPath.startsWith("asset:") || uriOrPath.startsWith("file:") || uriOrPath.startsWith("pano:") -> uriOrPath
            else -> "file:$uriOrPath"
        }
        Log.d("PixoraHome", "setBackgroundFile: old=${_backgroundUri.value} new=$uri")
        _backgroundUri.value = uri
        viewModelScope.launch {
            dataStore.edit { it[KEY_BACKGROUND] = uri }
            Log.d("PixoraHome", "Background saved to DataStore: $uri")
        }
    }

    private fun loadDockApps() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_DOCK_APPS] }.first()
            if (saved != null) {
                val list: List<String> = Gson().fromJson(saved, object : TypeToken<List<String>>() {}.type)
                _dockApps.value = list
            } else {
                // Auto-detect default dock apps from installed apps
                viewModelScope.launch(Dispatchers.IO) {
                    val installed = appsRepo.getInstalledApps().map { it.packageName }.toSet()
                    val defaults = DEFAULT_DOCK.filter { it in installed }.take(5)
                    // Deduplicate (e.g. only one phone app)
                    val unique = mutableListOf<String>()
                    val categories = mutableSetOf<String>()
                    for (pkg in defaults) {
                        val cat = when {
                            pkg.contains("dialer") || pkg.contains("phone") -> "phone"
                            pkg.contains("whatsapp") -> "whatsapp"
                            pkg.contains("mms") || pkg.contains("messaging") -> "messages"
                            pkg.contains("chrome") -> "chrome"
                            pkg.contains("camera") -> "camera"
                            else -> pkg
                        }
                        if (cat !in categories) {
                            categories.add(cat)
                            unique.add(pkg)
                        }
                    }
                    _dockApps.value = unique
                    saveDockApps(unique)
                }
            }
        }
    }

    private fun saveDockApps(apps: List<String>) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_DOCK_APPS] = Gson().toJson(apps) }
        }
    }

    fun addDockApp(packageName: String) {
        val current = _dockApps.value.toMutableList()
        if (packageName !in current && current.size < 5) {
            current.add(packageName)
            _dockApps.value = current
            saveDockApps(current)
        }
    }

    fun removeDockApp(packageName: String) {
        val current = _dockApps.value.toMutableList()
        current.remove(packageName)
        _dockApps.value = current
        saveDockApps(current)
    }

    fun openDrawer() { _isDrawerOpen.value = true }
    fun closeDrawer() { _isDrawerOpen.value = false }
}

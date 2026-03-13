package com.orbix.pixora.launcher.ui.home

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.service.AppsRepository
import com.orbix.pixora.launcher.ui.EffectKeys
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

    private val _backgroundUri = MutableStateFlow("asset:icon_room_01")
    val backgroundUri: StateFlow<String> = _backgroundUri

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen

    private val _dockApps = MutableStateFlow<List<String>>(emptyList())
    val dockApps: StateFlow<List<String>> = _dockApps

    /**
     * Grid slots: nullable list where null = empty slot.
     * Always padded to a multiple of APPS_PER_PAGE so every page has exactly 16 cells.
     */
    private val _gridSlots = MutableStateFlow<List<String?>>(emptyList())
    val gridSlots: StateFlow<List<String?>> = _gridSlots

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    private val _showTouchGlow = MutableStateFlow(true)
    val showTouchGlow: StateFlow<Boolean> = _showTouchGlow
    private val _showEqualizer = MutableStateFlow(true)
    val showEqualizer: StateFlow<Boolean> = _showEqualizer
    private val _showBatteryRing = MutableStateFlow(true)
    val showBatteryRing: StateFlow<Boolean> = _showBatteryRing
    private val _showSystemRings = MutableStateFlow(true)
    val showSystemRings: StateFlow<Boolean> = _showSystemRings

    companion object {
        val KEY_BACKGROUND = stringPreferencesKey("home_background")
        val KEY_DOCK_APPS = stringPreferencesKey("dock_apps")
        val KEY_GRID_SLOTS = stringPreferencesKey("grid_slots")
        const val APPS_PER_PAGE = 16

        val DEFAULT_DOCK = listOf(
            "com.android.dialer", "com.samsung.android.dialer", "com.google.android.dialer",
            "com.whatsapp",
            "com.android.mms", "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "com.android.chrome",
            "com.sec.android.app.camera", "com.google.android.camera",
        )
    }

    init {
        loadApps()
        loadBackground()
        loadDockApps()
        loadEffects()
        loadGridSlots()
    }

    private fun loadBackground() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_BACKGROUND] }.first()
            if (saved != null) _backgroundUri.value = saved
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = appsRepo.getInstalledApps()
            refreshGrid()
        }
    }

    fun launchApp(packageName: String) { appsRepo.launchApp(packageName) }
    fun setPage(page: Int) { _currentPage.value = page }

    fun selectRoom(roomId: String) {
        val uri = "asset:$roomId"
        _backgroundUri.value = uri
        viewModelScope.launch { dataStore.edit { it[KEY_BACKGROUND] = uri } }
    }

    fun setBackgroundFile(uriOrPath: String) {
        val uri = when {
            uriOrPath.startsWith("asset:") || uriOrPath.startsWith("file:") || uriOrPath.startsWith("pano:") -> uriOrPath
            else -> "file:$uriOrPath"
        }
        _backgroundUri.value = uri
        viewModelScope.launch { dataStore.edit { it[KEY_BACKGROUND] = uri } }
    }

    // ── Grid Slots ──────────────────────────────────────────

    private var savedSlots: List<String?> = emptyList()

    private fun loadGridSlots() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_GRID_SLOTS] }.first()
            if (saved != null) {
                savedSlots = Gson().fromJson(saved, object : TypeToken<List<String?>>() {}.type)
            }
            refreshGrid()
        }
    }

    /** Build the grid from saved slots + installed apps, pad to full pages */
    private fun refreshGrid() {
        val installed = _installedApps.value.map { it.packageName }
        if (installed.isEmpty()) return
        val installedSet = installed.toSet()

        val slots: MutableList<String?>

        if (savedSlots.isEmpty()) {
            // No saved order — alphabetical
            slots = installed.toMutableList<String?>()
        } else {
            // Start from saved, clean uninstalled
            slots = savedSlots.map { pkg ->
                if (pkg != null && pkg in installedSet) pkg else null
            }.toMutableList()

            // Add new apps not in saved slots
            val inSlots = slots.filterNotNull().toSet()
            val newApps = installed.filter { it !in inSlots }
            for (app in newApps) {
                // Find first null slot
                val emptyIdx = slots.indexOfFirst { it == null }
                if (emptyIdx >= 0) {
                    slots[emptyIdx] = app
                } else {
                    slots.add(app)
                }
            }
        }

        // Pad to full pages (multiple of APPS_PER_PAGE)
        val totalPages = ((slots.size + APPS_PER_PAGE - 1) / APPS_PER_PAGE).coerceAtLeast(1)
        while (slots.size < totalPages * APPS_PER_PAGE) {
            slots.add(null)
        }

        Log.d("PixoraGrid", "refreshGrid: ${slots.filterNotNull().size} apps in ${totalPages} pages")
        _gridSlots.value = slots
    }

    private fun saveGrid(slots: List<String?>) {
        _gridSlots.value = slots
        savedSlots = slots
        viewModelScope.launch {
            dataStore.edit { it[KEY_GRID_SLOTS] = Gson().toJson(slots) }
        }
    }

    /**
     * Move an app from one global slot to another.
     * If target is empty → just move there.
     * If target has an app → swap them.
     */
    fun moveApp(fromGlobalIndex: Int, toGlobalIndex: Int) {
        val slots = _gridSlots.value.toMutableList()
        if (fromGlobalIndex < 0 || fromGlobalIndex >= slots.size) return
        if (toGlobalIndex < 0 || toGlobalIndex >= slots.size) return
        if (fromGlobalIndex == toGlobalIndex) return

        Log.d("PixoraGrid", "moveApp: $fromGlobalIndex → $toGlobalIndex (${slots[fromGlobalIndex]} ↔ ${slots[toGlobalIndex]})")

        // Swap (works for both empty and occupied targets)
        val temp = slots[fromGlobalIndex]
        slots[fromGlobalIndex] = slots[toGlobalIndex]
        slots[toGlobalIndex] = temp

        saveGrid(slots)
    }

    /** Move app to first empty slot on next page, return target page index */
    fun moveAppToNextPage(fromGlobalIndex: Int): Int {
        val slots = _gridSlots.value.toMutableList()
        if (fromGlobalIndex < 0 || fromGlobalIndex >= slots.size) return -1
        val pkg = slots[fromGlobalIndex] ?: return -1

        val currentPage = fromGlobalIndex / APPS_PER_PAGE
        val nextPageStart = (currentPage + 1) * APPS_PER_PAGE

        // Ensure next page exists
        while (slots.size < nextPageStart + APPS_PER_PAGE) slots.add(null)

        // Find first empty slot on next page
        var targetIdx = -1
        for (i in nextPageStart until nextPageStart + APPS_PER_PAGE) {
            if (slots[i] == null) { targetIdx = i; break }
        }
        if (targetIdx == -1) targetIdx = nextPageStart // Force first position if full

        Log.d("PixoraGrid", "moveToNextPage: $fromGlobalIndex → $targetIdx")
        slots[fromGlobalIndex] = null
        val displaced = slots[targetIdx]
        slots[targetIdx] = pkg
        if (displaced != null) slots[fromGlobalIndex] = displaced // swap if occupied

        saveGrid(slots)
        return currentPage + 1
    }

    /** Move app to first empty slot on previous page, return target page index */
    fun moveAppToPrevPage(fromGlobalIndex: Int): Int {
        val slots = _gridSlots.value.toMutableList()
        if (fromGlobalIndex < 0 || fromGlobalIndex >= slots.size) return -1
        val pkg = slots[fromGlobalIndex] ?: return -1

        val currentPage = fromGlobalIndex / APPS_PER_PAGE

        if (currentPage == 0) {
            // Already on first grid page — prepend a new empty page
            val newSlots = MutableList<String?>(APPS_PER_PAGE) { null }.apply { addAll(slots) }
            val newFromIndex = fromGlobalIndex + APPS_PER_PAGE
            newSlots[newFromIndex] = null
            // Place on first slot of the new first page
            newSlots[0] = pkg
            Log.d("PixoraGrid", "moveToPrevPage: prepended new page, $fromGlobalIndex → 0")
            saveGrid(newSlots)
            return 0
        }

        val prevPageStart = (currentPage - 1) * APPS_PER_PAGE

        var targetIdx = -1
        for (i in prevPageStart until prevPageStart + APPS_PER_PAGE) {
            if (slots[i] == null) { targetIdx = i; break }
        }
        if (targetIdx == -1) targetIdx = prevPageStart

        Log.d("PixoraGrid", "moveToPrevPage: $fromGlobalIndex → $targetIdx")
        slots[fromGlobalIndex] = null
        val displaced = slots[targetIdx]
        slots[targetIdx] = pkg
        if (displaced != null) slots[fromGlobalIndex] = displaced

        saveGrid(slots)
        return currentPage - 1
    }

    /** Reset to alphabetical order */
    fun resetAppOrder() {
        savedSlots = emptyList()
        viewModelScope.launch {
            dataStore.edit { it.remove(KEY_GRID_SLOTS) }
        }
        refreshGrid()
    }

    fun enterEditMode() { _isEditMode.value = true }
    fun exitEditMode() { _isEditMode.value = false }

    // ── Dock ──────────────────────────────────────────

    private fun loadDockApps() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_DOCK_APPS] }.first()
            if (saved != null) {
                val list: List<String> = Gson().fromJson(saved, object : TypeToken<List<String>>() {}.type)
                _dockApps.value = list
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    val installed = appsRepo.getInstalledApps().map { it.packageName }.toSet()
                    val defaults = DEFAULT_DOCK.filter { it in installed }.take(5)
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
                        if (cat !in categories) { categories.add(cat); unique.add(pkg) }
                    }
                    _dockApps.value = unique
                    saveDockApps(unique)
                }
            }
        }
    }

    private fun saveDockApps(apps: List<String>) {
        viewModelScope.launch { dataStore.edit { it[KEY_DOCK_APPS] = Gson().toJson(apps) } }
    }

    fun addDockApp(packageName: String) {
        val current = _dockApps.value.toMutableList()
        if (packageName !in current && current.size < 5) {
            current.add(packageName); _dockApps.value = current; saveDockApps(current)
        }
    }

    fun removeDockApp(packageName: String) {
        val current = _dockApps.value.toMutableList()
        current.remove(packageName); _dockApps.value = current; saveDockApps(current)
    }

    // ── Effects ──────────────────────────────────────────

    private fun loadEffects() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _showTouchGlow.value = prefs[EffectKeys.TOUCH_GLOW] ?: true
            _showEqualizer.value = prefs[EffectKeys.EQUALIZER] ?: true
            _showBatteryRing.value = prefs[EffectKeys.BATTERY_RING] ?: true
            _showSystemRings.value = prefs[EffectKeys.SYSTEM_RINGS] ?: true
        }
    }

    fun setEffect(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[key] = enabled }
            when (key) {
                EffectKeys.TOUCH_GLOW -> _showTouchGlow.value = enabled
                EffectKeys.EQUALIZER -> _showEqualizer.value = enabled
                EffectKeys.BATTERY_RING -> _showBatteryRing.value = enabled
                EffectKeys.SYSTEM_RINGS -> _showSystemRings.value = enabled
            }
        }
    }

    fun openDrawer() { _isDrawerOpen.value = true }
    fun closeDrawer() { _isDrawerOpen.value = false }
}

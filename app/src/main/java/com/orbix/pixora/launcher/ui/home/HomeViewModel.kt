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
    private val gson = Gson()

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
     * Saved PER wallpaper — each wallpaper has its own icon layout.
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
        val KEY_GRID_LAYOUTS = stringPreferencesKey("grid_layouts") // Map<wallpaperUri, slots>
        const val APPS_PER_PAGE = 16

        val DEFAULT_DOCK = listOf(
            "com.android.dialer", "com.samsung.android.dialer", "com.google.android.dialer",
            "com.whatsapp",
            "com.android.mms", "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "com.android.chrome",
            "com.sec.android.app.camera", "com.google.android.camera",
        )
    }

    /** In-memory cache of ALL wallpaper layouts */
    private var allLayouts: MutableMap<String, List<String?>> = mutableMapOf()

    init {
        loadApps()
        loadBackgroundThenGrid()
        loadDockApps()
        loadEffects()
    }

    /**
     * Load background first, then load all grid layouts and apply the current wallpaper's layout.
     * Order matters: we need to know which wallpaper is active before loading its grid.
     */
    private fun loadBackgroundThenGrid() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_BACKGROUND] }.first()
            if (saved != null) _backgroundUri.value = saved
            loadAllLayouts()
            applySavedSlotsForCurrentWallpaper()
        }
    }

    private suspend fun loadAllLayouts() {
        val json = dataStore.data.map { it[KEY_GRID_LAYOUTS] }.first()
        if (json != null) {
            val type = object : TypeToken<Map<String, List<String?>>>() {}.type
            allLayouts = gson.fromJson<Map<String, List<String?>>>(json, type).toMutableMap()
            Log.d("PixoraGrid", "Loaded ${allLayouts.size} wallpaper layouts")
        }
    }

    /** Apply the saved slots for the current wallpaper, or default to alphabetical */
    private fun applySavedSlotsForCurrentWallpaper() {
        val uri = _backgroundUri.value
        val saved = allLayouts[uri]?.toMutableList()
        // Clean empty leading/trailing pages from saved data
        if (saved != null) {
            while (saved.size > APPS_PER_PAGE && saved.subList(0, APPS_PER_PAGE).all { it == null }) {
                repeat(APPS_PER_PAGE) { saved.removeAt(0) }
            }
            while (saved.size > APPS_PER_PAGE && saved.subList(saved.size - APPS_PER_PAGE, saved.size).all { it == null }) {
                repeat(APPS_PER_PAGE) { saved.removeAt(saved.size - 1) }
            }
            // Save cleaned version back
            if (saved != allLayouts[uri]) {
                allLayouts[uri] = saved
                viewModelScope.launch {
                    dataStore.edit { it[KEY_GRID_LAYOUTS] = gson.toJson(allLayouts) }
                }
            }
        }
        savedSlots = saved ?: emptyList()
        Log.d("PixoraGrid", "Applying layout for '$uri': ${if (saved != null) "${saved.filterNotNull().size} apps" else "default alphabetical"}")
        refreshGrid()
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
        switchWallpaper(uri)
    }

    fun setBackgroundFile(uriOrPath: String) {
        val uri = when {
            uriOrPath.startsWith("asset:") || uriOrPath.startsWith("file:") || uriOrPath.startsWith("pano:") -> uriOrPath
            else -> "file:$uriOrPath"
        }
        switchWallpaper(uri)
    }

    /** Switch wallpaper and load its associated icon layout */
    private fun switchWallpaper(uri: String) {
        _backgroundUri.value = uri
        viewModelScope.launch {
            dataStore.edit { it[KEY_BACKGROUND] = uri }
            applySavedSlotsForCurrentWallpaper()
        }
    }

    // ── Grid Slots ──────────────────────────────────────────

    private var savedSlots: List<String?> = emptyList()

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
                val emptyIdx = slots.indexOfFirst { it == null }
                if (emptyIdx >= 0) {
                    slots[emptyIdx] = app
                } else {
                    slots.add(app)
                }
            }
        }

        // Remove leading empty pages (cleanup from old prepends)
        while (slots.size > APPS_PER_PAGE) {
            val firstPage = slots.subList(0, APPS_PER_PAGE)
            if (firstPage.all { it == null }) {
                repeat(APPS_PER_PAGE) { slots.removeAt(0) }
            } else break
        }

        // Remove trailing empty pages (keep at least 1 page)
        while (slots.size > APPS_PER_PAGE) {
            val lastPage = slots.subList(slots.size - APPS_PER_PAGE, slots.size)
            if (lastPage.all { it == null }) {
                repeat(APPS_PER_PAGE) { slots.removeAt(slots.size - 1) }
            } else break
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
        // Save associated with current wallpaper
        val uri = _backgroundUri.value
        allLayouts[uri] = slots
        viewModelScope.launch {
            dataStore.edit { it[KEY_GRID_LAYOUTS] = gson.toJson(allLayouts) }
        }
        Log.d("PixoraGrid", "Saved layout for '$uri'")
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

        while (slots.size < nextPageStart + APPS_PER_PAGE) slots.add(null)

        var targetIdx = -1
        for (i in nextPageStart until nextPageStart + APPS_PER_PAGE) {
            if (slots[i] == null) { targetIdx = i; break }
        }
        if (targetIdx == -1) targetIdx = nextPageStart

        Log.d("PixoraGrid", "moveToNextPage: $fromGlobalIndex → $targetIdx")
        slots[fromGlobalIndex] = null
        val displaced = slots[targetIdx]
        slots[targetIdx] = pkg
        if (displaced != null) slots[fromGlobalIndex] = displaced

        saveGrid(slots)
        return currentPage + 1
    }

    /** Move app to first empty slot on previous page, return target page index */
    fun moveAppToPrevPage(fromGlobalIndex: Int): Int {
        val slots = _gridSlots.value.toMutableList()
        if (fromGlobalIndex < 0 || fromGlobalIndex >= slots.size) return -1
        val pkg = slots[fromGlobalIndex] ?: return -1

        val currentPage = fromGlobalIndex / APPS_PER_PAGE

        if (currentPage == 0) return -1 // Already on first page, can't go further left

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

    /** Add an empty page after the given page index. Returns the new page index. */
    fun addPageAfter(pageIndex: Int): Int {
        val slots = _gridSlots.value.toMutableList()
        val totalPages = slots.size / APPS_PER_PAGE
        val safePage = pageIndex.coerceIn(0, totalPages - 1)
        val insertAt = ((safePage + 1) * APPS_PER_PAGE).coerceAtMost(slots.size)
        // Insert 16 null slots
        for (i in 0 until APPS_PER_PAGE) {
            slots.add(insertAt, null)
        }
        Log.d("PixoraGrid", "addPageAfter: inserted empty page after page $safePage (at index $insertAt)")
        saveGrid(slots)
        return safePage + 1
    }

    /**
     * Remove a page if it's completely empty.
     * Returns true if removed, false if page has icons.
     */
    fun removePage(pageIndex: Int): Boolean {
        val slots = _gridSlots.value.toMutableList()
        val pageStart = pageIndex * APPS_PER_PAGE
        val pageEnd = pageStart + APPS_PER_PAGE
        if (pageStart >= slots.size) return false

        // Check if page has any apps
        val pageSlots = slots.subList(pageStart, pageEnd.coerceAtMost(slots.size))
        if (pageSlots.any { it != null }) {
            Log.d("PixoraGrid", "removePage: page $pageIndex has apps, can't remove")
            return false
        }

        // Don't remove the last page
        val totalPages = slots.size / APPS_PER_PAGE
        if (totalPages <= 1) return false

        // Remove the 16 slots
        repeat(APPS_PER_PAGE) { slots.removeAt(pageStart) }
        Log.d("PixoraGrid", "removePage: removed empty page $pageIndex")
        saveGrid(slots)
        return true
    }

    /** Reset to alphabetical order (current wallpaper only) */
    fun resetAppOrder() {
        savedSlots = emptyList()
        val uri = _backgroundUri.value
        allLayouts.remove(uri)
        viewModelScope.launch {
            dataStore.edit { it[KEY_GRID_LAYOUTS] = gson.toJson(allLayouts) }
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
                val list: List<String> = gson.fromJson(saved, object : TypeToken<List<String>>() {}.type)
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
        viewModelScope.launch { dataStore.edit { it[KEY_DOCK_APPS] = gson.toJson(apps) } }
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

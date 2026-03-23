package com.orbix.pixora.launcher.ui.home

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.service.AppsRepository
import com.orbix.pixora.launcher.service.DayCycleManager
import com.orbix.pixora.launcher.service.StoryManager
import com.orbix.pixora.launcher.ui.EffectKeys
import com.orbix.pixora.launcher.ui.pixoraDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.withLock
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

    private val _isReloading = MutableStateFlow(false)
    val isReloading: StateFlow<Boolean> = _isReloading

    /** The page index that acts as "home" — launcher opens here */
    private val _homePage = MutableStateFlow(0)
    val homePage: StateFlow<Int> = _homePage

    private val _showTouchGlow = MutableStateFlow(true)
    val showTouchGlow: StateFlow<Boolean> = _showTouchGlow
    private val _showEqualizer = MutableStateFlow(true)
    val showEqualizer: StateFlow<Boolean> = _showEqualizer
    private val _showBatteryRing = MutableStateFlow(true)
    val showBatteryRing: StateFlow<Boolean> = _showBatteryRing
    private val _showSystemRings = MutableStateFlow(true)
    val showSystemRings: StateFlow<Boolean> = _showSystemRings
    private val _showAmbientParticles = MutableStateFlow(false)
    val showAmbientParticles: StateFlow<Boolean> = _showAmbientParticles

    private val _equalizerStyle = MutableStateFlow("CLASSIC")
    val equalizerStyle: StateFlow<String> = _equalizerStyle

    private val _recentApps = MutableStateFlow<List<String>>(emptyList())
    val recentApps: StateFlow<List<String>> = _recentApps

    companion object {
        val KEY_BACKGROUND = stringPreferencesKey("home_background")
        val KEY_DOCK_APPS = stringPreferencesKey("dock_apps")
        val KEY_GRID_LAYOUTS = stringPreferencesKey("grid_layouts") // Map<wallpaperUri, slots>
        val KEY_HOME_PAGES = stringPreferencesKey("home_pages") // Map<wallpaperUri, pageIndex>
        val KEY_RECENT_APPS = stringPreferencesKey("recent_apps")
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

    /** In-memory cache of home page per wallpaper */
    private var allHomePages: MutableMap<String, Int> = mutableMapOf()

    /** True once loadAllLayouts() has completed — guards against persisting empty data */
    @Volatile
    private var layoutsLoaded = false

    /** Serialize layout saves to avoid race conditions */
    private val layoutSaveMutex = kotlinx.coroutines.sync.Mutex()

    /** True once installed apps have been loaded at least once */
    @Volatile
    private var appsLoaded = false

    init {
        loadApps()
        loadBackgroundThenGrid()
        loadDockApps()
        loadEffects()
        loadRecentApps()
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
        val hpJson = dataStore.data.map { it[KEY_HOME_PAGES] }.first()
        if (hpJson != null) {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            allHomePages = gson.fromJson<Map<String, Int>>(hpJson, type).toMutableMap()
        }
        layoutsLoaded = true
    }

    /**
     * Get the layout key for the current wallpaper.
     * For stories, all frames share one layout keyed by "story:{id}".
     */
    private fun getLayoutKey(): String {
        val story = StoryManager.activeStory.value
        return if (story != null) "story:${story.id}" else _backgroundUri.value
    }

    /** Apply the saved slots for the current wallpaper, or default to alphabetical */
    private suspend fun applySavedSlotsForCurrentWallpaper() {
        // Wait for installed apps to be available before building the grid
        if (!appsLoaded) {
            Log.d("PixoraGrid", "applySavedSlots: waiting for apps to load...")
            while (!appsLoaded) kotlinx.coroutines.delay(50)
        }
        val uri = getLayoutKey()
        val saved = allLayouts[uri]?.toMutableList()
        // Clean empty leading/trailing pages from saved data
        if (saved != null) {
            while (saved.size > APPS_PER_PAGE && saved.subList(0, APPS_PER_PAGE).all { it == null }) {
                saved.subList(0, APPS_PER_PAGE).clear()
            }
            while (saved.size > APPS_PER_PAGE && saved.subList(saved.size - APPS_PER_PAGE, saved.size).all { it == null }) {
                saved.subList(saved.size - APPS_PER_PAGE, saved.size).clear()
            }
            // Save cleaned version back
            if (saved != allLayouts[uri]) {
                allLayouts[uri] = saved
                persistLayouts()
            }
        }
        hasSavedLayout = saved != null
        savedSlots = saved ?: emptyList()
        _homePage.value = allHomePages[uri] ?: 0
        Log.d("PixoraGrid", "Applying layout for '$uri': ${if (saved != null) "${saved.filterNotNull().size} apps" else "default alphabetical"}, homePage=${_homePage.value}")
        refreshGrid()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = appsRepo.getInstalledApps()
            appsLoaded = true
            // Only call refreshGrid if layouts are already loaded (avoid race with loadBackgroundThenGrid)
            if (layoutsLoaded) refreshGrid()
        }
    }

    fun launchApp(packageName: String) {
        appsRepo.launchApp(packageName)
        addToRecents(packageName)
    }
    fun setPage(page: Int) { _currentPage.value = page }

    fun selectRoom(roomId: String) {
        val uri = "asset:$roomId"
        deactivateStoryIfNeeded()
        deactivateDayCycleIfNeeded()
        switchWallpaper(uri)
    }

    fun setBackgroundFile(uriOrPath: String) {
        val uri = when {
            uriOrPath.startsWith("asset:") || uriOrPath.startsWith("file:") || uriOrPath.startsWith("pano:") -> uriOrPath
            else -> "file:$uriOrPath"
        }
        // If this is NOT coming from a story frame change, deactivate the story
        if (StoryManager.activeStory.value != null) {
            val storyPath = StoryManager.currentFramePath.value
            val isFromStory = storyPath != null && uri == "pano:$storyPath"
            if (!isFromStory) {
                deactivateStoryIfNeeded()
            }
        }
        // If this is NOT coming from day cycle, deactivate it
        if (DayCycleManager.activeTheme.value != null) {
            val dcPath = DayCycleManager.currentImagePath.value
            val isFromDayCycle = dcPath != null && uri == "pano:$dcPath"
            if (!isFromDayCycle) {
                deactivateDayCycleIfNeeded()
            }
        }
        switchWallpaper(uri)
    }

    private fun deactivateDayCycleIfNeeded() {
        if (DayCycleManager.activeTheme.value != null) {
            viewModelScope.launch {
                DayCycleManager.deactivate(getApplication())
                Log.d("PixoraGrid", "Day cycle deactivated — user changed wallpaper manually")
            }
        }
    }

    private fun deactivateStoryIfNeeded() {
        if (StoryManager.activeStory.value != null) {
            viewModelScope.launch {
                StoryManager.deactivate(getApplication())
                Log.d("PixoraGrid", "Story deactivated — user changed wallpaper manually")
            }
        }
    }

    /** Switch wallpaper and load its associated icon layout */
    private fun switchWallpaper(uri: String) {
        _backgroundUri.value = uri
        viewModelScope.launch {
            dataStore.edit { it[KEY_BACKGROUND] = uri }
            // Wait for layouts to be loaded before applying — avoids race condition on boot
            if (!layoutsLoaded) {
                Log.d("PixoraGrid", "switchWallpaper: waiting for layouts to load...")
                while (!layoutsLoaded) kotlinx.coroutines.delay(50)
            }
            applySavedSlotsForCurrentWallpaper()
        }
    }

    // ── Grid Slots ──────────────────────────────────────────

    private var savedSlots: List<String?> = emptyList()

    /** Whether the current wallpaper has an explicitly saved layout (even if empty) */
    private var hasSavedLayout: Boolean = false

    /** Build the grid from saved slots + installed apps, pad to full pages */
    private fun refreshGrid() {
        val installed = _installedApps.value.map { it.packageName }
        if (installed.isEmpty()) return
        val installedSet = installed.toSet()

        val slots: MutableList<String?>

        if (!hasSavedLayout && savedSlots.isEmpty()) {
            // No saved layout at all — default to alphabetical
            slots = installed.toMutableList<String?>()
        } else if (hasSavedLayout) {
            // Has an explicit saved layout — respect it (even if mostly empty)
            slots = savedSlots.map { pkg ->
                if (pkg != null && pkg in installedSet) pkg else null
            }.toMutableList()

            // Only auto-add apps that were NEWLY INSTALLED (not in any slot, even null ones)
            // Don't fill cleared slots — user intentionally cleared them
        } else {
            // savedSlots non-empty from a previous operation in this session
            slots = savedSlots.map { pkg ->
                if (pkg != null && pkg in installedSet) pkg else null
            }.toMutableList()

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
        hasSavedLayout = true
        // Save associated with current wallpaper (or story ID)
        val uri = getLayoutKey()
        allLayouts[uri] = slots
        persistLayouts(immediate = true)
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

    /** Move app to first empty slot on next page (or further). Return target page index. */
    fun moveAppToNextPage(fromGlobalIndex: Int): Int {
        val slots = _gridSlots.value.toMutableList()
        if (fromGlobalIndex < 0 || fromGlobalIndex >= slots.size) return -1
        val pkg = slots[fromGlobalIndex] ?: return -1

        val currentPage = fromGlobalIndex / APPS_PER_PAGE
        val searchStart = (currentPage + 1) * APPS_PER_PAGE

        // Search all pages after current for the first empty slot
        var targetIdx = -1
        for (i in searchStart until slots.size) {
            if (slots[i] == null) { targetIdx = i; break }
        }

        if (targetIdx == -1) {
            // No empty slot found — create a new page at the end
            val newPageStart = slots.size
            repeat(APPS_PER_PAGE) { slots.add(null) }
            targetIdx = newPageStart
        }

        val targetPage = targetIdx / APPS_PER_PAGE
        Log.d("PixoraGrid", "moveToNextPage: $fromGlobalIndex → $targetIdx (page $targetPage)")
        slots[fromGlobalIndex] = null
        slots[targetIdx] = pkg

        saveGrid(slots)
        return targetPage
    }

    /** Move app to first empty slot on previous page (or further back). Return target page index. */
    fun moveAppToPrevPage(fromGlobalIndex: Int): Int {
        val slots = _gridSlots.value.toMutableList()
        if (fromGlobalIndex < 0 || fromGlobalIndex >= slots.size) return -1
        val pkg = slots[fromGlobalIndex] ?: return -1

        val currentPage = fromGlobalIndex / APPS_PER_PAGE

        if (currentPage == 0) {
            // Prepend a new page at the beginning
            val newSlots = MutableList<String?>(APPS_PER_PAGE) { null }.apply { addAll(slots) }
            val newFromIndex = fromGlobalIndex + APPS_PER_PAGE
            newSlots[newFromIndex] = null
            newSlots[0] = pkg
            Log.d("PixoraGrid", "moveToPrevPage: prepended new page, $fromGlobalIndex → 0")
            saveGrid(newSlots)
            return 0
        }

        // Search backwards from end of previous page to slot 0 for first empty slot
        val prevPageEnd = currentPage * APPS_PER_PAGE - 1
        var targetIdx = -1
        for (i in prevPageEnd downTo 0) {
            if (slots[i] == null) { targetIdx = i; break }
        }

        if (targetIdx == -1) {
            // All previous pages are full — prepend a new page
            val newSlots = MutableList<String?>(APPS_PER_PAGE) { null }.apply { addAll(slots) }
            val newFromIndex = fromGlobalIndex + APPS_PER_PAGE
            newSlots[newFromIndex] = null
            newSlots[0] = pkg
            Log.d("PixoraGrid", "moveToPrevPage: all prev pages full, prepended new page, $fromGlobalIndex → 0")
            saveGrid(newSlots)
            return 0
        }

        val targetPage = targetIdx / APPS_PER_PAGE
        Log.d("PixoraGrid", "moveToPrevPage: $fromGlobalIndex → $targetIdx (page $targetPage)")
        slots[fromGlobalIndex] = null
        slots[targetIdx] = pkg

        saveGrid(slots)
        return targetPage
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

    /** Clear all icons from the home screen (current wallpaper) */
    fun clearAllPages() {
        savedSlots = List(APPS_PER_PAGE) { null }
        saveGrid(savedSlots)
        Log.d("PixoraGrid", "clearAllPages: cleared all icons")
    }

    /** Remove a specific app from the home grid */
    fun removeAppFromHome(packageName: String) {
        val slots = _gridSlots.value.toMutableList()
        val index = slots.indexOf(packageName)
        if (index >= 0) {
            slots[index] = null
            Log.d("PixoraGrid", "removeAppFromHome: removed $packageName from slot $index")
            saveGrid(slots)
        }
    }

    /** Add an app to the first available empty slot. Returns true if added. */
    fun addAppToHome(packageName: String): Boolean {
        val slots = _gridSlots.value.toMutableList()
        // Check if already on home
        if (packageName in slots) return false

        val emptyIdx = slots.indexOfFirst { it == null }
        if (emptyIdx >= 0) {
            slots[emptyIdx] = packageName
        } else {
            // No empty slot — add to end (new page will be created)
            slots.add(packageName)
            // Pad to full page
            while (slots.size % APPS_PER_PAGE != 0) slots.add(null)
        }
        Log.d("PixoraGrid", "addAppToHome: added $packageName")
        saveGrid(slots)
        return true
    }

    /** Check if an app is currently on the home screen */
    fun isAppOnHome(packageName: String): Boolean {
        return packageName in _gridSlots.value
    }

    /** Reset to alphabetical order (current wallpaper only) */
    private var persistJob: Job? = null

    /** Persist all layouts and home pages to DataStore (debounced + mutex-guarded) */
    private fun persistLayouts(immediate: Boolean = false) {
        // Guard: never persist if layouts haven't been loaded yet — would wipe all data
        if (!layoutsLoaded) {
            Log.w("PixoraGrid", "persistLayouts SKIPPED — layouts not loaded yet")
            return
        }
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (!immediate) kotlinx.coroutines.delay(500) // debounce 500ms
            layoutSaveMutex.withLock {
                dataStore.edit {
                    it[KEY_GRID_LAYOUTS] = gson.toJson(allLayouts)
                    it[KEY_HOME_PAGES] = gson.toJson(allHomePages)
                }
                Log.d("PixoraGrid", "Persisted ${allLayouts.size} layouts, ${allHomePages.size} home pages")
            }
        }
    }

    fun resetAppOrder() {
        savedSlots = emptyList()
        hasSavedLayout = false
        val uri = getLayoutKey()
        allLayouts.remove(uri)
        persistLayouts(immediate = true)
        refreshGrid()
    }

    /**
     * Generate a cinematic layout for stories: icons only in the bottom 2 rows (slots 8-15)
     * of each page, leaving the top area clear for panoramic art.
     */
    fun applyCinematicLayout() {
        val installed = _installedApps.value.map { it.packageName }
        if (installed.isEmpty()) return

        val slots = mutableListOf<String?>()
        var appIdx = 0

        // Fill pages: top 8 slots null (empty), bottom 8 slots with apps
        while (appIdx < installed.size) {
            // Top 2 rows empty (8 slots)
            repeat(APPS_PER_PAGE / 2) { slots.add(null) }
            // Bottom 2 rows with apps (8 slots)
            repeat(APPS_PER_PAGE / 2) {
                if (appIdx < installed.size) {
                    slots.add(installed[appIdx++])
                } else {
                    slots.add(null)
                }
            }
        }

        // Pad to full pages
        while (slots.size % APPS_PER_PAGE != 0) slots.add(null)

        Log.d("PixoraGrid", "Cinematic layout: ${installed.size} apps in ${slots.size / APPS_PER_PAGE} pages (bottom-half only)")
        saveGrid(slots)
    }

    fun enterEditMode() { _isEditMode.value = true }
    fun exitEditMode() { _isEditMode.value = false }

    /** Set a page as the home page (launcher opens here) */
    fun setHomePage(pageIndex: Int) {
        _homePage.value = pageIndex
        val uri = getLayoutKey()
        allHomePages[uri] = pageIndex
        persistLayouts(immediate = true)
        Log.d("PixoraGrid", "Set home page to $pageIndex for '$uri'")
    }

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
            _showAmbientParticles.value = prefs[EffectKeys.AMBIENT_PARTICLES] ?: false
            _equalizerStyle.value = prefs[EffectKeys.EQUALIZER_STYLE] ?: "CLASSIC"
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
                EffectKeys.AMBIENT_PARTICLES -> _showAmbientParticles.value = enabled
            }
        }
    }

    fun setEqualizerStyle(style: String) {
        _equalizerStyle.value = style
        viewModelScope.launch {
            dataStore.edit { it[EffectKeys.EQUALIZER_STYLE] = style }
        }
    }

    // ── Home button event ──────────────────────────────────
    private val _goHomeEvent = MutableStateFlow(0L)
    val goHomeEvent: StateFlow<Long> = _goHomeEvent

    /** Called when user presses system home button */
    fun triggerGoHome() {
        _isEditMode.value = false
        _isDrawerOpen.value = false
        _goHomeEvent.value = System.currentTimeMillis()
    }

    fun openDrawer() { _isDrawerOpen.value = true }
    fun closeDrawer() { _isDrawerOpen.value = false }

    /** Timestamp of last reload — prevents duplicate triggers from onNewIntent + onResume */
    private var lastReloadTime = 0L

    /**
     * Reload layouts and installed apps from disk.
     * Called on every Activity resume to ensure the grid is always up-to-date.
     *
     * Two modes:
     * - SKIP: Data already in memory (ViewModel survived) → nothing to do.
     * - FULL: Process was killed (grid empty, layouts not loaded) → full reload from
     *   DataStore with loading animation.
     */
    fun reloadFromDisk() {
        // Debounce: skip if called again within 2 seconds (onNewIntent + onResume fire together)
        val now = System.currentTimeMillis()
        if (now - lastReloadTime < 2000) {
            Log.d("PixoraGrid", "reloadFromDisk: debounced (duplicate trigger)")
            return
        }
        lastReloadTime = now

        // Data already in memory — ViewModel survived, nothing to reload
        if (layoutsLoaded && appsLoaded && _gridSlots.value.isNotEmpty()) {
            Log.d("PixoraGrid", "reloadFromDisk: skipped (data in memory)")
            return
        }

        // Full reload — process was killed, need everything from disk
        viewModelScope.launch {
            _isReloading.value = true

            // Wait for init's loadApps() if it's already running, instead of loading again
            if (!appsLoaded) {
                Log.d("PixoraGrid", "reloadFromDisk: waiting for apps from init...")
                while (!appsLoaded) kotlinx.coroutines.delay(50)
            }

            // Re-read layouts from DataStore (authoritative source)
            loadAllLayouts()

            // Re-read background URI
            val savedBg = dataStore.data.map { it[KEY_BACKGROUND] }.first()
            if (savedBg != null) _backgroundUri.value = savedBg

            // Reapply the correct layout for current wallpaper
            applySavedSlotsForCurrentWallpaper()
            Log.d("PixoraGrid", "reloadFromDisk: full reload complete")

            _isReloading.value = false
        }
    }

    // ── Recent Apps ──────────────────────────────────────────

    private fun loadRecentApps() {
        viewModelScope.launch {
            val saved = dataStore.data.map { it[KEY_RECENT_APPS] }.first()
            if (saved != null) {
                val list: List<String> = gson.fromJson(saved, object : TypeToken<List<String>>() {}.type)
                _recentApps.value = list
            }
        }
    }

    private fun addToRecents(packageName: String) {
        val current = _recentApps.value.toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        val trimmed = current.take(8)
        _recentApps.value = trimmed
        viewModelScope.launch {
            dataStore.edit { it[KEY_RECENT_APPS] = gson.toJson(trimmed) }
        }
    }
}

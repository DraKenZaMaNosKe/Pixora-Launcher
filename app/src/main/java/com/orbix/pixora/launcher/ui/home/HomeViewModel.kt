package com.orbix.pixora.launcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbix.pixora.launcher.data.models.AppInfo
import com.orbix.pixora.launcher.service.AppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appsRepo = AppsRepository(application)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    private val _selectedRoom = MutableStateFlow("icon_room_01")
    val selectedRoom: StateFlow<String> = _selectedRoom

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen

    init {
        loadApps()
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

    fun selectRoom(roomId: String) {
        _selectedRoom.value = roomId
    }

    fun toggleDrawer() {
        _isDrawerOpen.value = !_isDrawerOpen.value
    }

    fun openDrawer() {
        _isDrawerOpen.value = true
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
    }
}

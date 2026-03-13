package com.orbix.pixora.launcher.data.models

data class AppInfo(
    val packageName: String,
    val label: String,
    val iconBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int = packageName.hashCode()
}

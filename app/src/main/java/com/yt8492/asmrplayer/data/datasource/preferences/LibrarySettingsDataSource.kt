package com.yt8492.asmrplayer.data.datasource.preferences

import android.content.Context

internal class LibrarySettingsDataSource(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("library_settings", Context.MODE_PRIVATE)
    val isSetupComplete: Boolean
        get() = preferences.getBoolean("setup_complete", false)
    fun completeSetup() {
        preferences.edit().putBoolean("setup_complete", true).apply()
    }
}

package com.yt8492.asmrplayer.data.repository

import android.content.Context

class LibrarySettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    val isSetupComplete: Boolean
        get() = preferences.getBoolean("setup_complete", false)

    fun completeSetup() {
        preferences.edit().putBoolean("setup_complete", true).apply()
    }
}

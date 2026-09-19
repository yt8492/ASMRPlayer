package com.yt8492.asmrplayer.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class LibrarySettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    val isSetupComplete: Boolean
        get() = preferences.getBoolean("setup_complete", false)

    fun completeSetup() {
        preferences.edit().putBoolean("setup_complete", true).apply()
    }
}

internal fun audioReadPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_AUDIO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

internal fun Context.hasAudioReadPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, audioReadPermission()) == PackageManager.PERMISSION_GRANTED

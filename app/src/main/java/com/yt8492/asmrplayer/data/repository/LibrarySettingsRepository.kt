package com.yt8492.asmrplayer.data.repository

interface LibrarySettingsRepository {
    val isSetupComplete: Boolean
    fun completeSetup()
}

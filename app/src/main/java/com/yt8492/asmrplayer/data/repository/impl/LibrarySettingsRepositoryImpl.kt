package com.yt8492.asmrplayer.data.repository.impl

import com.yt8492.asmrplayer.data.datasource.preferences.LibrarySettingsDataSource
import com.yt8492.asmrplayer.data.repository.LibrarySettingsRepository

internal class LibrarySettingsRepositoryImpl(private val source: LibrarySettingsDataSource) : LibrarySettingsRepository {
    override val isSetupComplete: Boolean get() = source.isSetupComplete
    override fun completeSetup() = source.completeSetup()
}

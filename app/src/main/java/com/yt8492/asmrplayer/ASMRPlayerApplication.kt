package com.yt8492.asmrplayer

import android.app.Application
import com.yt8492.asmrplayer.logging.CrashlyticsTimberTree
import timber.log.Timber

class ASMRPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            Timber.plant(CrashlyticsTimberTree())
        } else {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("ASMRPlayerApplication started buildType=%s", BuildConfig.BUILD_TYPE)
    }
}

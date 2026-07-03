package com.yt8492.asmrplayer.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

class CrashlyticsTimberTree(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance(),
) : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.INFO
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) return
        crashlytics.log(buildLogMessage(priority, tag, message))
        if (priority >= Log.ERROR && t != null) {
            crashlytics.recordException(t)
        }
    }

    private fun buildLogMessage(priority: Int, tag: String?, message: String): String {
        val priorityLabel = when (priority) {
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "ASSERT"
            else -> priority.toString()
        }
        val tagPrefix = tag?.let { "[$it] " }.orEmpty()
        return "$priorityLabel $tagPrefix$message"
    }
}

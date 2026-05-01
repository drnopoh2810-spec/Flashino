package com.eduspecial.update

import android.content.Context

internal object UpdateDownloadTracker {
    private const val PREFS_NAME = "flashino_update_download"
    private const val KEY_DOWNLOAD_ID = "download_id"

    fun save(context: Context, downloadId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .apply()
    }

    @Synchronized
    fun consumeIfMatches(context: Context, downloadId: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) != downloadId) return false
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        return true
    }

    fun clearIfMatches(context: Context, downloadId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) == downloadId) {
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        }
    }
}

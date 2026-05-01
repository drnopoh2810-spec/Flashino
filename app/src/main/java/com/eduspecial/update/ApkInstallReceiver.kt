package com.eduspecial.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Receives ACTION_DOWNLOAD_COMPLETE from DownloadManager.
 * Retrieves the downloaded APK and triggers the system install prompt.
 */
class ApkInstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ApkInstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)

        if (!cursor.moveToFirst()) {
            cursor.close()
            return
        }

        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val status = cursor.getInt(statusCol)

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            if (!UpdateDownloadTracker.consumeIfMatches(context, downloadId)) {
                cursor.close()
                return
            }
            // On Android 10+ COLUMN_LOCAL_URI returns a content:// URI directly
            // On older versions it returns a file:// URI — we need the File path
            val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUriStr = cursor.getString(localUriCol)
            cursor.close()

            if (localUriStr != null) {
                triggerInstall(context, localUriStr)
            }
        } else {
            cursor.close()
            UpdateDownloadTracker.clearIfMatches(context, downloadId)
            Log.w(TAG, "Download not successful, status=$status")
        }
    }

    private fun triggerInstall(context: Context, localUriStr: String) {
        try {
            val installUri: Uri = when {
                // Android 10+ — DownloadManager gives us a content:// URI directly
                localUriStr.startsWith("content://") -> {
                    Uri.parse(localUriStr)
                }
                // Older Android — file:// URI, wrap with FileProvider for Android 7+
                else -> {
                    val file = File(Uri.parse(localUriStr).path ?: return)
                    if (!file.exists()) {
                        Log.e(TAG, "APK file not found: ${file.absolutePath}")
                        return
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.update.provider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                }
            }

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = installUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
            context.startActivity(installIntent)
            Log.d(TAG, "Install intent launched for: $installUri")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger install", e)
        }
    }
}

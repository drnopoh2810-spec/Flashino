package com.eduspecial.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateViewModel"
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Checks GitHub for a newer release.
     * - 404 (no releases yet) -> silently sets UpToDate
     * - Network error -> sets Error with a user-friendly message
     * - Newer version found -> sets UpdateAvailable
     */
    fun checkForUpdate() {
        if (_state.value is UpdateState.Checking) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UpdateState.Checking
            try {
                val release = repository.checkForUpdate()
                if (release == null) {
                    _state.value = UpdateState.UpToDate
                    return@launch
                }
                val asset = repository.findApkAsset(release)
                if (asset == null) {
                    // Release exists but has no APK asset attached yet
                    _state.value = UpdateState.UpToDate
                    Log.w(TAG, "Release ${release.tagName} has no APK asset")
                    return@launch
                }
                _state.value = UpdateState.UpdateAvailable(release, asset.downloadUrl)
            } catch (e: Exception) {
                // Do not show an error dialog for update checks - just log and stay Idle
                Log.w(TAG, "Update check failed: ${e.message}")
                _state.value = UpdateState.Idle
            }
        }
    }

    /**
     * Starts the APK download via DownloadManager.
     * Polls progress every 500ms and transitions to ReadyToInstall on completion.
     */
    fun startDownload(apkUrl: String, versionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = UpdateState.Downloading(0)

            val fileName = "Flashino-$versionName.apk"
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("تحديث فلاشينو")
                setDescription("جاري تنزيل الإصدار $versionName...")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    allowScanningByMediaScanner()
                }
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)
            Log.d(TAG, "Download enqueued: id=$downloadId, file=$fileName")

            pollDownloadProgress(dm, downloadId)
        }
    }

    private suspend fun pollDownloadProgress(dm: DownloadManager, downloadId: Long) {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                _state.value = UpdateState.Error("انقطع التنزيل. يرجى المحاولة مرة أخرى.")
                break
            }

            try {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                if (statusIdx == -1 || downloadedIdx == -1 || totalIdx == -1 || localUriIdx == -1 || reasonIdx == -1) {
                    _state.value = UpdateState.Error("خطأ في قراءة بيانات التنزيل.")
                    break
                }

                val status = cursor.getInt(statusIdx)
                val downloaded = cursor.getLong(downloadedIdx)
                val total = cursor.getLong(totalIdx)
                val localUri = cursor.getString(localUriIdx)
                val reason = cursor.getInt(reasonIdx)

                when (status) {
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING -> {
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        _state.value = UpdateState.Downloading(progress)
                        delay(500)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Log.d(TAG, "Download complete: $localUri")
                        _state.value = UpdateState.ReadyToInstall(localUri ?: "")
                        // Trigger install immediately - do not wait for BroadcastReceiver
                        localUri?.let { triggerInstallFromUri(it) }
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Log.e(TAG, "Download failed, reason=$reason")
                        _state.value = UpdateState.Error(
                            "فشل التنزيل (رمز الخطأ: $reason). يرجى المحاولة مرة أخرى."
                        )
                        break
                    }
                    else -> delay(500)
                }
            } finally {
                cursor.close()
            }
        }
    }

    /**
     * Triggers the system install prompt directly from the ViewModel.
     * This is a fallback in case the BroadcastReceiver fires late.
     */
    private fun triggerInstallFromUri(localUriStr: String) {
        try {
            val installUri = Uri.parse(localUriStr)
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = installUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger install from ViewModel", e)
        }
    }

    fun dismissUpdate() {
        _state.value = UpdateState.Idle
    }

    fun resetError() {
        _state.value = UpdateState.Idle
    }
}

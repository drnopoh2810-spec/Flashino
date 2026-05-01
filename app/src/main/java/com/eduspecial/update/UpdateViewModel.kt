package com.eduspecial.update

import android.app.DownloadManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParseException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    fun checkForUpdate(autoInstall: Boolean = true, notifyErrors: Boolean = false) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
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
                    _state.value = UpdateState.UpToDate
                    Log.w(TAG, "Release ${release.tagName} has no APK asset")
                    return@launch
                }

                if (!autoInstall) {
                    _state.value = UpdateState.UpdateAvailable(release, asset.downloadUrl)
                    return@launch
                }

                if (needsInstallPermission()) {
                    _state.value = UpdateState.PermissionRequired(release, asset.downloadUrl)
                    return@launch
                }

                downloadAndInstall(asset.downloadUrl, release.tagName.trimStart('v'))
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                _state.value = if (notifyErrors) {
                    UpdateState.Error(e.toUpdateCheckMessage())
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    fun resumePendingUpdate() {
        val pending = _state.value as? UpdateState.PermissionRequired ?: return
        if (needsInstallPermission()) return
        startDownload(pending.apkUrl, pending.release.tagName.trimStart('v'))
    }

    fun startDownload(apkUrl: String, versionName: String) {
        if (needsInstallPermission()) {
            _state.value = UpdateState.Error("يجب السماح للتطبيق بتثبيت التحديثات أولا.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            downloadAndInstall(apkUrl, versionName)
        }
    }

    private fun needsInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    private fun Exception.toUpdateCheckMessage(): String {
        val httpError = findCause(GitHubUpdateHttpException::class.java)
        return when {
            !hasValidatedInternet() ->
                "الهاتف متصل بشبكة، لكن Android لا يؤكد وجود إنترنت فعلي الآن. جرّب فتح أي موقع ثم أعد المحاولة."
            findCause(UnknownHostException::class.java) != null ->
                "تعذر الوصول إلى GitHub من هذه الشبكة. جرّب شبكة Wi‑Fi أو بيانات مختلفة ثم أعد المحاولة."
            findCause(SocketTimeoutException::class.java) != null ->
                "انتهت مهلة الاتصال بـ GitHub. الاتصال موجود لكنه بطيء أو يمنع GitHub مؤقتًا."
            findCause(ConnectException::class.java) != null ->
                "تعذر فتح اتصال مع GitHub من هذه الشبكة. جرّب شبكة مختلفة أو VPN موثوق."
            findCause(SSLException::class.java) != null ->
                "فشل الاتصال الآمن مع GitHub. تأكد من ضبط التاريخ والوقت أو جرّب شبكة مختلفة."
            findCause(JsonParseException::class.java) != null ->
                "وصل رد من GitHub لكن التطبيق لم يستطع قراءة بيانات التحديث. ثبّت آخر إصدار يدويًا مرة واحدة."
            httpError?.statusCode == 403 || httpError?.statusCode == 429 ->
                "GitHub رفض طلب التحديث مؤقتًا بسبب كثرة الطلبات أو قيود الشبكة. حاول مرة أخرى بعد قليل."
            httpError != null && httpError.statusCode >= 500 ->
                "خوادم GitHub لا ترد بشكل صحيح الآن. حاول مرة أخرى بعد قليل."
            httpError != null ->
                "تعذر الوصول إلى صفحة التحديثات على GitHub. رمز الاستجابة: ${httpError.statusCode}."
            else ->
                "تعذر التحقق من التحديثات عبر GitHub الآن (${rootCauseName()}). حاول مرة أخرى أو ثبّت آخر إصدار يدويًا."
        }
    }

    private fun hasValidatedInternet(): Boolean = runCatching {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return@runCatching false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)

    private fun <T : Throwable> Throwable.findCause(type: Class<T>): T? {
        var current: Throwable? = this
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current)
            current = current.cause
        }
        return null
    }

    private fun Throwable.rootCauseName(): String {
        var current: Throwable = this
        while (current.cause != null) {
            current = current.cause!!
        }
        return current.javaClass.simpleName.ifBlank { "خطأ غير معروف" }
    }

    private suspend fun downloadAndInstall(apkUrl: String, versionName: String) {
        _state.value = UpdateState.Downloading(0)

        val fileName = "Flashino-$versionName.apk"
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(fileName)
            ?.takeIf { it.exists() }
            ?.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("تحديث Flashino")
            setDescription("جاري تنزيل الإصدار $versionName...")
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
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
        UpdateDownloadTracker.save(context, downloadId)
        Log.d(TAG, "Download enqueued: id=$downloadId, file=$fileName")

        pollDownloadProgress(dm, downloadId)
    }

    private suspend fun pollDownloadProgress(dm: DownloadManager, downloadId: Long) {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                UpdateDownloadTracker.clearIfMatches(context, downloadId)
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
                    UpdateDownloadTracker.clearIfMatches(context, downloadId)
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
                        if (localUri != null && UpdateDownloadTracker.consumeIfMatches(context, downloadId)) {
                            triggerInstallFromUri(localUri)
                        }
                        _state.value = UpdateState.ReadyToInstall(localUri.orEmpty())
                        break
                    }

                    DownloadManager.STATUS_FAILED -> {
                        Log.e(TAG, "Download failed, reason=$reason")
                        UpdateDownloadTracker.clearIfMatches(context, downloadId)
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

    private fun triggerInstallFromUri(localUriStr: String) {
        try {
            val installUri = resolveInstallUri(localUriStr) ?: return
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = installUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger install from ViewModel", e)
            _state.value = UpdateState.Error("تم تنزيل التحديث لكن تعذر فتح شاشة التثبيت.")
        }
    }

    private fun resolveInstallUri(localUriStr: String): Uri? {
        val uri = Uri.parse(localUriStr)
        if (uri.scheme == "content") return uri

        val file = when (uri.scheme) {
            "file" -> File(uri.path ?: return null)
            else -> File(localUriStr)
        }
        if (!file.exists()) {
            Log.e(TAG, "APK file not found: ${file.absolutePath}")
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.update.provider", file)
        } else {
            Uri.fromFile(file)
        }
    }

    fun dismissUpdate() {
        _state.value = UpdateState.Idle
    }

    fun resetError() {
        _state.value = UpdateState.Idle
    }
}

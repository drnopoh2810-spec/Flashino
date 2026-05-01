package com.eduspecial.data.remote.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.eduspecial.data.repository.ConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class CloudinaryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository
) {
    companion object {
        private const val TAG = "CloudinaryService"
        private const val EDUSPECIAL_FOLDER = "eduspecial"
        private const val MAX_AVATAR_SIZE = 512
        private const val AVATAR_QUALITY = 82
    }

    private var initializedCloudName: String? = null

    fun initialize() {
        val primary = getAccountsFromConfig().firstOrNull() ?: return
        initializeWithAccount(primary)
    }

    suspend fun uploadMedia(
        uri: Uri,
        resourceType: String = "auto",
        folder: String = EDUSPECIAL_FOLDER,
        publicId: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult {
        val accounts = getAccountsFromConfig()
        if (accounts.isEmpty()) return UploadResult.Failure("No Cloudinary accounts configured")

        var lastError = "Upload failed"
        for (account in accounts) {
            Log.d(TAG, "Trying upload with account: ${account.cloudName}")
            initializeWithAccount(account)
            val result = tryUpload(uri, resourceType, folder, publicId, account, onProgress)
            when (result) {
                is UploadResult.Success -> {
                    account.markHealthy()
                    return result
                }

                is UploadResult.Failure -> {
                    account.markFailed()
                    lastError = result.error
                    if (
                        result.error.contains("quota", ignoreCase = true) ||
                        result.error.contains("storage", ignoreCase = true) ||
                        result.error.contains("limit", ignoreCase = true)
                    ) {
                        account.markQuotaExceeded()
                    }
                }
            }
        }

        return UploadResult.Failure("All accounts failed. Last error: $lastError")
    }

    suspend fun uploadAvatar(
        uri: Uri,
        folder: String = "avatars",
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult {
        Log.d(TAG, "Preparing avatar upload from uri=$uri")
        val preparedUpload = prepareAvatarUpload(uri)
        return try {
            val result = uploadMedia(
                uri = preparedUpload.uri,
                resourceType = "image",
                folder = folder,
                onProgress = onProgress
            )
            Log.d(TAG, "Avatar upload result=$result")
            result
        } finally {
            preparedUpload.cleanup()
        }
    }

    fun getOptimizedImageUrl(publicId: String, width: Int = 800, height: Int? = null): String {
        val accounts = getAccountsFromConfig()
        val cloudName = initializedCloudName ?: accounts.firstOrNull()?.cloudName ?: ""
        val heightPart = if (height != null) ",h_$height" else ""
        return "https://res.cloudinary.com/$cloudName/image/upload/f_auto,q_auto,w_$width$heightPart/$publicId"
    }

    fun getHlsStreamUrl(publicId: String): String {
        val cloudName = initializedCloudName ?: ""
        return "https://res.cloudinary.com/$cloudName/video/upload/sp_hd/$publicId.m3u8"
    }

    fun getVideoThumbnailUrl(publicId: String, timeOffset: Float = 0f): String {
        val cloudName = initializedCloudName ?: ""
        return "https://res.cloudinary.com/$cloudName/video/upload/f_auto,q_auto,so_${timeOffset}/$publicId.jpg"
    }

    fun getAudioUrl(publicId: String): String {
        val cloudName = initializedCloudName ?: getAccountsFromConfig().firstOrNull()?.cloudName.orEmpty()
        return "https://res.cloudinary.com/$cloudName/video/upload/$publicId.mp3"
    }

    suspend fun findAudioUrl(publicId: String): String? = withContext(Dispatchers.IO) {
        getAccountsFromConfig().forEach { account ->
            val url = "https://res.cloudinary.com/${account.cloudName}/video/upload/$publicId.mp3"
            val exists = runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    requestMethod = "HEAD"
                    instanceFollowRedirects = true
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    connect()
                    try {
                        responseCode in 200..299
                    } finally {
                        disconnect()
                    }
                }
            }.getOrDefault(false)
            if (exists) {
                initializedCloudName = account.cloudName
                return@withContext url
            }
        }
        null
    }

    suspend fun audioExists(publicId: String): Boolean = findAudioUrl(publicId) != null

    fun getPlayableUrl(uploadResult: UploadResult.Success): String {
        return if (uploadResult.resourceType.equals("video", ignoreCase = true)) {
            getAudioUrl(uploadResult.publicId)
        } else {
            uploadResult.url
        }
    }

    private fun getAccountsFromConfig(): List<CloudinaryAccount> {
        return configRepository.getCloudinaryConfigs().mapIndexed { index, config ->
            CloudinaryAccount(
                cloudName = config.cloudName,
                uploadPreset = config.uploadPreset,
                priority = index + 1
            )
        }
    }

    private fun initializeWithAccount(account: CloudinaryAccount) {
        if (initializedCloudName == account.cloudName) return
        val config = mapOf("cloud_name" to account.cloudName, "secure" to true)
        try {
            MediaManager.init(context, config)
        } catch (_: IllegalStateException) {
            Log.w(TAG, "MediaManager already initialized, using existing instance")
        } catch (error: Exception) {
            Log.w(TAG, "Init error: ${error.message}")
        }
        initializedCloudName = account.cloudName
    }

    private suspend fun tryUpload(
        uri: Uri,
        resourceType: String,
        folder: String,
        publicId: String?,
        account: CloudinaryAccount,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult = suspendCancellableCoroutine { continuation ->
        val requestBuilder = MediaManager.get()
            .upload(uri)
            .unsigned(account.uploadPreset)
            .option("folder", folder)
            .option("resource_type", resourceType)
            .option("quality", "auto")
            .option("fetch_format", "auto")
            .option("flags", "progressive")

        if (!publicId.isNullOrBlank()) {
            requestBuilder.option("public_id", publicId)
        }

        val requestId = requestBuilder.callback(object : UploadCallback {
                override fun onStart(requestId: String?) = Unit

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                    if (totalBytes > 0) {
                        val percent = ((bytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                        onProgress?.invoke(percent)
                    }
                }

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String ?: ""
                    val publicId = resultData?.get("public_id") as? String ?: ""
                    val resType = resultData?.get("resource_type") as? String ?: "image"
                    if (!continuation.isCompleted) {
                        continuation.resume(UploadResult.Success(url, publicId, resType))
                    }
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    if (!continuation.isCompleted) {
                        continuation.resume(UploadResult.Failure(error?.description ?: "Upload failed"))
                    }
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    if (!continuation.isCompleted) {
                        continuation.resume(UploadResult.Failure("Rescheduled: ${error?.description}"))
                    }
                }
            })
            .dispatch()

        continuation.invokeOnCancellation {
            try {
                MediaManager.get().cancelRequest(requestId)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun prepareAvatarUpload(sourceUri: Uri): PreparedUpload = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decodeBitmapBounds(sourceUri, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("ملف الصورة غير صالح")
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_AVATAR_SIZE * 2)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = decodeBitmap(sourceUri, decodeOptions)
            ?: throw IllegalArgumentException("تعذر معالجة الصورة المحددة")

        val squareSize = min(decodedBitmap.width, decodedBitmap.height)
        val left = (decodedBitmap.width - squareSize) / 2
        val top = (decodedBitmap.height - squareSize) / 2
        val croppedBitmap = Bitmap.createBitmap(decodedBitmap, left, top, squareSize, squareSize)
        val targetSize = squareSize.coerceAtMost(MAX_AVATAR_SIZE)
        val finalBitmap = if (croppedBitmap.width != targetSize) {
            Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true)
        } else {
            croppedBitmap
        }

        if (decodedBitmap !== croppedBitmap) {
            decodedBitmap.recycle()
        }
        if (croppedBitmap !== finalBitmap) {
            croppedBitmap.recycle()
        }

        val tempFile = File.createTempFile("avatar_upload_", ".jpg", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { output ->
                if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, output)) {
                    throw IllegalStateException("تعذر تجهيز الصورة للرفع")
                }
            }
            Log.d(
                TAG,
                "Prepared avatar file path=${tempFile.absolutePath}, size=${tempFile.length()} bytes"
            )
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        } finally {
            finalBitmap.recycle()
        }

        PreparedUpload(
            uri = Uri.fromFile(tempFile),
            cleanup = { tempFile.delete() }
        )
    }

    private fun decodeBitmapBounds(sourceUri: Uri, options: BitmapFactory.Options) {
        val resolver = context.contentResolver
        val decoded = resolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
            true
        } ?: resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
            true
        }

        if (decoded != true) {
            throw IllegalArgumentException("تعذر قراءة الصورة المحددة")
        }
    }

    private fun decodeBitmap(sourceUri: Uri, options: BitmapFactory.Options): Bitmap? {
        val resolver = context.contentResolver
        return resolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
        } ?: resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimension || currentHeight > maxDimension) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}

data class CloudinaryAccount(
    val cloudName: String,
    val uploadPreset: String,
    val priority: Int = 0
) {
    @Volatile
    var isQuotaExceeded: Boolean = false
        private set

    @Volatile
    var consecutiveFailures: Int = 0
        private set

    fun markHealthy() {
        consecutiveFailures = 0
    }

    fun markFailed() {
        consecutiveFailures++
    }

    fun markQuotaExceeded() {
        isQuotaExceeded = true
    }

    val isAvailable: Boolean
        get() = !isQuotaExceeded && consecutiveFailures < 3
}

@Singleton
class CloudinaryAccountRegistry @Inject constructor(
    private val configRepository: ConfigRepository
) {
    fun getAllAccounts(): List<CloudinaryAccount> {
        return configRepository.getCloudinaryConfigs().mapIndexed { index, config ->
            CloudinaryAccount(
                cloudName = config.cloudName,
                uploadPreset = config.uploadPreset,
                priority = index + 1
            )
        }
    }

    fun getNextAvailable(): CloudinaryAccount? {
        return getAllAccounts()
            .filter { it.isAvailable }
            .minByOrNull { it.consecutiveFailures }
    }

    fun getStatus(): String {
        return getAllAccounts().joinToString("\n") { account ->
            val status = when {
                account.isQuotaExceeded -> "QUOTA FULL"
                account.isAvailable -> "OK"
                else -> "FAILING (${account.consecutiveFailures} errors)"
            }
            "• ${account.cloudName}: $status"
        }
    }
}

sealed class UploadResult {
    data class Success(val url: String, val publicId: String, val resourceType: String) : UploadResult()
    data class Failure(val error: String) : UploadResult()
}

private data class PreparedUpload(
    val uri: Uri,
    val cleanup: () -> Unit
)

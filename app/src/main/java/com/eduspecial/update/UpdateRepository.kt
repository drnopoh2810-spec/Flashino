package com.eduspecial.update

import com.eduspecial.BuildConfig
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class UpdateRepository @Inject constructor(
    private val service: GitHubUpdateService
) {
    companion object {
        const val GITHUB_OWNER = "drnopoh2810-spec"
        const val GITHUB_REPO = "Flashino"
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/drnopoh2810-spec/Flashino/releases/latest/download/flashino-update.json"
        private const val GITHUB_RELEASE_API_URL =
            "https://api.github.com/repos/drnopoh2810-spec/Flashino/releases/latest"
        private const val BACKEND_UPDATE_PATH = "/v1/app/update"
    }

    suspend fun checkForUpdate(): GitHubRelease? {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return fetchLatestRelease()
            } catch (e: Exception) {
                lastError = e
                if (!e.isRetryableUpdateError() || attempt == 2) throw e
                delay(700L * (attempt + 1))
            }
        }
        throw lastError ?: Exception("Unknown update check error")
    }

    private suspend fun fetchLatestRelease(): GitHubRelease? {
        updateBackendUrl()?.let { url ->
            runCatching { fetchLatestManifestRelease(url) }
                .onSuccess { return it }
        }

        runCatching { fetchLatestManifestRelease(UPDATE_MANIFEST_URL) }
            .onSuccess { return it }

        return fetchLatestGitHubApiRelease()
    }

    private suspend fun fetchLatestGitHubApiRelease(): GitHubRelease? {
        val response = service.getJson(GITHUB_RELEASE_API_URL)
        if (response.code() == 404) return null
        if (!response.isSuccessful) throw GitHubUpdateHttpException(response.code())

        val raw = response.body()?.string()?.trim().orEmpty()
        if (raw.isBlank()) return null
        val release = parseGitHubRelease(raw)
        if (release.draft || release.prerelease) return null

        val latestVersion = release.tagName.trimStart('v')
        val currentVersion = BuildConfig.VERSION_NAME
        return if (isNewerVersion(latestVersion, currentVersion)) release else null
    }

    private suspend fun fetchLatestManifestRelease(url: String): GitHubRelease? {
        val response = service.getJson(url)
        if (response.code() == 404) throw GitHubUpdateHttpException(404)
        if (!response.isSuccessful) throw GitHubUpdateHttpException(response.code())

        val raw = response.body()?.string()?.trim().orEmpty()
        if (raw.isBlank()) return null
        val manifest = parseUpdateManifest(raw)
        val latestVersion = manifest.versionName
        if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) return null

        val tagName = manifest.tagName?.takeIf { it.isNotBlank() } ?: "v$latestVersion"
        val apkName = manifest.apkName?.takeIf { it.isNotBlank() } ?: "Flashino-v$latestVersion-release.apk"
        return GitHubRelease(
            tagName = tagName,
            name = "Flashino $tagName",
            body = null,
            assets = listOf(
                GitHubAsset(
                    name = apkName,
                    downloadUrl = manifest.apkUrl,
                    contentType = "application/vnd.android.package-archive",
                    size = manifest.apkSize ?: 0L
                )
            ),
            prerelease = false,
            draft = false
        )
    }

    private fun updateBackendUrl(): String? {
        val baseUrl = BuildConfig.AUDIO_BACKEND_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) return null
        return "$baseUrl$BACKEND_UPDATE_PATH"
    }

    private fun parseUpdateManifest(raw: String): UpdateManifest {
        val json = JsonParser.parseString(raw).asJsonObject
        return UpdateManifest(
            tagName = json.stringOrNull("tag_name"),
            versionName = json.requiredString("version_name"),
            versionCode = json.intOrNull("version_code"),
            apkName = json.stringOrNull("apk_name"),
            apkUrl = json.requiredString("apk_url"),
            apkSize = json.longOrNull("apk_size")
        )
    }

    private fun parseGitHubRelease(raw: String): GitHubRelease {
        val json = JsonParser.parseString(raw).asJsonObject
        val tagName = json.requiredString("tag_name")
        val assets = json.arrayOrEmpty("assets")
            .mapNotNull { it.asObjectOrNull() }
            .map { asset ->
                GitHubAsset(
                    name = asset.requiredString("name"),
                    downloadUrl = asset.requiredString("browser_download_url"),
                    contentType = asset.stringOrNull("content_type").orEmpty(),
                    size = asset.longOrNull("size") ?: 0L
                )
            }

        return GitHubRelease(
            tagName = tagName,
            name = json.stringOrNull("name") ?: "Flashino $tagName",
            body = json.stringOrNull("body"),
            assets = assets,
            prerelease = json.booleanOrFalse("prerelease"),
            draft = json.booleanOrFalse("draft")
        )
    }

    fun findApkAsset(release: GitHubRelease): GitHubAsset? =
        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}

class GitHubUpdateHttpException(val statusCode: Int) : Exception("GitHub API error: HTTP $statusCode")

private fun Exception.isRetryableUpdateError(): Boolean =
    this !is GitHubUpdateHttpException || statusCode == 408 || statusCode == 429 || statusCode >= 500

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name) ?: throw IllegalArgumentException("Missing update field: $name")

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

private fun JsonObject.intOrNull(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.asInt

private fun JsonObject.longOrNull(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.asLong

private fun JsonObject.booleanOrFalse(name: String): Boolean =
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false

private fun JsonObject.arrayOrEmpty(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

private fun JsonElement.asObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

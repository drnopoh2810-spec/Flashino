package com.eduspecial.update

import com.eduspecial.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class UpdateRepository @Inject constructor(
    private val service: GitHubUpdateService
) {
    companion object {
        const val GITHUB_OWNER = "drnopoh2810-spec"
        const val GITHUB_REPO  = "Flashino"
        private const val UPDATE_MANIFEST_URL =
            "https://github.com/drnopoh2810-spec/Flashino/releases/latest/download/flashino-update.json"
    }

    /**
     * Fetches the latest release from GitHub and compares with the running version.
     *
     * Returns:
     *  - [GitHubRelease] if a newer version is available
     *  - null if already up-to-date OR no releases exist yet (404)
     *
     * Throws only on genuine network/server errors (5xx, timeout, etc.)
     */
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
        runCatching { fetchLatestManifestRelease() }
            .getOrNull()
            ?.let { return it }

        val response = service.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)

        // 404 = repo has no published releases yet → treat as up-to-date silently
        if (response.code() == 404) return null

        // Any other non-success code is a real error
        if (!response.isSuccessful) {
            throw GitHubUpdateHttpException(response.code())
        }

        val release = response.body() ?: return null

        // Skip drafts and pre-releases
        if (release.draft || release.prerelease) return null

        val latestVersion  = release.tagName.trimStart('v')
        val currentVersion = BuildConfig.VERSION_NAME

        return if (isNewerVersion(latestVersion, currentVersion)) release else null
    }

    private suspend fun fetchLatestManifestRelease(): GitHubRelease? {
        val response = service.getLatestManifest(UPDATE_MANIFEST_URL)
        if (response.code() == 404) return null
        if (!response.isSuccessful) {
            throw GitHubUpdateHttpException(response.code())
        }

        val manifest = response.body() ?: return null
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

    /** Finds the first APK asset in a release */
    fun findApkAsset(release: GitHubRelease): GitHubAsset? =
        release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    /**
     * Semantic version comparison — returns true if [remote] > [local].
     * Handles "1.2.3" and "1.2.3-beta" formats.
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
        val localParts  = local.split("-").first().split(".").mapNotNull { it.toIntOrNull() }
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

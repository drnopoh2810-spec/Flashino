package com.eduspecial.update

import com.google.gson.annotations.SerializedName

/** GitHub Releases API response model */
data class GitHubRelease(
    @SerializedName("tag_name")   val tagName: String,
    @SerializedName("name")       val name: String,
    @SerializedName("body")       val body: String?,
    @SerializedName("assets")     val assets: List<GitHubAsset>,
    @SerializedName("prerelease") val prerelease: Boolean,
    @SerializedName("draft")      val draft: Boolean
)

data class GitHubAsset(
    @SerializedName("name")                  val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type")         val contentType: String,
    @SerializedName("size")                 val size: Long
)

data class UpdateManifest(
    @SerializedName("tag_name")     val tagName: String?,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("version_code") val versionCode: Int?,
    @SerializedName("apk_name")     val apkName: String?,
    @SerializedName("apk_url")      val apkUrl: String,
    @SerializedName("apk_size")     val apkSize: Long?
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val release: GitHubRelease, val apkUrl: String) : UpdateState()
    data class PermissionRequired(val release: GitHubRelease, val apkUrl: String) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    data class ReadyToInstall(val apkPath: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

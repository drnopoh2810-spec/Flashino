package com.eduspecial.data.remote.config

import android.util.Log
import com.eduspecial.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

@Singleton
class RemoteConfigManager @Inject constructor() {

    suspend fun fetchAndActivate(): Boolean {
        Log.d("RemoteConfig", "Using local BuildConfig runtime values")
        return true
    }

    fun getAlgoliaConfig(): AlgoliaConfig {
        return AlgoliaConfig(
            appId = BuildConfig.ALGOLIA_APP_ID,
            searchKey = BuildConfig.ALGOLIA_SEARCH_KEY
        )
    }

    fun getElevenLabsKeys(): List<String> {
        return emptyList()
    }

    fun getIFlytekTtsAccounts(): List<IFlytekTtsAccountConfig> {
        Log.d("RemoteConfig", "iFLYTEK accounts are served through secure proxy")
        return emptyList()
    }

    fun getGoogleWebClientId(): String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun getCloudinaryConfigs(): List<CloudinaryConfig> {
        val json = BuildConfig.CLOUDINARY_ACCOUNTS_JSON
        return if (json.isBlank() || json == "[]") {
            DEFAULT_CLOUDINARY_CONFIGS
        } else {
            parseCloudinaryJson(json)
        }
    }

    fun getCloudinaryAccountCount(): Int = getCloudinaryConfigs().size

    fun getCloudinaryConfig(accountNumber: Int): CloudinaryConfig {
        val configs = getCloudinaryConfigs()
        return configs.getOrNull(accountNumber - 1) ?: configs.firstOrNull() ?: CloudinaryConfig("", "")
    }

    companion object {
        private val DEFAULT_CLOUDINARY_CONFIGS = emptyList<CloudinaryConfig>()

        private fun parseElevenLabsKeys(raw: String): List<String> {
            val trimmed = raw.trim()
            val parsed = if (trimmed.startsWith("[")) {
                try {
                    val array = JSONArray(trimmed)
                    buildList {
                        for (i in 0 until array.length()) {
                            add(array.optString(i))
                        }
                    }
                } catch (_: Exception) {
                    trimmed.split(",", ";", "\n")
                }
            } else {
                trimmed.split(",", ";", "\n")
            }

            return parsed
                .map { it.trim().removePrefix("\"").removeSuffix("\"") }
                .filter { it.length >= 20 }
                .distinct()
        }

        private fun parseCloudinaryJson(json: String): List<CloudinaryConfig> {
            return try {
                val result = mutableListOf<CloudinaryConfig>()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(
                        CloudinaryConfig(
                            obj.optString("cloudName").ifBlank { obj.optString("cloud_name") },
                            obj.optString("uploadPreset").ifBlank { obj.optString("upload_preset") }
                        )
                    )
                }
                result.filter { it.cloudName.isNotBlank() && it.uploadPreset.isNotBlank() }
                    .ifEmpty { DEFAULT_CLOUDINARY_CONFIGS }
            } catch (_: Exception) {
                DEFAULT_CLOUDINARY_CONFIGS
            }
        }

        private fun parseIFlytekAccountsJson(json: String): List<IFlytekTtsAccountConfig> {
            return try {
                val result = mutableListOf<IFlytekTtsAccountConfig>()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(
                        IFlytekTtsAccountConfig(
                            appId = obj.optString("appId").ifBlank { obj.optString("APPID") },
                            apiKey = obj.optString("apiKey").ifBlank { obj.optString("APIKey") },
                            apiSecret = obj.optString("apiSecret").ifBlank { obj.optString("APISecret") }
                        )
                    )
                }
                result
                    .filter { it.appId.isNotBlank() && it.apiKey.isNotBlank() && it.apiSecret.isNotBlank() }
                    .ifEmpty { parseIFlytekAccountsFallback(json) }
            } catch (_: Exception) {
                parseIFlytekAccountsFallback(json)
            }
        }

        private fun parseIFlytekAccountsFallback(raw: String): List<IFlytekTtsAccountConfig> {
            val accountRegex = Regex(
                """\{\s*"appId"\s*:\s*"([^"]+)"\s*,\s*"apiKey"\s*:\s*"([^"]+)"\s*,\s*"apiSecret"\s*:\s*"([^"]+)"\s*}"""
            )
            return accountRegex.findAll(raw).map { match ->
                IFlytekTtsAccountConfig(
                    appId = match.groupValues[1],
                    apiKey = match.groupValues[2],
                    apiSecret = match.groupValues[3]
                )
            }.toList()
        }
    }
}

data class CloudinaryConfig(val cloudName: String, val uploadPreset: String)
data class AlgoliaConfig(val appId: String, val searchKey: String)
data class IFlytekTtsAccountConfig(
    val appId: String,
    val apiKey: String,
    val apiSecret: String
)

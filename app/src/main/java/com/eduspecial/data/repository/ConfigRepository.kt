package com.eduspecial.data.repository

import com.eduspecial.data.remote.config.AlgoliaConfig
import com.eduspecial.data.remote.config.CloudinaryConfig
import com.eduspecial.data.remote.config.IFlytekTtsAccountConfig
import com.eduspecial.data.remote.config.RemoteConfigManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ConfigRepository @Inject constructor(
    private val remoteConfigManager: RemoteConfigManager
) {
    private val _isConfigLoaded = MutableStateFlow(false)
    val isConfigLoaded: StateFlow<Boolean> = _isConfigLoaded.asStateFlow()

    private val _configStatus = MutableStateFlow("Initializing...")
    val configStatus: StateFlow<String> = _configStatus.asStateFlow()

    suspend fun initializeConfig(): Boolean {
        val success = remoteConfigManager.fetchAndActivate()
        _isConfigLoaded.value = true
        _configStatus.value = if (success) {
            "Local configuration active"
        } else {
            "Using local fallback values"
        }
        return success
    }

    fun getCloudinaryConfigs(): List<CloudinaryConfig> {
        val count = remoteConfigManager.getCloudinaryAccountCount()
        return (1..count).map { remoteConfigManager.getCloudinaryConfig(it) }
            .filter { it.cloudName.isNotEmpty() && it.uploadPreset.isNotEmpty() }
    }

    fun getAlgoliaConfig(): AlgoliaConfig = remoteConfigManager.getAlgoliaConfig()

    fun getElevenLabsKeys(): List<String> = remoteConfigManager.getElevenLabsKeys()

    fun getIFlytekTtsAccounts(): List<IFlytekTtsAccountConfig> =
        remoteConfigManager.getIFlytekTtsAccounts()

    fun getConfigurationSummary(): String = """
        Configuration Status: ${_configStatus.value}
        Cloudinary Accounts: ${getCloudinaryConfigs().size}
        iFLYTEK TTS Accounts: ${getIFlytekTtsAccounts().size}
        ElevenLabs Keys: ${getElevenLabsKeys().size}
    """.trimIndent()
}

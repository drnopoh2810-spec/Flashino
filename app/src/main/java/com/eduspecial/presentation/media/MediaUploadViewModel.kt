package com.eduspecial.presentation.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.data.remote.api.CloudinaryService
import com.eduspecial.data.remote.api.UploadResult
import com.eduspecial.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MediaUploadUiState(
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0,
    val uploadedUrl: String? = null,
    val uploadedMediaType: MediaType = MediaType.NONE,
    val error: String? = null
)

@HiltViewModel
class MediaUploadViewModel @Inject constructor(
    private val cloudinaryService: CloudinaryService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUploadUiState())
    val uiState: StateFlow<MediaUploadUiState> = _uiState.asStateFlow()

    fun uploadMedia(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null, uploadProgress = 0) }
            val resourceType = if (isVideo) "video" else "image"
            try {
                when (val result = cloudinaryService.uploadMedia(
                    uri = uri,
                    resourceType = resourceType,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )) {
                    is UploadResult.Success -> {
                        val mediaType = when (result.resourceType) {
                            "video" -> MediaType.VIDEO
                            "raw"   -> MediaType.AUDIO
                            else    -> MediaType.IMAGE
                        }
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadProgress = 100,
                                uploadedUrl = result.url,
                                uploadedMediaType = mediaType
                            )
                        }
                    }
                    is UploadResult.Failure -> {
                        _uiState.update {
                            it.copy(isUploading = false, error = "فشل الرفع: ${result.error}")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, error = "فشل الرفع: ${e.message}")
                }
            }
        }
    }

    fun uploadAudio(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null, uploadProgress = 0) }
            try {
                when (val result = cloudinaryService.uploadMedia(
                    uri = uri,
                    resourceType = "raw",
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )) {
                    is UploadResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isUploading = false,
                                uploadProgress = 100,
                                uploadedUrl = result.url,
                                uploadedMediaType = MediaType.AUDIO
                            )
                        }
                    }
                    is UploadResult.Failure -> {
                        _uiState.update {
                            it.copy(isUploading = false, error = "فشل رفع الصوت: ${result.error}")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUploading = false, error = "فشل رفع الصوت: ${e.message}")
                }
            }
        }
    }

    fun clearUploadedUrl() {
        _uiState.update { it.copy(uploadedUrl = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetState() {
        _uiState.value = MediaUploadUiState()
    }
}

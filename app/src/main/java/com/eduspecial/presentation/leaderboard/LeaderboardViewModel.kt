package com.eduspecial.presentation.leaderboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.data.repository.LeaderboardRepository
import com.eduspecial.domain.model.LeaderboardEntry
import com.eduspecial.domain.model.LeaderboardPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class LeaderboardUiState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPeriod: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
    val isRefreshing: Boolean = false,
    val isOfflineData: Boolean = false // إضافة مؤشر للبيانات القديمة
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val repository: LeaderboardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        // تحميل البيانات المحلية أولاً إذا وجدت (بافتراض أن الريبوزيتوري يدعم ذلك)
        loadLeaderboard(LeaderboardPeriod.ALL_TIME)
    }

    fun selectPeriod(period: LeaderboardPeriod) {
        if (_uiState.value.selectedPeriod == period && _uiState.value.entries.isNotEmpty()) return
        _uiState.update { it.copy(selectedPeriod = period, entries = emptyList()) }
        loadLeaderboard(period)
    }

    fun refresh() {
        loadLeaderboard(_uiState.value.selectedPeriod, isRefresh = true)
    }

    private fun loadLeaderboard(period: LeaderboardPeriod, isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    error = null
                )
            }
            
            repository.getLeaderboard(period)
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(
                            entries = entries,
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            isOfflineData = false
                        )
                    }
                }
                .onFailure { e ->
                    // في حال الفشل، لا نمسح البيانات القديمة إذا كانت موجودة، فقط نظهر رسالة
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (it.entries.isEmpty()) "تعذّر تحميل البيانات. تحقق من الاتصال." else null,
                            isOfflineData = it.entries.isNotEmpty()
                        )
                    }
                }
        }
    }
}

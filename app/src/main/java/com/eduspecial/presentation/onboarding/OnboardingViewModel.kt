package com.eduspecial.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.utils.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TOTAL_PAGES = 3

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore
) : ViewModel() {

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun nextPage() {
        val next = _currentPage.value + 1
        if (next < TOTAL_PAGES) {
            _currentPage.value = next
        } else {
            complete()
        }
    }

    fun skip() {
        complete()
    }

    fun complete() {
        viewModelScope.launch {
            prefs.markOnboardingDone()
            _isComplete.value = true
        }
    }

    val isLastPage: Boolean
        get() = _currentPage.value == TOTAL_PAGES - 1
}

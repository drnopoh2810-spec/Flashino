package com.eduspecial.presentation.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduspecial.utils.UserPreferencesDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val prefs: UserPreferencesDataStore
) : ViewModel() {

    /** Called after the user grants or skips permissions — never show this screen again */
    fun markPermissionsDone() {
        viewModelScope.launch {
            prefs.markPermissionsDone()
        }
    }
}

package com.getgymdone.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getgymdone.app.data.repository.UserPrefsRepository
import com.getgymdone.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPrefsRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = prefs.observe()
        .map { ThemeMode.fromString(it.theme) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)
}

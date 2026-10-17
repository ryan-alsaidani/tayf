package com.tayf.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.FilterType
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val busy: Boolean = false,
    val messageRes: Int? = null,
    val errorText: String? = null,
    val restartNeeded: Boolean = false
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val s = container.settings
    private fun <T> share(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val hijri = share(s.hijriDates, false)
    val pageSize = share(s.defaultPageSize, PageSize.A4)
    val filter = share(s.defaultFilter, FilterType.ENHANCED_COLOR)
    val autoCapture = share(s.autoCapture, true)
    val qualityGuard = share(s.qualityGuard, true)
    val pinEnabled = share(s.pinEnabled, false)
    val biometric = share(s.biometricEnabled, false)
    val lockTimeout = share(s.lockTimeoutSeconds, 30)
    val jpegQuality = share(s.jpegQuality, 90)

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    fun setHijri(v: Boolean) = viewModelScope.launch { s.setHijriDates(v) }
    fun setPageSize(v: PageSize) = viewModelScope.launch { s.setDefaultPageSize(v) }
    fun setFilter(v: FilterType) = viewModelScope.launch { s.setDefaultFilter(v) }
    fun setAutoCapture(v: Boolean) = viewModelScope.launch { s.setAutoCapture(v) }
    fun setQualityGuard(v: Boolean) = viewModelScope.launch { s.setQualityGuard(v) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { s.setBiometricEnabled(v) }
    fun setLockTimeout(v: Int) = viewModelScope.launch { s.setLockTimeoutSeconds(v) }
    fun setJpegQuality(v: Int) = viewModelScope.launch { s.setJpegQuality(v) }

    fun enablePin(pin: String) = viewModelScope.launch { container.pinManager.setPin(pin) }

    fun disablePin(pin: String) = viewModelScope.launch {
        val ok = container.pinManager.clearPin(pin)
        if (ok) s.setBiometricEnabled(false)
        _ui.update { it.copy(messageRes = if (ok) R.string.settings_pin_removed else R.string.lock_wrong) }
    }

    fun backup(target: Uri, password: String?) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = container.backup.createBackup(target, password?.ifBlank { null })) {
                is Outcome.Ok -> _ui.update { it.copy(busy = false, messageRes = R.string.settings_backup_done) }
                is Outcome.Fail -> _ui.update { it.copy(busy = false, errorText = r.message) }
            }
        }
    }

    fun restore(source: Uri, password: String?) {
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val r = container.backup.restoreBackup(source, password?.ifBlank { null })) {
                is Outcome.Ok -> _ui.update { it.copy(busy = false, restartNeeded = true) }
                is Outcome.Fail -> _ui.update { it.copy(busy = false, errorText = r.message) }
            }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(messageRes = null, errorText = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

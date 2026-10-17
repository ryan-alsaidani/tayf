package com.tayf.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tayf.app.core.FilterType
import com.tayf.app.core.PageSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object K {
        val hijri = booleanPreferencesKey("hijri_dates")
        val pageSize = stringPreferencesKey("default_page_size")
        val filter = stringPreferencesKey("default_filter")
        val autoCapture = booleanPreferencesKey("auto_capture")
        val qualityGuard = booleanPreferencesKey("quality_guard")
        val pinEnabled = booleanPreferencesKey("pin_enabled")
        val biometric = booleanPreferencesKey("biometric_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout_seconds")
        val jpegQuality = intPreferencesKey("jpeg_quality")
    }

    private val data: Flow<Preferences> = store.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }

    val hijriDates: Flow<Boolean> = data.map { it[K.hijri] ?: false }
    suspend fun setHijriDates(v: Boolean) { store.edit { it[K.hijri] = v } }

    val defaultPageSize: Flow<PageSize> = data.map { p ->
        runCatching { PageSize.valueOf(p[K.pageSize] ?: PageSize.A4.name) }.getOrDefault(PageSize.A4)
    }
    suspend fun setDefaultPageSize(v: PageSize) { store.edit { it[K.pageSize] = v.name } }

    val defaultFilter: Flow<FilterType> = data.map { p ->
        runCatching { FilterType.valueOf(p[K.filter] ?: FilterType.ENHANCED_COLOR.name) }.getOrDefault(FilterType.ENHANCED_COLOR)
    }
    suspend fun setDefaultFilter(v: FilterType) { store.edit { it[K.filter] = v.name } }

    val autoCapture: Flow<Boolean> = data.map { it[K.autoCapture] ?: true }
    suspend fun setAutoCapture(v: Boolean) { store.edit { it[K.autoCapture] = v } }

    val qualityGuard: Flow<Boolean> = data.map { it[K.qualityGuard] ?: true }
    suspend fun setQualityGuard(v: Boolean) { store.edit { it[K.qualityGuard] = v } }

    val pinEnabled: Flow<Boolean> = data.map { it[K.pinEnabled] ?: false }
    suspend fun setPinEnabled(v: Boolean) { store.edit { it[K.pinEnabled] = v } }

    val biometricEnabled: Flow<Boolean> = data.map { it[K.biometric] ?: false }
    suspend fun setBiometricEnabled(v: Boolean) { store.edit { it[K.biometric] = v } }

    val lockTimeoutSeconds: Flow<Int> = data.map { it[K.lockTimeout] ?: 30 }
    suspend fun setLockTimeoutSeconds(v: Int) { store.edit { it[K.lockTimeout] = v } }

    /** 50..100, default 90 */
    val jpegQuality: Flow<Int> = data.map { (it[K.jpegQuality] ?: 90).coerceIn(50, 100) }
    suspend fun setJpegQuality(v: Int) { store.edit { it[K.jpegQuality] = v.coerceIn(50, 100) } }
}

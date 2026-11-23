package com.rfid.rolltrack.config

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rfid.rolltrack.scanner.TagIdNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "rolltrack_settings")

class SettingsRepository(private val context: Context) {

    private val KEY_BASE_URL = stringPreferencesKey("base_url")
    private val KEY_DEMO_MODE = booleanPreferencesKey("demo_mode")
    private val KEY_ERP_FORMAT = stringPreferencesKey("erp_format")
    private val KEY_USB_BYTE_ORDER = stringPreferencesKey("usb_byte_order")
    private val KEY_SIMULATE_OFFLINE = booleanPreferencesKey("simulate_offline")

    private val _session = MutableStateFlow(UserSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[KEY_BASE_URL] ?: "http://118.179.223.40:7007/ords/",
            demoMode = prefs[KEY_DEMO_MODE] ?: false,
            erpFormat = prefs[KEY_ERP_FORMAT]?.let {
                try { TagIdNormalizer.ErpFormat.valueOf(it) } catch (_: Exception) { TagIdNormalizer.ErpFormat.DECIMAL_LE }
            } ?: TagIdNormalizer.ErpFormat.DECIMAL_LE,
            usbByteOrder = prefs[KEY_USB_BYTE_ORDER]?.let {
                try { TagIdNormalizer.ByteOrder.valueOf(it) } catch (_: Exception) { TagIdNormalizer.ByteOrder.LITTLE_ENDIAN }
            } ?: TagIdNormalizer.ByteOrder.LITTLE_ENDIAN,
            simulateOffline = prefs[KEY_SIMULATE_OFFLINE] ?: false
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        // Collect current and save
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                baseUrl = prefs[KEY_BASE_URL] ?: "http://118.179.223.40:7007/ords/",
                demoMode = prefs[KEY_DEMO_MODE] ?: false,
                erpFormat = prefs[KEY_ERP_FORMAT]?.let {
                    try { TagIdNormalizer.ErpFormat.valueOf(it) } catch (_: Exception) { TagIdNormalizer.ErpFormat.DECIMAL_LE }
                } ?: TagIdNormalizer.ErpFormat.DECIMAL_LE,
                usbByteOrder = prefs[KEY_USB_BYTE_ORDER]?.let {
                    try { TagIdNormalizer.ByteOrder.valueOf(it) } catch (_: Exception) { TagIdNormalizer.ByteOrder.LITTLE_ENDIAN }
                } ?: TagIdNormalizer.ByteOrder.LITTLE_ENDIAN,
                simulateOffline = prefs[KEY_SIMULATE_OFFLINE] ?: false
            )
            val updated = transform(current)
            prefs[KEY_BASE_URL] = updated.baseUrl
            prefs[KEY_DEMO_MODE] = updated.demoMode
            prefs[KEY_ERP_FORMAT] = updated.erpFormat.name
            prefs[KEY_USB_BYTE_ORDER] = updated.usbByteOrder.name
            prefs[KEY_SIMULATE_OFFLINE] = updated.simulateOffline
        }
    }

    private val KEY_SESSION = stringPreferencesKey("user_session")
    private val io = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Restores the last login so app restarts / updates don't log the operator out. */
    suspend fun restoreSession() {
        val raw = context.dataStore.data.first()[KEY_SESSION] ?: return
        try {
            _session.value = kotlinx.serialization.json.Json.decodeFromString(UserSession.serializer(), raw)
        } catch (_: Exception) {}
    }

    fun setSession(userSession: UserSession) {
        _session.value = userSession
        io.launch {
            context.dataStore.edit { it[KEY_SESSION] = kotlinx.serialization.json.Json.encodeToString(UserSession.serializer(), userSession) }
        }
    }

    fun clearSession() {
        _session.value = UserSession()
        io.launch { context.dataStore.edit { it.remove(KEY_SESSION) } }
    }
}

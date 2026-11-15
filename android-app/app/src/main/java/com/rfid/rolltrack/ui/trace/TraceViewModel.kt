package com.rfid.rolltrack.ui.trace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.remote.TraceResult
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TraceViewModel(private val container: AppContainer) : ViewModel() {

    private val _result = MutableStateFlow<TraceResult?>(null)
    val result: StateFlow<TraceResult?> = _result.asStateFlow()

    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun onScan(event: ScanEvent) {
        when (event) {
            is ScanEvent.Tag -> trace(event.tagId)
            is ScanEvent.Code -> trace(event.raw)
        }
    }

    fun trace(code: String) {
        val clean = code.trim()
        if (clean.isBlank()) return

        _busy.value = true
        _banner.value = null
        viewModelScope.launch {
            try {
                val userId = container.settings.session.value.userId.ifBlank { "1" }
                _result.value = container.api.trace(userId, clean)
                container.feedback.tagAccepted()
            } catch (e: Exception) {
                _banner.value = Banner(Banner.Tone.BAD, e.message ?: "Failed to trace code: $clean")
                container.feedback.error()
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissBanner() {
        _banner.value = null
    }
}

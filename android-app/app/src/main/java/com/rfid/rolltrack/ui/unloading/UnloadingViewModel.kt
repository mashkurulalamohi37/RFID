package com.rfid.rolltrack.ui.unloading

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.UnloadOp
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.launch

class UnloadingViewModel(container: AppContainer) :
    ScanSessionViewModel<UnloadHeader>(
        container = container,
        type = SessionType.UNLOADING,
        headerSerializer = UnloadHeader.serializer(),
        emptyHeader = { UnloadHeader() }
    ) {

    override suspend fun onTag(event: ScanEvent.Tag) {
        val currentRef = header.value?.loadingRef
        if (currentRef.isNullOrBlank()) {
            // WORKFLOW 3 (Unloading): RFID scan replaces QR code scanning!
            // Tapping a Vehicle/Gate RFID card loads the loading reference & expected manifest!
            openLoading(event.tagId, isRfid = true)
            return
        }

        // Loading ref is active: this is a roll tag being unloaded
        addTag(event)
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        openLoading(event.raw, isRfid = false)
    }

    fun openLoading(code: String, isRfid: Boolean = false) {
        val clean = code.trim()
        if (clean.isBlank()) return

        val currentSendable = sendable
        if (currentSendable.isNotEmpty()) {
            show(Banner.Tone.WARN, "Submit or clear the scanned rolls before opening another vehicle")
            container.feedback.warn()
            return
        }

        _busy.value = true
        viewModelScope.launch {
            try {
                val userId = container.settings.session.value.userId.ifBlank { "1" }
                val expected = container.api.loadingExpected(userId, clean)
                saveHeader(UnloadHeader(loadingRef = clean, expected = expected))
                val sourceLabel = if (isRfid) "RFID Tag" else "Code"
                val expCount = expected.tagIds.size
                show(Banner.Tone.OK, "Loading trip loaded via $sourceLabel: $clean ($expCount rolls expected)")
                container.feedback.tagAccepted()
            } catch (e: Exception) {
                show(Banner.Tone.BAD, e.message ?: "Failed to load manifest for: $clean")
                container.feedback.error()
            } finally {
                _busy.value = false
            }
        }
    }

    fun submitRows() {
        val ref = header.value?.loadingRef
        if (ref.isNullOrBlank()) {
            show(Banner.Tone.WARN, "Tap Vehicle RFID card or enter loading reference first")
            container.feedback.warn()
            return
        }

        val currentSendable = sendable
        if (currentSendable.isEmpty()) {
            show(Banner.Tone.WARN, "No rolls scanned yet")
            container.feedback.warn()
            return
        }

        val op = UnloadOp(
            loadingRef = ref,
            tags = currentSendable.map { it.tagId }
        )
        submit(op)
    }

    fun finishVehicle() {
        startNew()
    }
}

package com.rfid.rolltrack.ui.unloading

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.UnloadOp
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.scanner.TagIdNormalizer
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
        // Every RFID scan here is a roll being unloaded (backend needs only USER_ID + ITEMS).
        addTag(event)
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        val open = header.value?.loadingRef
        if (!open.isNullOrBlank()) {
            show(Banner.Tone.WARN, "Trip $open is open. Tap 'Finish vehicle' before opening ${event.raw.trim()}.")
            container.feedback.warn()
            return
        }
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
        val ref = header.value?.loadingRef.orEmpty() // optional; not sent to the backend

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

    /** Once unloading is accepted, the repository clears those tags' data on the device (issue + weight). */
    override suspend fun afterSubmit(result: com.rfid.rolltrack.data.SubmitSummary) {
        if (result.accepted <= 0) return
        if (result.kind == com.rfid.rolltrack.data.SubmitSummary.Kind.QUEUED) return
        val cleared = repo.lastUnloadCleared
        if (cleared > 0) {
            show(Banner.Tone.OK, "Unloaded. $cleared tag(s) cleared on this PDA and ready to be tagged for a new lot.")
        }
    }

    fun finishVehicle() {
        startNew()
    }
}

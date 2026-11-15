package com.rfid.rolltrack.ui.issue

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.IssueOp
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.launch

class TagIssueViewModel(container: AppContainer) :
    ScanSessionViewModel<IssueHeader>(
        container = container,
        type = SessionType.ISSUE,
        headerSerializer = IssueHeader.serializer(),
        emptyHeader = { IssueHeader() }
    ) {

    override suspend fun onTag(event: ScanEvent.Tag) {
        val req = header.value?.request
        if (req == null) {
            // Replaced QR code scan with RFID scan: tapping an RFID tag loads the request
            loadRequest(event.tagId, isRfid = true)
            return
        }

        if (header.value?.countOnly == true) {
            show(Banner.Tone.WARN, "Count-only mode is on. Switch to 'Scan tags' to record tags.")
            container.feedback.warn()
            return
        }

        val currentCount = items.value.size
        if (currentCount >= req.effectiveBalance) {
            show(Banner.Tone.BAD, "Balance reached (${req.effectiveBalance}). No more tags for this request.")
            container.feedback.error()
            return
        }

        addTag(event)
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        // Also allow manual typing or barcode entry of request ref
        loadRequest(event.raw, isRfid = false)
    }

    fun loadRequest(code: String, isRfid: Boolean = false) {
        val currentSendable = sendable
        if (currentSendable.isNotEmpty()) {
            show(Banner.Tone.WARN, "Submit or clear the ${currentSendable.size} scanned tags before opening another request")
            container.feedback.warn()
            return
        }

        _busy.value = true
        viewModelScope.launch {
            try {
                val userId = container.settings.session.value.userId.ifBlank { "1" }
                val req = container.api.lookupRequest(userId, code)
                saveHeader(IssueHeader(request = req, countOnly = false))
                val sourceLabel = if (isRfid) "RFID Tag" else "Code"
                show(Banner.Tone.OK, "Request loaded via $sourceLabel: ${req.refCode}")
                container.feedback.tagAccepted()
            } catch (e: Exception) {
                show(Banner.Tone.BAD, e.message ?: "Failed to lookup request: $code")
                container.feedback.error()
            } finally {
                _busy.value = false
            }
        }
    }

    fun setCountOnly(enabled: Boolean) {
        val current = header.value ?: IssueHeader()
        saveHeader(current.copy(countOnly = enabled))
    }

    fun submitTags() {
        val req = header.value?.request ?: return
        val currentSendable = sendable
        if (currentSendable.isEmpty()) {
            show(Banner.Tone.WARN, "No tags scanned yet")
            container.feedback.warn()
            return
        }

        val op = IssueOp(
            refId = req.refId,
            countOnly = false,
            count = currentSendable.size,
            tags = currentSendable.map { it.tagId }
        )
        submit(op)
    }

    fun submitCount(count: Int) {
        val req = header.value?.request ?: return
        if (count <= 0) {
            show(Banner.Tone.WARN, "Enter how many tags are issued")
            container.feedback.warn()
            return
        }
        if (count > req.effectiveBalance) {
            show(Banner.Tone.BAD, "Only ${req.effectiveBalance} tags remain for this request")
            container.feedback.error()
            return
        }

        val op = IssueOp(
            refId = req.refId,
            countOnly = true,
            count = count,
            tags = emptyList()
        )
        submit(op)
    }
}

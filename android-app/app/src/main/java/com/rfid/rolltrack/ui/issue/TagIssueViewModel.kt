package com.rfid.rolltrack.ui.issue

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.IssueOp
import com.rfid.rolltrack.data.SubmitSummary
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.data.remote.IssueRequest
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.scanner.ScanSource
import com.rfid.rolltrack.scanner.TagIdNormalizer
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.launch
import java.util.Locale

class TagIssueViewModel(container: AppContainer) :
    ScanSessionViewModel<IssueHeader>(
        container = container,
        type = SessionType.ISSUE,
        headerSerializer = IssueHeader.serializer(),
        emptyHeader = { IssueHeader() }
    ) {

    /** The ERP's REF_ID is a NUMBER (e.g. 38). Anything else (old demo "REQ-0002", a scanned code) is rejected by the server. */
    private fun isErpRefId(refId: String?): Boolean = !refId.isNullOrBlank() && refId.trim().all { it.isDigit() }

    override suspend fun onSessionRestored(id: Long) {
        val req = header.value?.request ?: return
        if (!isErpRefId(req.refId)) {
            com.rfid.rolltrack.data.remote.ApiLog.warn("Tag Issue: dropping saved request ${req.refCode} with non-ERP REF_ID '${req.refId}'")
            saveHeader(IssueHeader())
            show(Banner.Tone.WARN, "Old request '${req.refCode}' was not from the ERP and was cleared. Scan the request again.")
            return
        }
        // A request restored from an earlier session may be out of date: refresh its counts.
        refreshRequest()
    }

    override suspend fun onTag(event: ScanEvent.Tag) {
        // A roll tag never identifies the request: req-scan expects the REQ_NO business reference.
        acceptRollTags(listOf(event.tagId), event.source)
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        val reads = TagIdNormalizer.splitReads(event.raw)
        if (reads.isEmpty()) return

        // UHF trigger: the Seuic UHF service broadcasts one or more EPCs, one per line.
        if (reads.all { TagIdNormalizer.isEpc(it) }) {
            acceptRollTags(reads.map { it.uppercase(Locale.ROOT) }, event.source)
            return
        }

        if (reads.size > 1) {
            show(Banner.Tone.WARN, "Unrecognised scan (${reads.size} values). Scan one request QR at a time.")
            container.feedback.warn()
            return
        }

        val code = reads.first()
        val open = header.value?.request
        if (open != null) {
            show(Banner.Tone.WARN, "Request ${open.refCode} is open. Tap 'New request' before loading $code.")
            container.feedback.warn()
            return
        }

        // Request QR via the PDA barcode scanner, or a typed request number.
        loadRequest(code, isRfid = false)
    }

    private suspend fun acceptRollTags(epcs: List<String>, source: ScanSource) {
        val unique = epcs.distinct()
        val req = header.value?.request
        if (req == null) {
            val first = TagIdNormalizer.short(unique.first())
            val what = if (unique.size == 1) "Roll tag read ($first)" else "${unique.size} roll tags read ($first, ...)"
            show(Banner.Tone.WARN, "$what. Load the request first: scan the request QR or type the request no.")
            container.feedback.warn()
            return
        }

        if (header.value?.countOnly == true) {
            show(Banner.Tone.WARN, "Count-only mode is on. Switch to 'Scan tags' to record tags.")
            container.feedback.warn()
            return
        }

        val existing = items.value.map { it.tagId }.toSet()
        val alreadyIssued = container.issuedLedger.issuedTags(req.refId)
        val issuedBefore = unique.filter { it in alreadyIssued }
        val fresh = unique.filter { it !in existing && it !in alreadyIssued }
        if (fresh.isEmpty()) {
            if (issuedBefore.isNotEmpty()) {
                container.feedback.error()
                show(Banner.Tone.BAD, "Already issued to ${req.refCode}: ${issuedBefore.joinToString { TagIdNormalizer.short(it) }}")
            } else {
                container.feedback.duplicate()
                show(Banner.Tone.INFO, "Already scanned: ${unique.joinToString { TagIdNormalizer.short(it) }}")
            }
            return
        }

        val remaining = remainingFor(req)
        if (remaining <= 0) {
            show(Banner.Tone.BAD, "Balance reached (${req.effectiveBalance}). No more tags for this request.")
            container.feedback.error()
            return
        }

        val toAdd = fresh.take(remaining)
        var added = 0
        for (tagId in toAdd) {
            if (addTag(ScanEvent.Tag(tagId, source))) added++
        }

        val skippedDup = unique.size - fresh.size - issuedBefore.size
        val overBalance = fresh.size - toAdd.size
        when {
            overBalance > 0 -> {
                show(Banner.Tone.BAD, "Added $added. $overBalance more ignored: balance reached (${req.effectiveBalance}).")
                container.feedback.error()
            }
            issuedBefore.isNotEmpty() -> {
                show(Banner.Tone.WARN, "Added $added. ${issuedBefore.size} already issued to ${req.refCode}, skipped.")
                container.feedback.warn()
            }
            skippedDup > 0 -> show(Banner.Tone.INFO, "Added $added, $skippedDup already scanned.")
            added > 1 -> show(Banner.Tone.OK, "Added $added roll tags.")
        }
    }

    fun loadRequest(code: String, isRfid: Boolean = false) {
        val currentSendable = sendable
        // Only block when tags already belong to a loaded request; leftover tags with no request just carry over.
        if (currentSendable.isNotEmpty() && header.value?.request != null) {
            show(Banner.Tone.WARN, "Submit or clear the ${currentSendable.size} scanned tags before opening another request")
            container.feedback.warn()
            return
        }

        _busy.value = true
        viewModelScope.launch {
            try {
                val userId = container.settings.session.value.userId.ifBlank { "1" }
                val req = withLedger(container.api.lookupRequest(userId, code))
                saveHeader(IssueHeader(request = req, countOnly = false))
                val sourceLabel = if (isRfid) "RFID Tag" else "Code"
                if (req.effectiveBalance <= 0) {
                    show(Banner.Tone.BAD, "${req.refCode}: balance is 0 (${req.shownIssued} of ${req.required} already issued). No more tags can be submitted.")
                    container.feedback.error()
                } else {
                    show(Banner.Tone.OK, "Request loaded via $sourceLabel: ${req.refCode}. You can issue ${req.effectiveBalance} tags.")
                    container.feedback.tagAccepted()
                }
            } catch (e: Exception) {
                show(Banner.Tone.BAD, e.message ?: "Failed to lookup request: $code")
                container.feedback.error()
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * After the server accepts a batch, ask it again for the request's counts so Required / Issued / Balance
     * always come from the ERP (never counted up on the device). A failed refresh keeps the old numbers.
     */
    override suspend fun afterSubmit(result: SubmitSummary) {
        // Apply this device's issued record right away, even if the server refresh below fails.
        header.value?.let { h -> h.request?.let { saveHeader(h.copy(request = withLedger(it))) } }
        if (result.kind != SubmitSummary.Kind.ALL_ACCEPTED &&
            result.kind != SubmitSummary.Kind.PARTIAL &&
            result.kind != SubmitSummary.Kind.UNCERTAIN
        ) return
        refreshRequest()
    }

    private suspend fun refreshRequest() {
        val current = header.value ?: return
        val req = current.request ?: return
        try {
            val userId = container.settings.session.value.userId.ifBlank { "1" }
            val fresh = withLedger(container.api.lookupRequest(userId, req.refCode))
            saveHeader((header.value ?: current).copy(request = fresh))
            com.rfid.rolltrack.data.remote.ApiLog.info(
                "Tag Issue: refreshed ${fresh.refCode} required=${fresh.required} erpIssued=${fresh.issued} deviceIssued=${fresh.localIssued} balance=${fresh.effectiveBalance}"
            )
        } catch (e: Exception) {
            // Keep the old ERP numbers, but still apply this device's issued record.
            header.value?.let { h -> h.request?.let { saveHeader(h.copy(request = withLedger(it))) } }
            com.rfid.rolltrack.data.remote.ApiLog.warn("Tag Issue: could not refresh request counts: ${e.message}")
        }
    }

    /** Adds what this device has already issued to the request, since the ERP does not report it. */
    private fun withLedger(req: IssueRequest): IssueRequest =
        req.copy(localIssued = container.issuedLedger.issued(req.refId))

    /** Tags that may still be added: balance minus tags on screen that are not yet accepted (scanned, failed, queued). */
    private fun remainingFor(req: IssueRequest): Int = req.effectiveBalance - items.value.size

    /** Blocks a submit that would go over the balance, and says how many to remove. */
    private fun withinBalance(req: IssueRequest, count: Int, alreadyOnScreen: Int): Boolean {
        val allowed = req.effectiveBalance - alreadyOnScreen
        if (count <= allowed) return true
        val msg = if (allowed <= 0) "Balance reached (${req.effectiveBalance}). No more tags can be submitted for ${req.refCode}."
        else "Only $allowed tags can be submitted for ${req.refCode}. Remove ${count - allowed}."
        show(Banner.Tone.BAD, msg)
        container.feedback.error()
        return false
    }

    fun setCountOnly(enabled: Boolean) {
        val current = header.value ?: IssueHeader()
        saveHeader(current.copy(countOnly = enabled))
    }

    private fun refIdOk(refId: String): Boolean {
        if (isErpRefId(refId)) return true
        show(Banner.Tone.BAD, "This request has no ERP REF_ID ('$refId'). Tap New request and scan the request again.")
        container.feedback.error()
        return false
    }

    fun submitTags() {
        val req = header.value?.request ?: return
        if (!refIdOk(req.refId)) return
        val currentSendable = sendable
        if (currentSendable.isEmpty()) {
            show(Banner.Tone.WARN, "No tags scanned yet")
            container.feedback.warn()
            return
        }
        // Queued (offline) tags on screen are not accepted yet, so they still use up balance.
        val queuedOnScreen = items.value.size - currentSendable.size
        if (!withinBalance(req, currentSendable.size, queuedOnScreen)) return

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
        if (!refIdOk(req.refId)) return
        if (count <= 0) {
            show(Banner.Tone.WARN, "Enter how many tags are issued")
            container.feedback.warn()
            return
        }
        if (!withinBalance(req, count, items.value.size)) return

        val op = IssueOp(
            refId = req.refId,
            countOnly = true,
            count = count,
            tags = emptyList()
        )
        submit(op)
    }
}

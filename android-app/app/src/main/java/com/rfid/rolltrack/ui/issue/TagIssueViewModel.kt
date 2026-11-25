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
        // A tag stays taken until it is unloaded; then it starts fresh and can be issued to a new lot.
        val alreadyIssued = container.issuedLedger.issuedAnywhere()
        val issuedBefore = unique.filter { it in alreadyIssued && it !in existing }
        val fresh = unique.filter { it !in existing && it !in alreadyIssued }
        if (fresh.isEmpty()) {
            if (issuedBefore.isNotEmpty()) {
                container.feedback.error()
                val where = if (issuedBefore.all { alreadyIssued[it] == req.refId }) "to ${req.refCode}" else "to another request"
                show(Banner.Tone.BAD, "Already issued $where and not unloaded yet: ${issuedBefore.joinToString { TagIdNormalizer.short(it) }}")
            } else {
                container.feedback.duplicate()
                show(Banner.Tone.INFO, "Already scanned: ${unique.joinToString { TagIdNormalizer.short(it) }}")
            }
            return
        }

        val remaining = remainingFor(req)
        if (remaining <= 0) {
            show(Banner.Tone.BAD, "Limit reached: ${limitReason(req)}. No more tags can be scanned.")
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
        val full = added > 0 && items.value.size >= limitFor(req)
        when {
            full && overBalance == 0 -> show(Banner.Tone.OK, "All ${limitFor(req)} tags scanned. Tap Submit.")
            overBalance > 0 -> {
                show(Banner.Tone.BAD, "Added $added. $overBalance more ignored: ${limitReason(req)}.")
                container.feedback.error()
            }
            issuedBefore.isNotEmpty() -> {
                show(Banner.Tone.WARN, "Added $added. ${issuedBefore.size} skipped: already issued and not unloaded yet.")
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
                    val now = minOf(req.effectiveBalance, MAX_PER_SESSION)
                    show(Banner.Tone.OK, "Request loaded via $sourceLabel: ${req.refCode}. You can issue $now tags" +
                        if (req.effectiveBalance > MAX_PER_SESSION) " now (max $MAX_PER_SESSION per session)." else ".")
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
        header.value?.let { h ->
            h.request?.let { saveHeader(h.copy(request = withLedger(it), sessionAccepted = h.sessionAccepted + result.accepted)) }
        }

        // Everything accepted: tagging for this requisition is done. Clear the screen, ready for the next one.
        if (result.kind == SubmitSummary.Kind.ALL_ACCEPTED) {
            val req = header.value?.request
            val doneMsg = "Submitted ${result.accepted} tag(s)" + (req?.let { " to ${it.refCode} (balance now ${it.effectiveBalance})" } ?: "") +
                ". Ready for the next requisition."
            startNew { show(Banner.Tone.OK, doneMsg) }
            return
        }

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
    private fun withLedger(req: IssueRequest): IssueRequest {
        // Rule: every requisition takes exactly 10 tags. The server's NO_OF_ITEMS (now the number of item
        // lines, e.g. 1) is not the tag count, so it is logged but not used for the balance.
        // The server's issued/balance count items on a different scale (e.g. 50 items, 20 issued), so they would
        // wrongly use up the 10 tags. Only tags issued from this PDA (IssuedLedger) reduce the 10.
        if (req.required != TAGS_PER_REQUISITION || req.balance != 0 || req.issued != 0) {
            com.rfid.rolltrack.data.remote.ApiLog.info(
                "Tag Issue: ${req.refCode} server NO_OF_ITEMS=${req.required} issued=${req.issued} balance=${req.balance}; using $TAGS_PER_REQUISITION tags per requisition"
            )
        }
        return req.copy(
            required = TAGS_PER_REQUISITION,
            issued = 0,
            balance = 0,
            localIssued = container.issuedLedger.issued(req.refId)
        )
    }

    /** Tags already accepted while this requisition is open (counts toward the 10-per-session cap). */
    private val sessionAccepted: Int get() = header.value?.sessionAccepted ?: 0

    /** The most this session may still take: the balance, but never more than [MAX_PER_SESSION] in one session. */
    private fun limitFor(req: IssueRequest): Int =
        minOf(req.effectiveBalance, (MAX_PER_SESSION - sessionAccepted).coerceAtLeast(0))

    /** Says which limit stopped the operator: the requisition's balance or the 10-per-session cap. */
    private fun limitReason(req: IssueRequest): String =
        if (req.effectiveBalance <= MAX_PER_SESSION - sessionAccepted) "balance reached (${req.effectiveBalance})"
        else "maximum $MAX_PER_SESSION tags per session"

    /** Tags that may still be added: the limit minus tags on screen that are not yet accepted (scanned, failed, queued). */
    private fun remainingFor(req: IssueRequest): Int = limitFor(req) - items.value.size

    /** How many tags this requisition must be submitted with right now: exactly its balance (max 10). 0 = none. */
    fun exactTarget(): Int = header.value?.request?.let { limitFor(it) } ?: 0

    /**
     * Rule: a requisition is submitted with EXACTLY its balance, never more or less.
     * Blocks the submit otherwise and says how many to add or remove.
     */
    private fun withinBalance(req: IssueRequest, count: Int, alreadyOnScreen: Int): Boolean {
        val needed = limitFor(req) - alreadyOnScreen
        if (count == needed && needed > 0) return true
        val msg = when {
            needed <= 0 -> "Limit reached: ${limitReason(req)}. No more tags can be submitted for ${req.refCode}."
            count < needed -> "Exactly $needed tags are needed for ${req.refCode}. Scan ${needed - count} more."
            else -> "Exactly $needed tags are needed for ${req.refCode}. Remove ${count - needed}."
        }
        show(Banner.Tone.BAD, msg)
        container.feedback.error()
        return false
    }

    private companion object {
        /** Requirement: at most 10 tags can be validated in one session, whatever the server's balance says. */
        const val MAX_PER_SESSION = 10
        /** Rule: each requisition gets 10 tags in total, never more. */
        const val TAGS_PER_REQUISITION = 10
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

package com.rfid.rolltrack.ui.production

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.SubmitSummary
import com.rfid.rolltrack.data.WeightOp
import com.rfid.rolltrack.data.local.ScanItemEntity
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.data.remote.WeightItem
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.scanner.TagIdNormalizer
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** The one roll whose weight is being entered. */
data class WeightEntry(
    val tagId: String,
    /** Weight already sent for this tag from this device, if any. */
    val sentKg: Double? = null,
    /** Error shown in the weight box. */
    val error: String? = null,
    /** Weight to start the box with (when editing a roll already in the list). */
    val typed: String = ""
)

/**
 * Record weight: scan ONE roll tag, type its weight, Save. The roll is marked with its weight and stays in
 * the list; Submit sends every listed weight (ref-weight). Tags never come from Tag Issue: only rolls scanned
 * here. While a weight box is open, other reads are ignored, so a reader seeing several rolls cannot mix them up.
 */
class ProductionViewModel(container: AppContainer) :
    ScanSessionViewModel<ProductionHeader>(
        container = container,
        type = SessionType.PRODUCTION,
        headerSerializer = ProductionHeader.serializer(),
        emptyHeader = { ProductionHeader() }
    ) {

    private val _entry = MutableStateFlow<WeightEntry?>(null)
    val entry: StateFlow<WeightEntry?> = _entry.asStateFlow()

    /** The tag just saved and when, so the reader still seeing it does not reopen the box at once. */
    private var lastSavedTag: String? = null
    private var lastSavedAt = 0L

    override suspend fun onSessionRestored(id: Long) {
        // Rows without a weight come from the old list-style screen: drop them.
        repo.observeItems(id).first().filter { it.weightKg == null || it.weightKg <= 0.0 }
            .forEach { repo.removeItem(it.id) }
        if (header.value?.context != null) saveHeader(ProductionHeader())
    }

    override suspend fun onTag(event: ScanEvent.Tag) = openFor(listOf(event.tagId))

    override suspend fun onCode(event: ScanEvent.Code) {
        val reads = TagIdNormalizer.splitReads(event.raw)
        if (reads.isNotEmpty() && reads.all { TagIdNormalizer.isEpc(it) }) {
            openFor(reads.map { it.uppercase(Locale.ROOT) })
            return
        }
        show(Banner.Tone.WARN, "Not a roll tag: ${event.raw.trim().take(40)}. Scan the roll's RFID tag.")
        container.feedback.warn()
    }

    private fun openFor(tags: List<String>) {
        val unique = tags.distinct()
        if (unique.isEmpty()) return
        val open = _entry.value
        if (open != null) {
            // One roll at a time: finish or cancel the open one first.
            if (unique.any { it != open.tagId }) {
                show(Banner.Tone.WARN, "Finish ${TagIdNormalizer.short(open.tagId)} first (Save or Cancel). Other tag ignored.")
            }
            return
        }
        if (busy.value) return

        val tagId = unique.first()
        // The reader keeps reporting the roll just saved for a few seconds: don't reopen it.
        if (tagId == lastSavedTag && System.currentTimeMillis() - lastSavedAt < REREAD_QUIET_MS) return

        val listed = items.value.firstOrNull { it.tagId == tagId }
        val sent = container.weighedLedger.get(tagId)?.weightKg
        _entry.value = WeightEntry(tagId = tagId, sentKg = sent, typed = listed?.weightKg?.toString().orEmpty())
        container.feedback.tagAccepted()
        when {
            unique.size > 1 -> show(Banner.Tone.WARN, "${unique.size} tags in range. Opened ${TagIdNormalizer.short(tagId)} only; check it is the right roll.")
            listed != null -> show(Banner.Tone.INFO, "${TagIdNormalizer.short(tagId)} is already in the list. Saving changes its weight.")
            sent != null -> show(Banner.Tone.INFO, "${TagIdNormalizer.short(tagId)} was already submitted with $sent kg.")
            else -> dismissBanner()
        }
    }

    /** Tap a listed roll to change its weight. */
    fun edit(item: ScanItemEntity) {
        if (_entry.value != null || busy.value) return
        _entry.value = WeightEntry(tagId = item.tagId, sentKg = container.weighedLedger.get(item.tagId)?.weightKg, typed = item.weightKg?.toString().orEmpty())
    }

    fun cancelEntry() {
        _entry.value = null
    }

    /** Marks the roll with its weight in the list (on this PDA). Submit sends it. */
    fun saveWeight(text: String) {
        val current = _entry.value ?: return
        val weight = text.trim().replace(',', '.').toDoubleOrNull()
        if (weight == null || weight <= 0.0) {
            _entry.value = current.copy(error = "Enter the weight in kg, e.g. 24.5", typed = text)
            container.feedback.warn()
            return
        }
        viewModelScope.launch {
            val listed = items.value.firstOrNull { it.tagId == current.tagId }
            if (listed != null) {
                container.database.itemDao().setWeight(listed.id, weight)
            } else {
                addTag(ScanEvent.Tag(current.tagId, com.rfid.rolltrack.scanner.ScanSource.PDA_SCANNER), weightKg = weight)
                // The row reaches the list a moment later; wait so the count is right.
                withTimeoutOrNull(2000) { items.first { list -> list.any { it.tagId == current.tagId } } }
            }
            lastSavedTag = current.tagId
            lastSavedAt = System.currentTimeMillis()
            _entry.value = null
            show(Banner.Tone.OK, "${TagIdNormalizer.short(current.tagId)}: $weight kg marked. Scan the next roll, or tap Submit.")
        }
    }

    /** Sends every listed weight. Accepted rolls leave the list; refused ones stay, marked with the reason. */
    fun submitWeights() {
        val rows = sendable.filter { (it.weightKg ?: 0.0) > 0.0 }
        if (rows.isEmpty()) {
            show(Banner.Tone.WARN, "No weights to submit yet. Scan a roll and enter its weight.")
            container.feedback.warn()
            return
        }
        submit(WeightOp(programNo = "", items = rows.map { WeightItem(it.tagId, it.weightKg ?: 0.0) }))
    }

    override suspend fun afterSubmit(result: SubmitSummary) {
        when (result.kind) {
            SubmitSummary.Kind.ALL_ACCEPTED -> show(Banner.Tone.OK, "Submitted ${result.accepted} weight(s).")
            SubmitSummary.Kind.PARTIAL -> show(Banner.Tone.WARN, "Submitted ${result.accepted}, ${result.rejected} not saved (still in the list).")
            SubmitSummary.Kind.UNCERTAIN -> show(Banner.Tone.WARN, "Sent ${result.accepted}, but the server reply was unclear. Check in the ERP.")
            SubmitSummary.Kind.QUEUED -> show(Banner.Tone.WARN, "No connection: the weights will be sent automatically.")
            SubmitSummary.Kind.ALL_REJECTED -> {
                show(Banner.Tone.BAD, "Not saved: ${result.message}")
                container.feedback.error()
            }
        }
    }

    private companion object { const val REREAD_QUIET_MS = 4000L }
}

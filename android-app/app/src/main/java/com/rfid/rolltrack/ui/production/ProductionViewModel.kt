package com.rfid.rolltrack.ui.production

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.WeightOp
import com.rfid.rolltrack.data.local.ScanItemEntity
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.data.remote.WeightItem
import com.rfid.rolltrack.data.remote.WorkOrder
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.scanner.TagIdNormalizer
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ProductionViewModel(container: AppContainer) :
    ScanSessionViewModel<ProductionHeader>(
        container = container,
        type = SessionType.PRODUCTION,
        headerSerializer = ProductionHeader.serializer(),
        emptyHeader = { ProductionHeader() }
    ) {

    private val _workOrders = MutableStateFlow<List<WorkOrder>>(emptyList())
    val workOrders: StateFlow<List<WorkOrder>> = _workOrders.asStateFlow()

    private val _editingItem = MutableStateFlow<ScanItemEntity?>(null)
    val editingItem: StateFlow<ScanItemEntity?> = _editingItem.asStateFlow()

    init {
        searchWorkOrders("")
    }

    fun searchWorkOrders(query: String) {
        viewModelScope.launch {
            try {
                val userId = container.settings.session.value.userId.ifBlank { "1" }
                _workOrders.value = container.api.workOrders(userId, query)
            } catch (_: Exception) {}
        }
    }

    override suspend fun onTag(event: ScanEvent.Tag) {
        val ctx = header.value?.context
        if (ctx == null) {
            // A roll tag never identifies the program: pick the work order or scan the program QR first.
            show(Banner.Tone.WARN, "Roll tag read (${TagIdNormalizer.short(event.tagId)}). Pick the work order or scan the program QR first.")
            container.feedback.warn()
            return
        }

        // Program is active: this is a roll tag scan
        val success = addTag(event)
        if (success && _editingItem.value == null) {
            // The new row reaches the list a moment after it is saved; wait for it, then open its weight box.
            val item = withTimeoutOrNull(2000) {
                items.first { list -> list.any { it.tagId == event.tagId } }.first { it.tagId == event.tagId }
            }
            if (item != null && _editingItem.value == null) {
                _editingItem.value = item
            }
        }
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        val open = header.value?.context
        if (open != null) {
            show(Banner.Tone.WARN, "Program ${open.programNo} is open. Tap 'Change program' before loading ${event.raw.trim()}.")
            container.feedback.warn()
            return
        }
        openProgram(event.raw, isRfid = false)
    }

    override suspend fun onTagAdded(tagId: String) {
        val ctx = header.value?.context ?: return
        val fifoList = ctx.pendingFifo
        if (fifoList.isNotEmpty()) {
            val nextFifoTag = fifoList.firstOrNull { expected -> items.value.none { it.tagId == expected } }
            if (nextFifoTag != null && nextFifoTag != tagId) {
                show(Banner.Tone.WARN, "Not FIFO order. Oldest pending tag is $nextFifoTag")
                container.feedback.warn()
            }
        }
    }

    fun openProgram(code: String, isRfid: Boolean = false) {
        val currentSendable = sendable
        if (currentSendable.isNotEmpty()) {
            show(Banner.Tone.WARN, "Submit or clear the ${currentSendable.size} rows before opening another program")
            container.feedback.warn()
            return
        }

        _busy.value = true
        viewModelScope.launch {
            try {
                val userId = container.settings.session.value.userId.ifBlank { "1" }
                val ctx = container.api.programContext(userId, code)
                saveHeader(ProductionHeader(context = ctx))
                val sourceLabel = if (isRfid) "RFID Tag" else "Code"
                show(Banner.Tone.OK, "Program loaded via $sourceLabel: ${ctx.programNo} (${ctx.woNo})")
                container.feedback.tagAccepted()
            } catch (e: Exception) {
                show(Banner.Tone.BAD, e.message ?: "Failed to open program: $code")
                container.feedback.error()
            } finally {
                _busy.value = false
            }
        }
    }

    fun setWeight(item: ScanItemEntity, weight: Double) {
        viewModelScope.launch {
            container.database.itemDao().setWeight(item.id, weight)
            _editingItem.value = null
        }
    }

    fun openEditor(item: ScanItemEntity) {
        _editingItem.value = item
    }

    fun closeEditor() {
        _editingItem.value = null
    }

    fun submitRows() {
        val ctx = header.value?.context ?: return
        val currentSendable = sendable
        if (currentSendable.isEmpty()) {
            show(Banner.Tone.WARN, "No rolls scanned yet")
            container.feedback.warn()
            return
        }

        val missingWeight = currentSendable.filter { it.weightKg == null || it.weightKg <= 0.0 }
        if (missingWeight.isNotEmpty()) {
            show(Banner.Tone.BAD, "Weight missing for ${missingWeight.size} roll(s). Tap row to set weight.")
            container.feedback.error()
            return
        }

        val op = WeightOp(
            programNo = ctx.programNo,
            items = currentSendable.map { WeightItem(it.tagId, it.weightKg ?: 0.0) }
        )
        submit(op)
    }
}

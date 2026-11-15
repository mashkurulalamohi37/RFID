package com.rfid.rolltrack.ui.loading

import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.LoadOp
import com.rfid.rolltrack.data.SubmitSummary
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.data.remote.LoadHeader
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.launch

class LoadingViewModel(container: AppContainer) :
    ScanSessionViewModel<LoadingHeader>(
        container = container,
        type = SessionType.LOADING,
        headerSerializer = LoadingHeader.serializer(),
        emptyHeader = { LoadingHeader() }
    ) {

    override suspend fun onTag(event: ScanEvent.Tag) {
        val currentHeader = header.value?.load
        if (currentHeader == null || currentHeader.vehicleNo.isBlank()) {
            // WORKFLOW 3 (Loading): Tap Vehicle RFID card to set Vehicle ID
            setVehicleRfid(event.tagId)
            return
        }

        // Vehicle is set: this is a roll tag scan
        addTag(event)
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        val current = header.value ?: LoadingHeader()
        if (current.load.vehicleNo.isBlank()) {
            setFields(event.raw, current.load.challanRef, current.load.supplier)
        } else {
            show(Banner.Tone.INFO, "Scanned code: ${event.raw}")
        }
    }

    fun setVehicleRfid(tagId: String) {
        val current = header.value ?: LoadingHeader()
        saveHeader(current.copy(load = current.load.copy(vehicleNo = tagId)))
        show(Banner.Tone.OK, "Vehicle assigned via RFID: $tagId")
        container.feedback.tagAccepted()
    }

    fun setFields(vehicleNo: String, challanRef: String, supplier: String) {
        val current = header.value ?: LoadingHeader()
        saveHeader(current.copy(load = LoadHeader(vehicleNo.trim(), challanRef.trim(), supplier.trim())))
    }

    fun submitBatch() {
        val current = header.value ?: return
        if (current.load.vehicleNo.isBlank()) {
            show(Banner.Tone.WARN, "Tap Vehicle RFID card or enter vehicle number first")
            container.feedback.warn()
            return
        }

        val currentSendable = sendable
        if (currentSendable.isEmpty()) {
            show(Banner.Tone.WARN, "No rolls scanned yet")
            container.feedback.warn()
            return
        }

        val op = LoadOp(
            header = current.load,
            tags = currentSendable.map { it.tagId }
        )
        submit(op)
    }

    override suspend fun afterSubmit(result: SubmitSummary) {
        val ref = result.details["LOADING_REF"]
        if (!ref.isNullOrBlank()) {
            val current = header.value ?: LoadingHeader()
            val refs = (current.loadingRefs + ref).distinct()
            saveHeader(current.copy(loadingRefs = refs))
        }
    }

    fun finishVehicle() {
        startNew()
    }
}

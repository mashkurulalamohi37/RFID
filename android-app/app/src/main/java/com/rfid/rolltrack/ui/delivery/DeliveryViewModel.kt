package com.rfid.rolltrack.ui.delivery

import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.DeliveryOp
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.common.ScanSessionViewModel
import com.rfid.rolltrack.ui.components.Banner

class DeliveryViewModel(container: AppContainer) :
    ScanSessionViewModel<DeliveryHeader>(
        container = container,
        type = SessionType.DELIVERY,
        headerSerializer = DeliveryHeader.serializer(),
        emptyHeader = { DeliveryHeader() }
    ) {

    override suspend fun onTag(event: ScanEvent.Tag) {
        val current = header.value ?: DeliveryHeader()
        if (current.tubeTag.isBlank()) {
            setTube(event.tagId)
            return
        }
        addTag(event)
    }

    override suspend fun onCode(event: ScanEvent.Code) {
        setTube(event.raw)
    }

    fun setTube(tagId: String) {
        val current = header.value ?: DeliveryHeader()
        saveHeader(current.copy(tubeTag = tagId))
        show(Banner.Tone.OK, "Tube tag assigned via RFID: $tagId")
        container.feedback.tagAccepted()
    }

    fun setStore(store: String) {
        val current = header.value ?: DeliveryHeader()
        saveHeader(current.copy(storeCode = store.trim()))
    }

    fun submitTube() {
        val current = header.value ?: return
        if (current.tubeTag.isBlank()) {
            show(Banner.Tone.WARN, "Tap or scan tube RFID tag first")
            container.feedback.warn()
            return
        }

        val currentSendable = sendable
        if (currentSendable.isEmpty()) {
            show(Banner.Tone.WARN, "No rolls scanned into tube yet")
            container.feedback.warn()
            return
        }

        val op = DeliveryOp(
            storeCode = current.storeCode,
            tubeTag = current.tubeTag,
            tags = currentSendable.map { it.tagId }
        )
        submit(op)
    }

    fun nextTube() {
        startNew()
    }
}

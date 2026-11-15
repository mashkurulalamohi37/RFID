package com.rfid.rolltrack.scanner

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ScanHub(
    var byteOrder: TagIdNormalizer.ByteOrder = TagIdNormalizer.ByteOrder.LITTLE_ENDIAN
) {
    private val _events = MutableSharedFlow<ScanEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    fun publish(event: ScanEvent) {
        _events.tryEmit(event)
    }

    fun publishNfcUid(bytes: ByteArray) {
        val canonicalId = TagIdNormalizer.fromUidBytes(bytes)
        _events.tryEmit(ScanEvent.Tag(canonicalId, ScanSource.NFC_READER))
    }

    fun publishText(text: String, source: ScanSource) {
        val event = classify(text, source, byteOrder)
        _events.tryEmit(event)
    }

    companion object {
        fun classify(text: String, source: ScanSource, byteOrder: TagIdNormalizer.ByteOrder): ScanEvent {
            val normalizedTag = TagIdNormalizer.fromRaw(text, byteOrder)
            return if (normalizedTag != null) {
                ScanEvent.Tag(normalizedTag, source)
            } else {
                ScanEvent.Code(text.trim(), source)
            }
        }
    }
}

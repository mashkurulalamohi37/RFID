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
        com.rfid.rolltrack.data.remote.ApiLog.info(
            "scan raw (NFC): uid=$canonicalId (${bytes.size} bytes) " +
                TagIdNormalizer.allFormats(canonicalId).joinToString { "${it.first}=${it.second}" }
        )
        _events.tryEmit(ScanEvent.Tag(canonicalId, ScanSource.NFC_READER))
    }

    fun publishText(text: String, source: ScanSource) {
        com.rfid.rolltrack.data.remote.ApiLog.info("scan raw (${source.label}): ${text.replace("\n", "\n").replace("\r", "\r")}")
        // The Seuic UHF service sends one or more roll-tag EPCs, one per line. Each becomes its own Tag
        // event, so every screen sees a roll tag as a roll tag (never as a program, vehicle or tube code).
        val lines = TagIdNormalizer.splitReads(text)
        if (lines.isNotEmpty() && lines.all { TagIdNormalizer.isEpc(it) }) {
            lines.map { it.uppercase(java.util.Locale.ROOT) }.distinct().forEach { epc ->
                _events.tryEmit(ScanEvent.Tag(epc, source))
            }
            return
        }
        // Other multi-read bursts (e.g. NFC UIDs joined by commas): split when every part is a tag.
        val tokens = text.split('\n', '\r', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            val events = tokens.map { classify(it, source, byteOrder) }
            if (events.all { it is ScanEvent.Tag }) {
                events.forEach { _events.tryEmit(it) }
                return
            }
        }
        _events.tryEmit(classify(text, source, byteOrder))
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

package com.rfid.rolltrack.scanner

sealed class ScanEvent {
    data class Tag(
        val tagId: String,
        val source: ScanSource,
        val timestamp: Long = System.currentTimeMillis()
    ) : ScanEvent()

    data class Code(
        val raw: String,
        val source: ScanSource,
        val timestamp: Long = System.currentTimeMillis()
    ) : ScanEvent()
}

package com.rfid.rolltrack.scanner

enum class ScanSource(val label: String) {
    NFC_READER("Tablet NFC"),
    USB_READER("USB Reader"),
    PDA_SCANNER("PDA Scanner"),
    KEYBOARD("Keyboard"),
    MANUAL("Manual Entry")
}

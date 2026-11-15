package com.rfid.rolltrack.ui.components

data class Banner(
    val tone: Tone,
    val text: String
) {
    enum class Tone {
        OK,
        WARN,
        BAD,
        INFO
    }
}

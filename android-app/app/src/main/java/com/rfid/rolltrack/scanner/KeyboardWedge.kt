package com.rfid.rolltrack.scanner

import android.view.KeyEvent

class KeyboardWedge(
    private val onComplete: (String) -> Unit
) {
    private val buffer = StringBuilder()
    private var lastKeyTime = 0L

    fun handle(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val now = System.currentTimeMillis()
        if (now - lastKeyTime > 500 && buffer.isNotEmpty()) {
            buffer.clear()
        }
        lastKeyTime = now

        if (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (buffer.isNotEmpty()) {
                val scanned = buffer.toString()
                buffer.clear()
                onComplete(scanned)
                return true
            }
            return false
        }

        val unicode = event.unicodeChar
        if (unicode > 0) {
            val c = unicode.toChar()
            if (c.isLetterOrDigit() || c == '-' || c == ':' || c == '_' || c == '/') {
                buffer.append(c)
                return true
            }
        }

        return false
    }
}

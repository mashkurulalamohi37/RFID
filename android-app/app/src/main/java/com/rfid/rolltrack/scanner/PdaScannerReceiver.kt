package com.rfid.rolltrack.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class PdaScannerReceiver(
    private val onScanned: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        val scanString = intent.getStringExtra("scannerdata")
            ?: intent.getStringExtra("data")
            ?: intent.getStringExtra("barcode")
            ?: intent.getStringExtra("barcode_string")
            ?: intent.getStringExtra("se_barcode")
            ?: intent.getStringExtra("value")
            ?: intent.getStringExtra("scanner_data")
            ?: intent.getStringExtra("com.symbol.datawedge.data_string")
            ?: intent.getByteArrayExtra("scannerdata")?.let { bytes ->
                val len = intent.getIntExtra("length", bytes.size).coerceIn(0, bytes.size)
                String(bytes, 0, len, Charsets.UTF_8).trim()
            }
            ?: intent.getByteArrayExtra("barcode")?.let { bytes ->
                String(bytes, Charsets.UTF_8).trim()
            }
            ?: intent.getByteArrayExtra("se_barcode")?.let { bytes ->
                String(bytes, Charsets.UTF_8).trim()
            }

        if (!scanString.isNullOrBlank()) {
            onScanned(scanString)
        }
    }

    companion object {
        // Targeted for Seuic CRUISE GeP and common rugged industrial PDAs
        private val SCAN_ACTIONS = listOf(
            "com.android.server.scannerservice.broadcast", // Seuic CRUISE GeP primary action
            "com.seuic.scanner.action.RESULT",
            "com.seuic.scanservice",
            "android.intent.action.SCANRESULT",
            "android.intent.ACTION_DECODE_DATA",
            "com.symbol.datawedge.api.RESULT_ACTION",
            "com.rfid.SCAN",
            "urovo.rcv.action.GENERIC"
        )

        fun register(context: Context, receiver: PdaScannerReceiver) {
            val filter = IntentFilter()
            for (action in SCAN_ACTIONS) {
                filter.addAction(action)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }

        fun unregister(context: Context, receiver: PdaScannerReceiver) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }
}

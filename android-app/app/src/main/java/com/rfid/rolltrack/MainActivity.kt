package com.rfid.rolltrack

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rfid.rolltrack.scanner.KeyboardWedge
import com.rfid.rolltrack.scanner.PdaScannerReceiver
import com.rfid.rolltrack.scanner.ScanSource
import com.rfid.rolltrack.ui.RollTrackNavHost
import com.rfid.rolltrack.ui.theme.RollTrackTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private val container: AppContainer by lazy {
        (application as RollTrackApp).container
    }

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var wedge: KeyboardWedge
    private lateinit var pdaScanner: PdaScannerReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        wedge = KeyboardWedge { text ->
            container.scanHub.publishText(text, ScanSource.USB_READER)
        }

        pdaScanner = PdaScannerReceiver { text ->
            container.scanHub.publishText(text, ScanSource.PDA_SCANNER)
        }

        setContent {
            RollTrackTheme {
                RollTrackNavHost(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PdaScannerReceiver.register(this, pdaScanner)

        nfcAdapter?.let { adapter ->
            // All NFC families: yellow key-fobs are often NFC-V (ISO 15693) or NFC-B, not only MIFARE (NFC-A).
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            val extras = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }
            adapter.enableReaderMode(this, this, flags, extras)
        }
    }

    override fun onPause() {
        super.onPause()
        PdaScannerReceiver.unregister(this, pdaScanner)
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        com.rfid.rolltrack.data.remote.ApiLog.info("scan raw (NFC): techs=${tag.techList.joinToString { it.substringAfterLast('.') }}")
        val idBytes = tag.id ?: return
        container.scanHub.publishNfcUid(idBytes)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // USB keyboard-style tag reader capture is switched OFF: on this PDA the UHF trigger, barcode scanner and
        // NFC do not send keystrokes, and capturing keys swallowed typing in text fields. To re-enable for a
        // USB reader, set USB_READER_CAPTURE = true.
        if (USB_READER_CAPTURE && wedge.handle(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private const val USB_READER_CAPTURE = false
    }
}

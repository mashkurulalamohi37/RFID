package com.rfid.rolltrack

import android.content.Context
import androidx.room.Room
import com.rfid.rolltrack.config.AppSettings
import com.rfid.rolltrack.config.SettingsRepository
import com.rfid.rolltrack.data.ScanRepository
import com.rfid.rolltrack.data.local.AppDatabase
import com.rfid.rolltrack.data.remote.ApiLog
import com.rfid.rolltrack.data.remote.DemoErpApi
import com.rfid.rolltrack.data.remote.HttpTransport
import com.rfid.rolltrack.data.remote.OrdsErpApi
import com.rfid.rolltrack.data.remote.SwitchingErpApi
import com.rfid.rolltrack.scanner.Feedback
import com.rfid.rolltrack.scanner.ScanHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppContainer(val context: Context) {

    val settings = SettingsRepository(context)

    @Volatile
    private var cachedSettings = AppSettings()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http = HttpTransport()

    val demoApi = DemoErpApi()

    val ordsApi = OrdsErpApi(
        http = http,
        getBaseUrl = { cachedSettings.baseUrl },
        getFormat = { cachedSettings.erpFormat }
    )

    val api = SwitchingErpApi(
        ordsApi = ordsApi,
        demoApi = demoApi,
        currentSettings = { cachedSettings }
    )

    val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "rolltrack.db"
    ).fallbackToDestructiveMigration().build()

    val network = NetworkMonitor(context)

    val issuedLedger = com.rfid.rolltrack.data.IssuedLedger(context)
    val weighedLedger = com.rfid.rolltrack.data.WeighedLedger(context)

    val scanRepository = ScanRepository(
        db = database,
        api = api,
        getUserId = { settings.session.value.userId },
        isOnline = { network.online.value && !cachedSettings.simulateOffline },
        ledger = issuedLedger,
        weighed = weighedLedger
    )

    val serverHealth = ServerHealth(
        http = http,
        network = network,
        baseUrl = { cachedSettings.baseUrl },
        repo = { scanRepository }
    )

    val scanHub = ScanHub(cachedSettings.usbByteOrder)

    val feedback = Feedback(context)

    init {
        try {
            cachedSettings = runBlocking { settings.settings.first() }
            runBlocking { settings.restoreSession() }
            scanHub.byteOrder = cachedSettings.usbByteOrder
        } catch (_: Exception) {}
        ApiLog.info("RollTrack start: baseUrl=${cachedSettings.baseUrl} demo=${cachedSettings.demoMode} simulateOffline=${cachedSettings.simulateOffline}")
        // Keep the API layer in sync with Settings (previously only read once at startup).
        scope.launch {
            settings.settings.collect { s ->
                if (s != cachedSettings) {
                    ApiLog.info("settings changed: baseUrl=${s.baseUrl} demo=${s.demoMode} simulateOffline=${s.simulateOffline}")
                    cachedSettings = s
                    scanHub.byteOrder = s.usbByteOrder
                    serverHealth.check()
                }
            }
        }
        serverHealth.start()
    }
}

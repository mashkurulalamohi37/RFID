package com.rfid.rolltrack

import android.content.Context
import androidx.room.Room
import com.rfid.rolltrack.config.AppSettings
import com.rfid.rolltrack.config.SettingsRepository
import com.rfid.rolltrack.data.ScanRepository
import com.rfid.rolltrack.data.local.AppDatabase
import com.rfid.rolltrack.data.remote.DemoErpApi
import com.rfid.rolltrack.data.remote.HttpTransport
import com.rfid.rolltrack.data.remote.OrdsErpApi
import com.rfid.rolltrack.data.remote.SwitchingErpApi
import com.rfid.rolltrack.scanner.Feedback
import com.rfid.rolltrack.scanner.ScanHub
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppContainer(val context: Context) {

    val settings = SettingsRepository(context)

    private var cachedSettings = AppSettings()

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

    val scanRepository = ScanRepository(
        db = database,
        api = api,
        getUserId = { settings.session.value.userId }
    )

    val scanHub = ScanHub(cachedSettings.usbByteOrder)

    val feedback = Feedback(context)

    val network = NetworkMonitor(context)

    init {
        try {
            cachedSettings = runBlocking { settings.settings.first() }
            scanHub.byteOrder = cachedSettings.usbByteOrder
        } catch (_: Exception) {}
    }
}

package com.rfid.rolltrack.config

import com.rfid.rolltrack.scanner.TagIdNormalizer
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val baseUrl: String = "http://118.179.223.40:7007/ords/",
    val demoMode: Boolean = false,
    val erpFormat: TagIdNormalizer.ErpFormat = TagIdNormalizer.ErpFormat.DECIMAL_LE,
    val usbByteOrder: TagIdNormalizer.ByteOrder = TagIdNormalizer.ByteOrder.LITTLE_ENDIAN,
    val simulateOffline: Boolean = false,
    val deviceModel: String = "Model CRUISE GeP",
    val serialNumber: String = "547317bce70fa31c",
    val hardwareVersion: String = "110",
    val deviceId: String = "C16260702213",
    val functionModel: String = "2.0M8-16.0-4/64-X3PC(HD)GC13/5"
)

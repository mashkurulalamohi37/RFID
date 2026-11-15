package com.rfid.rolltrack.ui.delivery

import kotlinx.serialization.Serializable

@Serializable
data class DeliveryHeader(
    val storeCode: String = "MAIN",
    val tubeTag: String = ""
)

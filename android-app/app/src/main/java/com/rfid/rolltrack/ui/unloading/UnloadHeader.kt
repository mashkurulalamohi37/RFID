package com.rfid.rolltrack.ui.unloading

import com.rfid.rolltrack.data.remote.ExpectedList
import kotlinx.serialization.Serializable

@Serializable
data class UnloadHeader(
    val loadingRef: String = "",
    val expected: ExpectedList? = null
)

package com.rfid.rolltrack.ui.production

import com.rfid.rolltrack.data.remote.ProgramContext
import kotlinx.serialization.Serializable

@Serializable
data class ProductionHeader(
    val context: ProgramContext? = null
)

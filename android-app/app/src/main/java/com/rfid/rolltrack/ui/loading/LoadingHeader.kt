package com.rfid.rolltrack.ui.loading

import com.rfid.rolltrack.data.remote.LoadHeader
import kotlinx.serialization.Serializable

@Serializable
data class LoadingHeader(
    val load: LoadHeader = LoadHeader(),
    val loadingRefs: List<String> = emptyList()
)

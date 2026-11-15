package com.rfid.rolltrack.ui.issue

import com.rfid.rolltrack.data.remote.IssueRequest
import kotlinx.serialization.Serializable

@Serializable
data class IssueHeader(
    val request: IssueRequest? = null,
    val countOnly: Boolean = false
)

package com.rfid.rolltrack.ui.issue

import com.rfid.rolltrack.data.remote.IssueRequest
import kotlinx.serialization.Serializable

@Serializable
data class IssueHeader(
    val request: IssueRequest? = null,
    val countOnly: Boolean = false,
    /** Tags the server accepted while this requisition was open (max 10 per session). */
    val sessionAccepted: Int = 0
)

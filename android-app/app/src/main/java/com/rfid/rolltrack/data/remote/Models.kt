package com.rfid.rolltrack.data.remote

import kotlinx.serialization.Serializable

class ApiException(
    val code: String? = null,
    message: String,
    val httpCode: Int = 0,
    cause: Throwable? = null
) : Exception(message, cause)

@Serializable
data class LoginResult(
    val userId: String,
    val displayName: String
)

@Serializable
data class IssueRequest(
    val refId: String = "",
    val refCode: String,
    val supplier: String = "",
    val program: String = "",
    val workOrder: String = "",
    val item: String = "",
    val color: String = "",
    val required: Int = 0,
    val issued: Int = 0,
    val balance: Int = 0
) {
    val effectiveBalance: Int get() = if (balance > 0) balance else (required - issued).coerceAtLeast(0)
}

@Serializable
data class ProgramContext(
    val programNo: String,
    val woNo: String,
    val supplier: String = "",
    val supervisor: String = "",
    val eligibleTags: List<String> = emptyList(),
    val pendingFifo: List<String> = emptyList()
)

@Serializable
data class WorkOrder(
    val woNo: String,
    val programNo: String,
    val unit: String = "",
    val location: String = "",
    val supplier: String = ""
)

@Serializable
data class LoadHeader(
    val vehicleNo: String = "",
    val challanRef: String = "",
    val supplier: String = ""
)

@Serializable
data class ExpectedList(
    val loadingRef: String,
    val tagIds: List<String> = emptyList(),
    val rollNos: Map<String, String> = emptyMap()
)

@Serializable
data class WeightItem(
    val item: String,
    val weight: Double
)

@Serializable
data class ItemOutcome(
    val tagId: String,
    val accepted: Boolean,
    val code: String? = null,
    val message: String? = null,
    val rollNo: String? = null,
    val notSent: Boolean = false
)

@Serializable
data class ResultTable(
    val title: String = "",
    val rows: List<List<String>> = emptyList()
)

@Serializable
data class BulkResult(
    val ok: Boolean,
    val message: String = "",
    val items: List<ItemOutcome> = emptyList(),
    val details: Map<String, String> = emptyMap(),
    val table: ResultTable? = null
)

@Serializable
data class TraceEvent(
    val time: String,
    val step: String,
    val detail: String = ""
)

@Serializable
data class TraceResult(
    val tagId: String,
    val rollNo: String = "",
    val status: String = "",
    val weightKg: Double? = null,
    val qrToken: String? = null,
    val program: String? = null,
    val supplier: String? = null,
    val events: List<TraceEvent> = emptyList()
)

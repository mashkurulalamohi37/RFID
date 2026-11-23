package com.rfid.rolltrack.data

import com.rfid.rolltrack.data.remote.LoadHeader
import com.rfid.rolltrack.data.remote.ResultTable
import com.rfid.rolltrack.data.remote.WeightItem
import kotlinx.serialization.Serializable

@Serializable
sealed class SubmitOp {
    abstract val itemCount: Int
}

@Serializable
data class IssueOp(
    val refId: String,
    val countOnly: Boolean,
    val count: Int,
    val tags: List<String>
) : SubmitOp() {
    override val itemCount: Int get() = if (countOnly) count else tags.size
}

@Serializable
data class WeightOp(
    val programNo: String,
    val items: List<WeightItem>
) : SubmitOp() {
    override val itemCount: Int get() = items.size
}

@Serializable
data class LoadOp(
    val header: LoadHeader,
    val tags: List<String>
) : SubmitOp() {
    override val itemCount: Int get() = tags.size
}

@Serializable
data class UnloadOp(
    val loadingRef: String,
    val tags: List<String>
) : SubmitOp() {
    override val itemCount: Int get() = tags.size
}

@Serializable
data class DeliveryOp(
    val storeCode: String,
    val tubeTag: String,
    val tags: List<String>
) : SubmitOp() {
    override val itemCount: Int get() = tags.size
}

@Serializable
data class SubmitSummary(
    val kind: Kind,
    val message: String,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val details: Map<String, String> = emptyMap(),
    val table: ResultTable? = null,
    val httpCode: Int = 0,
    val raw: String = "",
    val errorCode: String? = null,
    val at: Long = System.currentTimeMillis()
) {
    enum class Kind {
        ALL_ACCEPTED,
        PARTIAL,
        ALL_REJECTED,
        QUEUED,
        UNCERTAIN
    }
}

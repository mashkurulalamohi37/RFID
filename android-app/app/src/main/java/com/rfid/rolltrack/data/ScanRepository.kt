package com.rfid.rolltrack.data

import com.rfid.rolltrack.data.local.AppDatabase
import com.rfid.rolltrack.data.local.ItemState
import com.rfid.rolltrack.data.local.PendingSubmitEntity
import com.rfid.rolltrack.data.local.ScanItemEntity
import com.rfid.rolltrack.data.local.ScanSessionEntity
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.data.remote.ApiException
import com.rfid.rolltrack.data.remote.BulkResult
import com.rfid.rolltrack.data.remote.ErpApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ScanRepository(
    private val db: AppDatabase,
    private val api: ErpApi,
    private val getUserId: () -> String
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun openSession(type: SessionType, userId: String): ScanSessionEntity {
        val existing = db.sessionDao().findOpen(type, userId)
        if (existing != null) return existing

        val newSession = ScanSessionEntity(type = type, userId = userId)
        val id = db.sessionDao().insert(newSession)
        return db.sessionDao().get(id) ?: newSession.copy(id = id)
    }

    fun observeSession(id: Long): Flow<ScanSessionEntity> {
        return db.sessionDao().observe(id).filterNotNull()
    }

    fun observeItems(sessionId: Long): Flow<List<ScanItemEntity>> {
        return db.itemDao().observe(sessionId)
    }

    suspend fun addItem(item: ScanItemEntity): Long {
        return db.itemDao().insert(item)
    }

    suspend fun removeItem(id: Long) {
        db.itemDao().delete(id)
    }

    suspend fun closeSession(id: Long) {
        db.sessionDao().close(id)
    }

    suspend fun saveHeader(sessionId: Long, headerJson: String?) {
        db.sessionDao().setHeader(sessionId, headerJson)
    }

    fun observePendingCount(): Flow<Int> {
        return db.pendingDao().observeCount()
    }

    fun observePending(): Flow<List<PendingSubmitEntity>> {
        return db.pendingDao().observeAll()
    }

    suspend fun submit(
        sessionId: Long,
        op: SubmitOp,
        itemIds: List<Long>
    ): SubmitSummary {
        val txnId = UUID.randomUUID().toString()
        val userId = getUserId()

        if (itemIds.isNotEmpty()) {
            db.itemDao().markSending(itemIds, ItemState.SENDING, txnId)
        }

        try {
            val result = executeOp(op, userId, txnId)
            return handleSuccess(sessionId, op, itemIds, txnId, result)
        } catch (e: ApiException) {
            return handleFailure(sessionId, op, itemIds, txnId, e)
        } catch (e: Exception) {
            val apiEx = ApiException(code = "UNKNOWN", message = e.message ?: "Submission failed", cause = e)
            return handleFailure(sessionId, op, itemIds, txnId, apiEx)
        }
    }

    private suspend fun executeOp(op: SubmitOp, userId: String, txnId: String): BulkResult {
        return when (op) {
            is IssueOp -> api.issueTags(userId, op.refId, if (op.countOnly) op.count else null, op.tags, txnId)
            is WeightOp -> api.saveWeights(userId, op.programNo, op.items, txnId)
            is LoadOp -> api.load(userId, op.header, op.tags, txnId)
            is UnloadOp -> api.unload(userId, op.loadingRef, op.tags, txnId)
            is DeliveryOp -> api.delivery(userId, op.storeCode, op.tubeTag, op.tags, txnId)
        }
    }

    private suspend fun handleSuccess(
        sessionId: Long,
        op: SubmitOp,
        itemIds: List<Long>,
        txnId: String,
        result: BulkResult
    ): SubmitSummary {
        val itemsMap = result.items.associateBy { it.tagId }
        val allItems = if (itemIds.isNotEmpty()) db.itemDao().byTxn(txnId) else emptyList()

        var acceptedCount = 0
        var rejectedCount = 0

        for (item in allItems) {
            val outcome = itemsMap[item.tagId]
            if (outcome == null || outcome.accepted) {
                acceptedCount++
                db.itemDao().delete(item.id) // Accepted rows disappear per UX spec
            } else {
                rejectedCount++
                db.itemDao().setResult(item.id, ItemState.FAILED, outcome.code, outcome.message)
            }
        }

        val kind = when {
            rejectedCount == 0 -> SubmitSummary.Kind.ALL_ACCEPTED
            acceptedCount == 0 -> SubmitSummary.Kind.ALL_REJECTED
            else -> SubmitSummary.Kind.PARTIAL
        }

        return SubmitSummary(
            kind = kind,
            message = result.message.ifBlank { "Accepted: $acceptedCount, Rejected: $rejectedCount" },
            accepted = acceptedCount,
            rejected = rejectedCount,
            details = result.details,
            table = result.table
        )
    }

    private suspend fun handleFailure(
        sessionId: Long,
        op: SubmitOp,
        itemIds: List<Long>,
        txnId: String,
        e: ApiException
    ): SubmitSummary {
        // Enqueue to pending_submit
        val pending = PendingSubmitEntity(
            txnId = txnId,
            sessionId = sessionId,
            type = when (op) {
                is IssueOp -> SessionType.ISSUE
                is WeightOp -> SessionType.PRODUCTION
                is LoadOp -> SessionType.LOADING
                is UnloadOp -> SessionType.UNLOADING
                is DeliveryOp -> SessionType.DELIVERY
            },
            opJson = json.encodeToString(op),
            itemCount = op.itemCount,
            status = "QUEUED",
            lastError = e.message
        )
        db.pendingDao().upsert(pending)
        if (itemIds.isNotEmpty()) {
            db.itemDao().setStateByTxn(txnId, ItemState.PENDING, e.message)
        }

        return SubmitSummary(
            kind = SubmitSummary.Kind.QUEUED,
            message = "Offline or server unreachable. Batch queued to Pending.",
            accepted = 0,
            rejected = op.itemCount
        )
    }
}

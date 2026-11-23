package com.rfid.rolltrack.data

import com.rfid.rolltrack.data.local.AppDatabase
import com.rfid.rolltrack.data.local.ItemState
import com.rfid.rolltrack.data.local.PendingSubmitEntity
import com.rfid.rolltrack.data.local.ScanItemEntity
import com.rfid.rolltrack.data.local.ScanSessionEntity
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.data.remote.ApiException
import com.rfid.rolltrack.data.remote.ApiLog
import com.rfid.rolltrack.data.remote.BulkResult
import com.rfid.rolltrack.data.remote.ErpApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ScanRepository(
    private val db: AppDatabase,
    private val api: ErpApi,
    private val getUserId: () -> String,
    /** Device has a network connection. Checked before every submit. */
    private val isOnline: () -> Boolean = { true }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val retryLock = Mutex()

    suspend fun openSession(type: SessionType, userId: String): ScanSessionEntity {
        // Recover rows stuck from an earlier run (app killed mid-send, or batch dropped from the queue).
        retryLock.withLock {
            db.itemDao().resetStuckSending()
            db.itemDao().releaseOrphanPending()
        }
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
        ApiLog.info("submit ${op::class.simpleName} txn=$txnId user=$userId items=${op.itemCount}")
        if (userId.isBlank()) {
            return SubmitSummary(kind = SubmitSummary.Kind.ALL_REJECTED, message = "Not logged in - please sign in again", errorCode = "NO_USER")
        }

        if (itemIds.isNotEmpty()) {
            db.itemDao().markSending(itemIds, ItemState.SENDING, txnId)
        }

        if (!isOnline()) {
            return queue(sessionId, op, itemIds, txnId, ApiException(code = "OFFLINE", message = "No internet connection on the device"))
        }

        return try {
            val result = executeOp(op, userId, txnId)
            handleSuccess(op, itemIds, txnId, result)
        } catch (e: ApiException) {
            if (e.isNetwork) queue(sessionId, op, itemIds, txnId, e) else handleRejected(itemIds, txnId, e)
        } catch (e: Exception) {
            ApiLog.warn("submit crashed: ${e.javaClass.simpleName}: ${e.message}")
            handleRejected(itemIds, txnId, ApiException(code = "APP_ERROR", message = "App error: ${e.message ?: e.javaClass.simpleName}", cause = e))
        }
    }

    /**
     * Re-sends every queued batch. Called when the server becomes reachable again and from Diagnostics.
     * @return human-readable outcome.
     */
    suspend fun retryPending(): String = retryLock.withLock {
        val queued = db.pendingDao().byStatus("QUEUED")
        if (queued.isEmpty()) return "Nothing pending"
        if (getUserId().isBlank()) return "Not logged in - ${queued.size} batch(es) wait until login"
        if (!isOnline()) return "Still offline - ${queued.size} batch(es) waiting"
        var sent = 0
        var rejected = 0
        var waiting = 0
        for (p in queued) {
            val op = try { json.decodeFromString<SubmitOp>(p.opJson) } catch (e: Exception) {
                ApiLog.warn("pending ${p.txnId}: cannot decode op, dropping (${e.message})")
                db.pendingDao().delete(p.txnId); continue
            }
            try {
                val r = executeOp(op, getUserId(), p.txnId)
                db.itemDao().byTxn(p.txnId).forEach { db.itemDao().delete(it.id) }
                db.pendingDao().delete(p.txnId)
                ApiLog.info("pending ${p.txnId} sent: ${r.message}")
                sent++
            } catch (e: ApiException) {
                if (e.isNetwork) {
                    db.pendingDao().upsert(p.copy(attempts = p.attempts + 1, lastError = e.message, updatedAt = System.currentTimeMillis()))
                    waiting++
                    break // server still unreachable; stop hammering it
                } else {
                    db.itemDao().setStateByTxn(p.txnId, ItemState.FAILED, e.message)
                    db.pendingDao().delete(p.txnId)
                    ApiLog.warn("pending ${p.txnId} rejected by server: ${e.message}")
                    rejected++
                }
            }
        }
        val left = db.pendingDao().byStatus("QUEUED").size
        "Sent $sent, rejected $rejected, still waiting $left" + if (waiting > 0) " (server unreachable)" else ""
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
        op: SubmitOp,
        itemIds: List<Long>,
        txnId: String,
        result: BulkResult
    ): SubmitSummary {
        // ERP answered but refused the batch (STATUS "E"): keep every row, marked FAILED with the reason,
        // so the operator can see it, fix it and resubmit. Nothing is queued: retrying would be refused again.
        if (!result.ok) {
            if (itemIds.isNotEmpty()) {
                db.itemDao().setStateByTxn(txnId, ItemState.FAILED, result.message)
            }
            return SubmitSummary(
                kind = SubmitSummary.Kind.ALL_REJECTED,
                message = "Rejected by ERP: ${result.message.ifBlank { "no reason given" }}",
                accepted = 0,
                rejected = op.itemCount,
                details = result.details,
                table = result.table
            )
        }

        // Weights are keyed by roll item; tags by tag id.
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
        if (allItems.isEmpty()) acceptedCount = op.itemCount

        val kind = when {
            result.uncertain -> SubmitSummary.Kind.UNCERTAIN
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
            table = result.table,
            httpCode = result.httpCode,
            raw = result.raw
        )
    }

    /** Server answered and said no (HTTP error, -1/-2/-3, STATUS=E). Items stay on screen as FAILED so they can be resent. */
    private suspend fun handleRejected(itemIds: List<Long>, txnId: String, e: ApiException): SubmitSummary {
        if (itemIds.isNotEmpty()) {
            db.itemDao().setStateByTxn(txnId, ItemState.FAILED, e.message)
        }
        return SubmitSummary(
            kind = SubmitSummary.Kind.ALL_REJECTED,
            message = e.message ?: "Rejected by server",
            accepted = 0,
            rejected = itemIds.size,
            httpCode = e.httpCode,
            raw = e.raw.orEmpty(),
            errorCode = e.code
        )
    }

    /** No answer from the server (offline / timeout). Batch is saved and retried automatically. */
    private suspend fun queue(
        sessionId: Long,
        op: SubmitOp,
        itemIds: List<Long>,
        txnId: String,
        e: ApiException
    ): SubmitSummary {
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
        ApiLog.warn("queued txn=$txnId: ${e.message}")

        return SubmitSummary(
            kind = SubmitSummary.Kind.QUEUED,
            message = "Not sent: ${e.message}. Saved to Pending, will resend when the server is reachable.",
            accepted = 0,
            rejected = op.itemCount,
            errorCode = e.code
        )
    }
}

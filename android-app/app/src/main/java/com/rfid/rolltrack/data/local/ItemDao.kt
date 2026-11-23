package com.rfid.rolltrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ScanItemEntity): Long

    @Upsert
    suspend fun upsert(item: ScanItemEntity)

    @Query("SELECT * FROM scan_item WHERE sessionId = :sessionId ORDER BY scannedAt DESC")
    fun observe(sessionId: Long): Flow<List<ScanItemEntity>>

    @Query("SELECT * FROM scan_item WHERE sessionId = :sessionId")
    suspend fun list(sessionId: Long): List<ScanItemEntity>

    @Query("SELECT * FROM scan_item WHERE txnId = :txnId")
    suspend fun byTxn(txnId: String): List<ScanItemEntity>

    @Query("UPDATE scan_item SET weightKg = :weight WHERE id = :id")
    suspend fun setWeight(id: Long, weight: Double?)

    @Query("UPDATE scan_item SET state = :state, txnId = :txnId, errorCode = NULL, errorMessage = NULL WHERE id IN (:ids)")
    suspend fun markSending(ids: List<Long>, state: ItemState, txnId: String?)

    @Query("UPDATE scan_item SET state = :state, errorCode = :code, errorMessage = :message, txnId = NULL WHERE id = :id")
    suspend fun setResult(id: Long, state: ItemState, code: String?, message: String?)

    @Query("UPDATE scan_item SET state = :state, errorMessage = :message WHERE txnId = :txnId")
    suspend fun setStateByTxn(txnId: String, state: ItemState, message: String?)

    @Query("DELETE FROM scan_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scan_item WHERE sessionId = :sessionId AND state IN ('LOCAL', 'FAILED')")
    suspend fun clearUnsent(sessionId: Long)

    @Query("UPDATE scan_item SET state = 'LOCAL', txnId = NULL WHERE state = 'SENDING'")
    suspend fun resetStuckSending()

    /** PENDING rows whose batch is no longer in the retry queue can never be sent: make them resendable. */
    @Query("UPDATE scan_item SET state = 'FAILED', errorMessage = 'Not sent - please resend' WHERE state = 'PENDING' AND (txnId IS NULL OR txnId NOT IN (SELECT txnId FROM pending_submit))")
    suspend fun releaseOrphanPending()
}

package com.rfid.rolltrack.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDao {
    @Upsert
    suspend fun upsert(p: PendingSubmitEntity)

    @Query("SELECT * FROM pending_submit ORDER BY createdAt")
    fun observeAll(): Flow<List<PendingSubmitEntity>>

    @Query("SELECT COUNT(*) FROM pending_submit")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_submit WHERE status = :status ORDER BY createdAt")
    suspend fun byStatus(status: String): List<PendingSubmitEntity>

    @Query("SELECT * FROM pending_submit WHERE txnId = :txnId")
    suspend fun get(txnId: String): PendingSubmitEntity?

    @Query("SELECT COUNT(*) FROM pending_submit")
    suspend fun count(): Int

    @Query("DELETE FROM pending_submit WHERE txnId = :txnId")
    suspend fun delete(txnId: String)
}

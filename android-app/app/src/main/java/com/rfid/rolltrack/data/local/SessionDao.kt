package com.rfid.rolltrack.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: ScanSessionEntity): Long

    @Query("SELECT * FROM scan_session WHERE type = :type AND userId = :userId AND open = 1 ORDER BY id DESC LIMIT 1")
    suspend fun findOpen(type: SessionType, userId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_session WHERE id = :id")
    fun observe(id: Long): Flow<ScanSessionEntity?>

    @Query("SELECT * FROM scan_session WHERE id = :id")
    suspend fun get(id: Long): ScanSessionEntity?

    @Query("UPDATE scan_session SET headerJson = :headerJson WHERE id = :id")
    suspend fun setHeader(id: Long, headerJson: String?)

    @Query("UPDATE scan_session SET lastResultJson = :json WHERE id = :id")
    suspend fun setLastResult(id: Long, json: String?)

    @Query("UPDATE scan_session SET open = 0 WHERE id = :id")
    suspend fun close(id: Long)
}

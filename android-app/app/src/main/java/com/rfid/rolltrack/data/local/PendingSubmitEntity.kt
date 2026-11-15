package com.rfid.rolltrack.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_submit")
data class PendingSubmitEntity(
    @PrimaryKey
    val txnId: String,
    val sessionId: Long,
    val type: SessionType,
    val opJson: String,
    val itemCount: Int,
    val status: String = "QUEUED",
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

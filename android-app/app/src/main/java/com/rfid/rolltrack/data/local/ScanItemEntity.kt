package com.rfid.rolltrack.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_item",
    indices = [
        Index(value = ["sessionId", "tagId"], unique = true),
        Index(value = ["state"])
    ]
)
data class ScanItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val tagId: String,
    val source: String,
    val scannedAt: Long = System.currentTimeMillis(),
    val weightKg: Double? = null,
    val state: ItemState = ItemState.LOCAL,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val txnId: String? = null
)

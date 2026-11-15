package com.rfid.rolltrack.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_session",
    indices = [
        Index(value = ["type", "userId", "open"])
    ]
)
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: SessionType,
    val userId: String,
    val headerJson: String? = null,
    val lastResultJson: String? = null,
    val open: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

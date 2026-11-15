package com.rfid.rolltrack.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanSessionEntity::class,
        ScanItemEntity::class,
        PendingSubmitEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun itemDao(): ItemDao
    abstract fun pendingDao(): PendingDao
}

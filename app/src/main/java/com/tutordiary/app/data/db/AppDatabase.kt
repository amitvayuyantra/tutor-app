package com.tutordiary.app.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TutorProfileEntity::class,
        BatchEntity::class,
        StudentEntity::class,
        ChargeEntity::class,
        WaiverEntity::class,
        PaymentEntity::class,
        AttendanceEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun batchDao(): BatchDao
    abstract fun studentDao(): StudentDao
    abstract fun chargeDao(): ChargeDao
    abstract fun waiverDao(): WaiverDao
    abstract fun paymentDao(): PaymentDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        const val NAME = "tutor_diary.db"
    }
}

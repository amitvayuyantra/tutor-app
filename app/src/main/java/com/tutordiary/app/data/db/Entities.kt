package com.tutordiary.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/* Dates are stored as epoch days (LocalDate.toEpochDay). Months as yyyymm Ints. Money as whole rupees (Long). */

@Entity(tableName = "tutor_profile")
data class TutorProfileEntity(
    @PrimaryKey val id: Int = 1,
    val tutorName: String,
    val businessName: String?,
    val upiId: String?,
    val templateEn: String,
    val templateHi: String,
)

@Entity(tableName = "batches")
data class BatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subject: String?,
    val classDays: String?,
    val classTime: String?,
    val archived: Boolean = false,
)

@Entity(
    tableName = "students",
    indices = [Index("batchId")],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentName: String,
    /** Always "+91XXXXXXXXXX". */
    val phone: String,
    val batchId: Long?,
    val monthlyFee: Long,
    val dueDay: Int,
    val joinDate: Long,
    /** Null = active. */
    val archivedOn: Long?,
    /** First month that may be charged (yyyymm). */
    val chargesFromMonth: Int,
    val notes: String?,
    val lastReminderOn: Long?,
    /** Code the tutor shared so this student/parent can see their record online. Null = not shared. */
    @ColumnInfo(defaultValue = "NULL") val inviteCode: String? = null,
)

@Entity(
    tableName = "charges",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["studentId", "month"], unique = true)],
)
data class ChargeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val month: Int,
    val amount: Long,
    val dueDate: Long,
)

@Entity(
    tableName = "waivers",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["studentId", "month"], unique = true)],
)
data class WaiverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val month: Int,
    val amount: Long,
    val note: String,
    val createdOn: Long,
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("studentId")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val amount: Long,
    val date: Long,
    /** CASH, UPI or OTHER. */
    val method: String,
    val note: String?,
)

@Entity(
    tableName = "attendance",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class, parentColumns = ["id"], childColumns = ["studentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["studentId", "date"], unique = true),
        Index(value = ["batchId", "date"]),
    ],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val batchId: Long,
    val date: Long,
    @ColumnInfo(name = "present") val present: Boolean,
)

enum class PaymentMethod { CASH, UPI, OTHER;
    companion object {
        fun parse(value: String): PaymentMethod? = entries.firstOrNull { it.name == value }
    }
}

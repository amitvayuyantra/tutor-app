package com.tutordiary.app.data.backup

import com.tutordiary.app.data.db.AttendanceEntity
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.PaymentMethod
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.db.WaiverEntity
import com.tutordiary.app.domain.PhoneNumbers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/* The backup file. Bump FORMAT_VERSION whenever the shape changes, and keep reading old versions. */

const val BACKUP_APP_ID = "com.tutordiary.app"
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupFile(
    val app: String = BACKUP_APP_ID,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val createdAtEpochDay: Long,
    val profile: ProfileB? = null,
    val batches: List<BatchB> = emptyList(),
    val students: List<StudentB> = emptyList(),
    val charges: List<ChargeB> = emptyList(),
    val waivers: List<WaiverB> = emptyList(),
    val payments: List<PaymentB> = emptyList(),
    val attendance: List<AttendanceB> = emptyList(),
)

@Serializable data class ProfileB(val tutorName: String, val businessName: String? = null, val upiId: String? = null, val templateEn: String, val templateHi: String)
@Serializable data class BatchB(val id: Long, val name: String, val subject: String? = null, val classDays: String? = null, val classTime: String? = null, val archived: Boolean = false)
@Serializable data class StudentB(val id: Long, val name: String, val parentName: String, val phone: String, val batchId: Long? = null, val monthlyFee: Long, val dueDay: Int, val joinDate: Long, val archivedOn: Long? = null, val chargesFromMonth: Int, val notes: String? = null, val lastReminderOn: Long? = null, val inviteCode: String? = null)
@Serializable data class ChargeB(val id: Long, val studentId: Long, val month: Int, val amount: Long, val dueDate: Long)
@Serializable data class WaiverB(val id: Long, val studentId: Long, val month: Int, val amount: Long, val note: String, val createdOn: Long)
@Serializable data class PaymentB(val id: Long, val studentId: Long, val amount: Long, val date: Long, val method: String, val note: String? = null)
@Serializable data class AttendanceB(val id: Long, val studentId: Long, val batchId: Long, val date: Long, val present: Boolean)

/** Everything in the database, as plain rows. */
data class AllData(
    val profile: TutorProfileEntity?,
    val batches: List<BatchEntity>,
    val students: List<StudentEntity>,
    val charges: List<ChargeEntity>,
    val waivers: List<WaiverEntity>,
    val payments: List<PaymentEntity>,
    val attendance: List<AttendanceEntity>,
)

sealed interface BackupProblem {
    data object NotJson : BackupProblem
    data object WrongApp : BackupProblem
    data class NewerVersion(val version: Int) : BackupProblem
    data class Invalid(val detail: String) : BackupProblem
}

sealed interface DecodeResult {
    data class Ok(val data: AllData) : DecodeResult
    data class Bad(val problem: BackupProblem) : DecodeResult
}

object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: AllData, todayEpochDay: Long): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            createdAtEpochDay = todayEpochDay,
            profile = data.profile?.let { ProfileB(it.tutorName, it.businessName, it.upiId, it.templateEn, it.templateHi) },
            batches = data.batches.map { BatchB(it.id, it.name, it.subject, it.classDays, it.classTime, it.archived) },
            students = data.students.map {
                StudentB(it.id, it.name, it.parentName, it.phone, it.batchId, it.monthlyFee, it.dueDay, it.joinDate,
                    it.archivedOn, it.chargesFromMonth, it.notes, it.lastReminderOn, it.inviteCode)
            },
            charges = data.charges.map { ChargeB(it.id, it.studentId, it.month, it.amount, it.dueDate) },
            waivers = data.waivers.map { WaiverB(it.id, it.studentId, it.month, it.amount, it.note, it.createdOn) },
            payments = data.payments.map { PaymentB(it.id, it.studentId, it.amount, it.date, it.method, it.note) },
            attendance = data.attendance.map { AttendanceB(it.id, it.studentId, it.batchId, it.date, it.present) },
        )
    )

    /** Reads and fully checks a backup. Nothing is written to the database here. */
    fun decode(text: String): DecodeResult {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            return DecodeResult.Bad(BackupProblem.NotJson)
        }
        if (file.app != BACKUP_APP_ID) return DecodeResult.Bad(BackupProblem.WrongApp)
        if (file.formatVersion > BACKUP_FORMAT_VERSION) return DecodeResult.Bad(BackupProblem.NewerVersion(file.formatVersion))
        if (file.formatVersion < 1) return DecodeResult.Bad(BackupProblem.Invalid("formatVersion"))
        validate(file)?.let { return DecodeResult.Bad(BackupProblem.Invalid(it)) }
        return DecodeResult.Ok(
            AllData(
                profile = file.profile?.let { TutorProfileEntity(1, it.tutorName, it.businessName, it.upiId, it.templateEn, it.templateHi) },
                batches = file.batches.map { BatchEntity(it.id, it.name, it.subject, it.classDays, it.classTime, it.archived) },
                students = file.students.map {
                    StudentEntity(it.id, it.name, it.parentName, it.phone, it.batchId, it.monthlyFee, it.dueDay, it.joinDate,
                        it.archivedOn, it.chargesFromMonth, it.notes, it.lastReminderOn, it.inviteCode)
                },
                charges = file.charges.map { ChargeEntity(it.id, it.studentId, it.month, it.amount, it.dueDate) },
                waivers = file.waivers.map { WaiverEntity(it.id, it.studentId, it.month, it.amount, it.note, it.createdOn) },
                payments = file.payments.map { PaymentEntity(it.id, it.studentId, it.amount, it.date, it.method, it.note) },
                attendance = file.attendance.map { AttendanceEntity(it.id, it.studentId, it.batchId, it.date, it.present) },
            )
        )
    }

    /** Returns a short reason when something is wrong, or null when the file is fine. */
    private fun validate(f: BackupFile): String? {
        fun <T> dupIds(items: List<T>, id: (T) -> Long) = items.groupBy(id).any { it.value.size > 1 }
        fun validMonth(m: Int) = m / 100 in 2000..2200 && m % 100 in 1..12

        f.profile?.let { if (it.tutorName.isBlank()) return "profile.tutorName" }
        if (dupIds(f.batches) { it.id }) return "batches.id"
        if (f.batches.any { it.id <= 0 || it.name.isBlank() }) return "batches"
        val batchIds = f.batches.map { it.id }.toSet()

        if (dupIds(f.students) { it.id }) return "students.id"
        for (s in f.students) {
            if (s.id <= 0 || s.name.isBlank() || s.parentName.isBlank()) return "students.name"
            if (PhoneNumbers.normalize(s.phone) != s.phone) return "students.phone"
            if (s.monthlyFee < 0) return "students.monthlyFee"
            if (s.dueDay !in 1..31) return "students.dueDay"
            if (!validMonth(s.chargesFromMonth)) return "students.chargesFromMonth"
            if (s.batchId != null && s.batchId !in batchIds) return "students.batchId"
        }
        val studentIds = f.students.map { it.id }.toSet()

        if (dupIds(f.charges) { it.id }) return "charges.id"
        if (f.charges.groupBy { it.studentId to it.month }.any { it.value.size > 1 }) return "charges.duplicateMonth"
        if (f.charges.any { it.studentId !in studentIds || it.amount < 0 || !validMonth(it.month) }) return "charges"
        val chargeByKey = f.charges.associateBy { it.studentId to it.month }

        if (dupIds(f.waivers) { it.id }) return "waivers.id"
        if (f.waivers.groupBy { it.studentId to it.month }.any { it.value.size > 1 }) return "waivers.duplicateMonth"
        for (w in f.waivers) {
            val c = chargeByKey[w.studentId to w.month] ?: return "waivers.noCharge"
            if (w.amount <= 0 || w.amount > c.amount) return "waivers.amount"
        }

        if (dupIds(f.payments) { it.id }) return "payments.id"
        if (f.payments.any { it.studentId !in studentIds || it.amount <= 0 || PaymentMethod.parse(it.method) == null }) return "payments"

        if (dupIds(f.attendance) { it.id }) return "attendance.id"
        if (f.attendance.groupBy { it.studentId to it.date }.any { it.value.size > 1 }) return "attendance.duplicate"
        if (f.attendance.any { it.studentId !in studentIds || it.batchId !in batchIds }) return "attendance"
        return null
    }
}

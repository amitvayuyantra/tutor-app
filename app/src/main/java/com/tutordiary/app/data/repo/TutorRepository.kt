package com.tutordiary.app.data.repo

import androidx.room.withTransaction
import com.tutordiary.app.data.backup.AllData
import com.tutordiary.app.data.db.AppDatabase
import com.tutordiary.app.data.db.AttendanceEntity
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.PaymentMethod
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.db.WaiverEntity
import com.tutordiary.app.domain.AppClock
import com.tutordiary.app.domain.Billing
import com.tutordiary.app.domain.BillingStudent
import com.tutordiary.app.domain.MonthKey
import com.tutordiary.app.domain.PhoneNumbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the student form sends. Phone may be in any common format; it is normalized here. */
data class StudentInput(
    val id: Long = 0,
    val name: String,
    val parentName: String,
    val phone: String,
    val batchId: Long?,
    val monthlyFee: Long,
    val dueDay: Int,
    val joinDate: LocalDate,
    val notes: String?,
)

/** Rule broken by a request. The UI turns each into a friendly message. */
class RuleException(val rule: Rule) : Exception(rule.name)

enum class Rule {
    NAME_REQUIRED, PARENT_REQUIRED, PHONE_INVALID, FEE_INVALID, DUE_DAY_INVALID,
    AMOUNT_NOT_POSITIVE, METHOD_INVALID, NO_CHARGE_FOR_MONTH, WAIVER_TOO_BIG, WAIVER_EXISTS,
    NOTE_REQUIRED, NOT_FOUND,
}

data class FeeData(
    val students: List<StudentEntity>,
    val charges: List<ChargeEntity>,
    val waivers: List<WaiverEntity>,
    val payments: List<PaymentEntity>,
)

data class AttendanceRow(val student: StudentEntity, val present: Boolean, val saved: Boolean)

@Singleton
class TutorRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: AppClock,
) {
    private val profileDao = db.profileDao()
    private val batchDao = db.batchDao()
    private val studentDao = db.studentDao()
    private val chargeDao = db.chargeDao()
    private val waiverDao = db.waiverDao()
    private val paymentDao = db.paymentDao()
    private val attendanceDao = db.attendanceDao()

    fun today(): LocalDate = clock.today()

    // ---------- Profile ----------

    fun observeProfile(): Flow<TutorProfileEntity?> = profileDao.observe()

    suspend fun saveProfile(tutorName: String, businessName: String?, upiId: String?, defaultEn: String, defaultHi: String) {
        if (tutorName.isBlank()) throw RuleException(Rule.NAME_REQUIRED)
        val current = profileDao.get()
        profileDao.upsert(
            TutorProfileEntity(
                id = 1,
                tutorName = tutorName.trim(),
                businessName = businessName?.trim()?.ifEmpty { null },
                upiId = upiId?.trim()?.ifEmpty { null },
                templateEn = current?.templateEn ?: defaultEn,
                templateHi = current?.templateHi ?: defaultHi,
            )
        )
    }

    suspend fun saveTemplates(templateEn: String, templateHi: String) {
        val current = profileDao.get() ?: throw RuleException(Rule.NOT_FOUND)
        profileDao.upsert(current.copy(templateEn = templateEn, templateHi = templateHi))
    }

    // ---------- Batches ----------

    fun observeBatches(): Flow<List<BatchEntity>> = batchDao.observeAll()

    suspend fun saveBatch(batch: BatchEntity): Long {
        if (batch.name.isBlank()) throw RuleException(Rule.NAME_REQUIRED)
        val clean = batch.copy(
            name = batch.name.trim(),
            subject = batch.subject?.trim()?.ifEmpty { null },
            classDays = batch.classDays?.trim()?.ifEmpty { null },
            classTime = batch.classTime?.trim()?.ifEmpty { null },
        )
        return if (clean.id == 0L) batchDao.insert(clean) else { batchDao.update(clean); clean.id }
    }

    suspend fun getBatch(id: Long): BatchEntity? = batchDao.get(id)

    suspend fun setBatchArchived(id: Long, archived: Boolean) {
        val b = batchDao.get(id) ?: throw RuleException(Rule.NOT_FOUND)
        batchDao.update(b.copy(archived = archived))
    }

    suspend fun activeStudentCount(batchId: Long): Int = studentDao.countActiveInBatch(batchId)

    // ---------- Students ----------

    fun observeStudents(): Flow<List<StudentEntity>> = studentDao.observeAll()
    fun observeStudent(id: Long): Flow<StudentEntity?> = studentDao.observe(id)
    suspend fun getStudent(id: Long): StudentEntity? = studentDao.get(id)

    private fun check(input: StudentInput): String {
        if (input.name.isBlank()) throw RuleException(Rule.NAME_REQUIRED)
        if (input.parentName.isBlank()) throw RuleException(Rule.PARENT_REQUIRED)
        val phone = PhoneNumbers.normalize(input.phone) ?: throw RuleException(Rule.PHONE_INVALID)
        if (input.monthlyFee < 0) throw RuleException(Rule.FEE_INVALID)
        if (input.dueDay !in 1..31) throw RuleException(Rule.DUE_DAY_INVALID)
        return phone
    }

    suspend fun addStudent(input: StudentInput): Long {
        val phone = check(input)
        return db.withTransaction {
            val id = studentDao.insert(
                StudentEntity(
                    name = input.name.trim(),
                    parentName = input.parentName.trim(),
                    phone = phone,
                    batchId = input.batchId,
                    monthlyFee = input.monthlyFee,
                    dueDay = input.dueDay,
                    joinDate = input.joinDate.toEpochDay(),
                    archivedOn = null,
                    chargesFromMonth = MonthKey.of(input.joinDate),
                    notes = input.notes?.trim()?.ifEmpty { null },
                    lastReminderOn = null,
                )
            )
            generateFor(studentDao.get(id)!!)
            id
        }
    }

    /**
     * Saves edits. Old months are charged with the OLD fee first, so a fee change only
     * affects months that start after today.
     */
    suspend fun updateStudent(input: StudentInput) {
        val phone = check(input)
        db.withTransaction {
            val old = studentDao.get(input.id) ?: throw RuleException(Rule.NOT_FOUND)
            generateFor(old)
            val oldJoinMonth = MonthKey.of(LocalDate.ofEpochDay(old.joinDate))
            val newJoinMonth = MonthKey.of(input.joinDate)
            if (newJoinMonth > oldJoinMonth) {
                // Join date was a mistake: remove charges for months before the corrected join month.
                chargeDao.deleteBefore(old.id, newJoinMonth)
                waiverDao.deleteBefore(old.id, newJoinMonth)
            }
            val chargesFrom = if (old.chargesFromMonth == oldJoinMonth) newJoinMonth else maxOf(old.chargesFromMonth, newJoinMonth)
            val updated = old.copy(
                name = input.name.trim(),
                parentName = input.parentName.trim(),
                phone = phone,
                batchId = input.batchId,
                monthlyFee = input.monthlyFee,
                dueDay = input.dueDay,
                joinDate = input.joinDate.toEpochDay(),
                chargesFromMonth = chargesFrom,
                notes = input.notes?.trim()?.ifEmpty { null },
            )
            studentDao.update(updated)
            generateFor(updated)
        }
    }

    suspend fun archiveStudent(id: Long) = db.withTransaction {
        val s = studentDao.get(id) ?: throw RuleException(Rule.NOT_FOUND)
        if (s.archivedOn != null) return@withTransaction
        generateFor(s) // charge everything up to and including this month first
        studentDao.update(s.copy(archivedOn = today().toEpochDay()))
    }

    /** Charging restarts from the current month. Months while archived are never charged. */
    suspend fun unarchiveStudent(id: Long) = db.withTransaction {
        val s = studentDao.get(id) ?: throw RuleException(Rule.NOT_FOUND)
        if (s.archivedOn == null) return@withTransaction
        val restored = s.copy(archivedOn = null, chargesFromMonth = maxOf(s.chargesFromMonth, MonthKey.of(today())))
        studentDao.update(restored)
        generateFor(restored)
    }

    /** Removes the student and all their charges, payments, waivers and attendance. */
    suspend fun deleteStudentPermanently(id: Long) = studentDao.delete(id)

    suspend fun markReminderOpened(id: Long) {
        val s = studentDao.get(id) ?: return
        studentDao.update(s.copy(lastReminderOn = today().toEpochDay()))
    }

    // ---------- Fees ----------

    fun observeFeeData(): Flow<FeeData> = combine(
        studentDao.observeAll(), chargeDao.observeAll(), waiverDao.observeAll(), paymentDao.observeAll(),
    ) { s, c, w, p -> FeeData(s, c, w, p) }

    private fun StudentEntity.toBilling() = BillingStudent(
        id = id,
        monthlyFee = monthlyFee,
        dueDay = dueDay,
        joinDate = LocalDate.ofEpochDay(joinDate),
        archivedOn = archivedOn?.let { LocalDate.ofEpochDay(it) },
        chargesFromMonth = chargesFromMonth,
    )

    private suspend fun generateFor(s: StudentEntity): Int {
        val existing = chargeDao.monthsForStudent(s.id).toSet()
        val planned = Billing.missingCharges(s.toBilling(), existing, today())
        if (planned.isEmpty()) return 0
        val ids = chargeDao.insertIgnore(planned.map {
            ChargeEntity(studentId = it.studentId, month = it.month, amount = it.amount, dueDate = it.dueDate.toEpochDay())
        })
        return ids.count { it != -1L }
    }

    /** Creates every missing monthly charge for every student. Safe to call any number of times. */
    suspend fun generateDueCharges(): Int = db.withTransaction {
        studentDao.all().sumOf { generateFor(it) }
    }

    private fun checkPayment(p: PaymentEntity) {
        if (p.amount <= 0) throw RuleException(Rule.AMOUNT_NOT_POSITIVE)
        if (PaymentMethod.parse(p.method) == null) throw RuleException(Rule.METHOD_INVALID)
    }

    suspend fun addPayment(p: PaymentEntity): Long {
        checkPayment(p)
        return paymentDao.insert(p.copy(id = 0, note = p.note?.trim()?.ifEmpty { null }))
    }

    suspend fun updatePayment(p: PaymentEntity) {
        checkPayment(p)
        paymentDao.get(p.id) ?: throw RuleException(Rule.NOT_FOUND)
        paymentDao.update(p.copy(note = p.note?.trim()?.ifEmpty { null }))
    }

    suspend fun deletePayment(id: Long) = paymentDao.delete(id)

    suspend fun getPayment(id: Long): PaymentEntity? = paymentDao.get(id)

    suspend fun addWaiver(studentId: Long, month: Int, amount: Long, note: String) = db.withTransaction {
        if (note.isBlank()) throw RuleException(Rule.NOTE_REQUIRED)
        if (amount <= 0) throw RuleException(Rule.AMOUNT_NOT_POSITIVE)
        val charge = chargeDao.forStudent(studentId).firstOrNull { it.month == month }
            ?: throw RuleException(Rule.NO_CHARGE_FOR_MONTH)
        if (amount > charge.amount) throw RuleException(Rule.WAIVER_TOO_BIG)
        if (waiverDao.countFor(studentId, month) > 0) throw RuleException(Rule.WAIVER_EXISTS)
        waiverDao.insert(WaiverEntity(studentId = studentId, month = month, amount = amount, note = note.trim(), createdOn = today().toEpochDay()))
    }

    suspend fun deleteWaiver(id: Long) = waiverDao.delete(id)

    // ---------- Attendance ----------

    /** Active students of the batch. Everyone is present unless a saved record says otherwise. */
    suspend fun attendanceSheet(batchId: Long, date: LocalDate): List<AttendanceRow> {
        val day = date.toEpochDay()
        return studentDao.activeInBatch(batchId).map { s ->
            val rec = attendanceDao.find(s.id, day)
            AttendanceRow(s, present = rec?.present ?: true, saved = rec != null)
        }
    }

    suspend fun saveAttendance(batchId: Long, date: LocalDate, presentByStudent: Map<Long, Boolean>) = db.withTransaction {
        val day = date.toEpochDay()
        val rows = presentByStudent.map { (studentId, present) ->
            val existing = attendanceDao.find(studentId, day)
            AttendanceEntity(id = existing?.id ?: 0, studentId = studentId, batchId = batchId, date = day, present = present)
        }
        attendanceDao.upsertAll(rows)
    }

    fun observeAttendance(studentId: Long, from: LocalDate, to: LocalDate): Flow<List<AttendanceEntity>> =
        attendanceDao.observeForStudent(studentId, from.toEpochDay(), to.toEpochDay())

    // ---------- Backup ----------

    suspend fun readAll(): AllData = db.withTransaction {
        AllData(
            profile = profileDao.get(),
            batches = batchDao.all(),
            students = studentDao.all(),
            charges = chargeDao.all(),
            waivers = waiverDao.all(),
            payments = paymentDao.all(),
            attendance = attendanceDao.all(),
        )
    }

    /** Replaces everything. All-or-nothing: if any insert fails, the old data stays untouched. */
    suspend fun replaceAll(data: AllData) = db.withTransaction {
        attendanceDao.clear()
        paymentDao.clear()
        waiverDao.clear()
        chargeDao.clear()
        studentDao.clear()
        batchDao.clear()
        profileDao.clear()
        data.profile?.let { profileDao.insertAll(listOf(it)) }
        batchDao.insertAll(data.batches)
        studentDao.insertAll(data.students)
        chargeDao.insertAll(data.charges)
        waiverDao.insertAll(data.waivers)
        paymentDao.insertAll(data.payments)
        attendanceDao.insertAll(data.attendance)
    }
}

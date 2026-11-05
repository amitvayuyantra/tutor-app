package com.tutordiary.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tutordiary.app.data.backup.BackupCodec
import com.tutordiary.app.data.backup.DecodeResult
import com.tutordiary.app.data.db.AppDatabase
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.repo.Rule
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.StudentInput
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.domain.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TutorRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TutorRepository
    private var today = LocalDate.parse("2026-03-10")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TutorRepository(db, AppClock { today })
    }

    @After fun tearDown() = db.close()

    private fun input(fee: Long = 1500, join: String = "2026-01-15", dueDay: Int = 5, id: Long = 0) =
        StudentInput(id, "Aarav", "Mr Sharma", "98765 43210", null, fee, dueDay, LocalDate.parse(join), null)

    private suspend fun charges(id: Long): List<ChargeEntity> = db.chargeDao().forStudent(id)

    @Test fun addingStudentCreatesChargesUpToToday() = runBlocking {
        val id = repo.addStudent(input())
        assertEquals(listOf(202601, 202602, 202603), charges(id).map { it.month })
        assertEquals("+919876543210", repo.getStudent(id)!!.phone)
    }

    @Test fun generatingManyTimesNeverDuplicates() = runBlocking {
        val id = repo.addStudent(input())
        repeat(5) { repo.generateDueCharges() }
        assertEquals(3, charges(id).size)
        // Inserting a duplicate month directly is ignored by the unique index too.
        db.chargeDao().insertIgnore(listOf(ChargeEntity(studentId = id, month = 202601, amount = 1, dueDate = 0)))
        assertEquals(3, charges(id).size)
    }

    @Test fun catchUpAfterMonthsAway() = runBlocking {
        val id = repo.addStudent(input())
        today = LocalDate.parse("2026-07-01")
        assertEquals(4, repo.generateDueCharges())
        assertEquals(listOf(202601, 202602, 202603, 202604, 202605, 202606, 202607), charges(id).map { it.month })
    }

    @Test fun feeChangeOnlyAffectsFutureMonths() = runBlocking {
        val id = repo.addStudent(input(fee = 1500))
        today = LocalDate.parse("2026-05-10") // April and May not yet created
        repo.updateStudent(input(fee = 2000, id = id))
        val byMonth = charges(id).associate { it.month to it.amount }
        assertEquals(1500L, byMonth[202604]) // caught up at the old fee before the change
        assertEquals(1500L, byMonth[202605])
        today = LocalDate.parse("2026-06-01")
        repo.generateDueCharges()
        assertEquals(2000L, charges(id).first { it.month == 202606 }.amount)
        assertEquals(1500L, charges(id).first { it.month == 202601 }.amount)
    }

    @Test fun dueDay31InFebruary() = runBlocking {
        val id = repo.addStudent(input(dueDay = 31))
        val feb = charges(id).first { it.month == 202602 }
        assertEquals(LocalDate.parse("2026-02-28").toEpochDay(), feb.dueDate)
    }

    @Test fun archiveStopsChargesAndUnarchiveSkipsTheGap() = runBlocking {
        val id = repo.addStudent(input())
        repo.archiveStudent(id)
        today = LocalDate.parse("2026-06-10")
        repo.generateDueCharges()
        assertEquals(listOf(202601, 202602, 202603), charges(id).map { it.month })
        repo.unarchiveStudent(id)
        assertEquals(listOf(202601, 202602, 202603, 202606), charges(id).map { it.month })
    }

    @Test fun movingJoinDateLaterRemovesWrongCharges() = runBlocking {
        val id = repo.addStudent(input(join = "2026-01-15"))
        repo.updateStudent(input(join = "2026-03-01", id = id))
        assertEquals(listOf(202603), charges(id).map { it.month })
    }

    @Test fun paymentsMustBePositive() = runBlocking {
        val id = repo.addStudent(input())
        for (amount in listOf(0L, -100L)) {
            try {
                repo.addPayment(PaymentEntity(studentId = id, amount = amount, date = 0, method = "CASH", note = null))
                fail("should reject $amount")
            } catch (e: RuleException) {
                assertEquals(Rule.AMOUNT_NOT_POSITIVE, e.rule)
            }
        }
    }

    @Test fun waiverRules() = runBlocking {
        val id = repo.addStudent(input())
        repo.addWaiver(id, 202602, 500, "Sick leave")
        try { repo.addWaiver(id, 202602, 100, "again"); fail() } catch (e: RuleException) { assertEquals(Rule.WAIVER_EXISTS, e.rule) }
        try { repo.addWaiver(id, 202603, 5000, "too big"); fail() } catch (e: RuleException) { assertEquals(Rule.WAIVER_TOO_BIG, e.rule) }
        try { repo.addWaiver(id, 202512, 100, "before join"); fail() } catch (e: RuleException) { assertEquals(Rule.NO_CHARGE_FOR_MONTH, e.rule) }
    }

    @Test fun invalidPhoneIsRejected() = runBlocking {
        try { repo.addStudent(input().copy(phone = "12345")); fail() } catch (e: RuleException) { assertEquals(Rule.PHONE_INVALID, e.rule) }
    }

    @Test fun permanentDeleteRemovesEverything() = runBlocking {
        val id = repo.addStudent(input())
        repo.addPayment(PaymentEntity(studentId = id, amount = 100, date = 0, method = "UPI", note = null))
        repo.deleteStudentPermanently(id)
        assertNull(repo.getStudent(id))
        assertTrue(db.chargeDao().all().isEmpty())
        assertTrue(db.paymentDao().all().isEmpty())
    }

    @Test fun attendanceSavesAndEditsWithoutDuplicates() = runBlocking {
        val batchId = repo.saveBatch(com.tutordiary.app.data.db.BatchEntity(name = "Class 8", subject = null, classDays = null, classTime = null))
        val id = repo.addStudent(input().copy(batchId = batchId))
        val day = LocalDate.parse("2026-03-09")
        assertTrue(repo.attendanceSheet(batchId, day).single().present) // present by default
        repo.saveAttendance(batchId, day, mapOf(id to false))
        repo.saveAttendance(batchId, day, mapOf(id to true))
        assertEquals(1, db.attendanceDao().all().size)
        assertTrue(repo.attendanceSheet(batchId, day).single().present)
        // Attendance never changes fees.
        assertEquals(3, charges(id).size)
    }

    @Test fun backupAndRestoreRoundTrip() = runBlocking {
        repo.saveProfile("Priya", null, "priya@okaxis", "EN", "HI")
        val id = repo.addStudent(input())
        repo.addPayment(PaymentEntity(studentId = id, amount = 700, date = 1, method = "UPI", note = "GPay"))
        val text = BackupCodec.encode(repo.readAll(), 1)

        repo.deleteStudentPermanently(id)
        val decoded = BackupCodec.decode(text) as DecodeResult.Ok
        repo.replaceAll(decoded.data)

        val restored = repo.readAll()
        assertEquals(decoded.data, restored)
        assertEquals("Priya", repo.observeProfile().first()!!.tutorName)
    }

    @Test fun failedRestoreLeavesOldDataUntouched() = runBlocking {
        val id = repo.addStudent(input())
        val before = repo.readAll()
        // A payment pointing at a student that does not exist breaks the foreign key half way through.
        val broken = before.copy(payments = listOf(PaymentEntity(1, 999, 100, 1, "CASH", null)))
        try {
            repo.replaceAll(broken)
            fail("restore should fail")
        } catch (e: Exception) {
            // expected
        }
        assertEquals(before, repo.readAll())
        assertEquals("Aarav", repo.getStudent(id)!!.name)
    }
}

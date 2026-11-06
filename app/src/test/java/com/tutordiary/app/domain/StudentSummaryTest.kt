package com.tutordiary.app.domain

import com.tutordiary.app.data.db.AttendanceEntity
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.repo.FeeData
import com.tutordiary.app.data.sync.SummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StudentSummaryTest {
    private val today = LocalDate.parse("2026-11-10")
    private fun d(s: String) = LocalDate.parse(s).toEpochDay()
    private val shared = StudentEntity(1, "Aarav", "Mr Mehta", "+919876543210", null, 1500, 5, d("2026-10-01"), null, 202610, "secret note", null, "ABC234")
    private val notShared = StudentEntity(2, "Other", "X", "+919123456789", null, 900, 5, d("2026-10-01"), null, 202610, null, null, null)
    private val data = FeeData(
        students = listOf(shared, notShared),
        charges = listOf(ChargeEntity(1, 1, 202610, 1500, d("2026-10-05")), ChargeEntity(2, 1, 202611, 1500, d("2026-11-05")), ChargeEntity(3, 2, 202611, 900, d("2026-11-05"))),
        waivers = emptyList(),
        payments = listOf(PaymentEntity(1, 1, 1000, d("2026-10-03"), "UPI", "gpay")),
    )
    private val attendance = listOf(
        AttendanceEntity(1, 1, 1, d("2026-11-02"), true), AttendanceEntity(2, 1, 1, d("2026-11-03"), false),
        AttendanceEntity(3, 1, 1, d("2026-10-20"), true),
    )
    private val profile = TutorProfileEntity(1, "Priya", "Priya Classes", "priya@okaxis", "", "")

    @Test fun buildsOnlySharedStudentsWithoutPrivateDetails() {
        val list = SummaryBuilder.build("T1", profile, data, attendance, today)
        val s = list.single()
        assertEquals("T1_1", s.key)
        assertEquals("Priya Classes", s.tutorName)
        assertEquals(2000L, s.balance)
        assertEquals(listOf(202610, 202611), s.unpaid.map { it.month })
        assertEquals(500L, s.unpaid.first().remaining)
        assertEquals(listOf(StudentSummary.PaymentLine(d("2026-10-03"), 1000, "UPI")), s.payments)
        assertEquals(listOf(StudentSummary.AttendanceMonth(202611, 1, 2), StudentSummary.AttendanceMonth(202610, 1, 1)), s.attendance)
        val map = s.toMap()
        assertFalse(map.containsKey("phone"))
        assertFalse(map.values.any { it.toString().contains("secret note") || it.toString().contains("gpay") || it.toString().contains("9876543210") })
        assertEquals(FeeStatus.OVERDUE, s.status(today))
    }

    @Test fun roundTripsThroughTheServerFormat() {
        val s = SummaryBuilder.build("T1", profile, data, attendance, today).single()
        assertEquals(s, StudentSummary.fromMap(s.key, s.toMap()))
    }

    @Test fun readsDamagedServerDataSafely() {
        val s = StudentSummary.fromMap("k", mapOf("studentName" to "A", "balance" to "oops", "unpaid" to listOf("bad", mapOf("m" to 202611L))))
        assertEquals(0L, s.balance)
        assertEquals(1, s.unpaid.size)
        assertNull(s.upiId)
    }

    @Test fun inviteCodes() {
        repeat(50) { val c = InviteCodes.generate(); assertEquals(c, InviteCodes.normalize(c)) }
        assertEquals("ABC234", InviteCodes.normalize(" abc-234 "))
        assertNull(InviteCodes.normalize("ABC23"))
        assertNull(InviteCodes.normalize("ABC10O")) // 0, 1 and O are never used
    }
}

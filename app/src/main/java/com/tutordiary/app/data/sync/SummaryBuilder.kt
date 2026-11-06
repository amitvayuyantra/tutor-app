package com.tutordiary.app.data.sync

import com.tutordiary.app.data.db.AttendanceEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.repo.FeeData
import com.tutordiary.app.data.repo.ledgers
import com.tutordiary.app.domain.MonthKey
import com.tutordiary.app.domain.StudentSummary
import java.time.LocalDate

object SummaryBuilder {

    /** Summaries for every student the tutor has shared (students with an invite code). */
    fun build(
        tutorUid: String,
        profile: TutorProfileEntity?,
        data: FeeData,
        attendance: List<AttendanceEntity>,
        today: LocalDate,
    ): List<StudentSummary> {
        val ledgers = data.ledgers(today)
        val paymentsBy = data.payments.groupBy { it.studentId }
        val attendanceBy = attendance.groupBy { it.studentId }
        return data.students.filter { it.inviteCode != null }.map { s ->
            val ledger = ledgers[s.id]
            StudentSummary(
                key = StudentSummary.keyFor(tutorUid, s.id),
                tutorUid = tutorUid,
                studentName = s.name,
                parentName = s.parentName,
                tutorName = profile?.businessName ?: profile?.tutorName.orEmpty(),
                upiId = profile?.upiId,
                monthlyFee = s.monthlyFee,
                dueDay = s.dueDay,
                balance = ledger?.balance ?: 0,
                unpaid = ledger?.unpaidMonths.orEmpty().map {
                    StudentSummary.UnpaidMonth(it.month, it.dueDate.toEpochDay(), it.remaining)
                },
                payments = paymentsBy[s.id].orEmpty()
                    .sortedWith(compareByDescending<com.tutordiary.app.data.db.PaymentEntity> { it.date }.thenByDescending { it.id })
                    .take(StudentSummary.MAX_PAYMENTS)
                    .map { StudentSummary.PaymentLine(it.date, it.amount, it.method) },
                attendance = attendanceBy[s.id].orEmpty()
                    .groupBy { MonthKey.of(LocalDate.ofEpochDay(it.date)) }
                    .map { (month, recs) -> StudentSummary.AttendanceMonth(month, recs.count { it.present }, recs.size) }
                    .sortedByDescending { it.month }
                    .take(StudentSummary.MAX_ATTENDANCE_MONTHS),
                archived = s.archivedOn != null,
                updatedOn = today.toEpochDay(),
            )
        }
    }
}

package com.tutordiary.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Months are stored as an Int like 202611 (November 2026). */
object MonthKey {
    fun of(month: YearMonth): Int = month.year * 100 + month.monthValue
    fun of(date: LocalDate): Int = of(YearMonth.from(date))
    fun toYearMonth(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)
}

/** The facts about a student that billing needs. */
data class BillingStudent(
    val id: Long,
    val monthlyFee: Long,
    val dueDay: Int,
    val joinDate: LocalDate,
    /** Null while the student is active. */
    val archivedOn: LocalDate?,
    /** First month that may be charged (join month, or the month the student was un-archived). */
    val chargesFromMonth: Int,
)

data class PlannedCharge(
    val studentId: Long,
    val month: Int,
    val amount: Long,
    val dueDate: LocalDate,
)

/**
 * Billing rules (plain words):
 * - One charge per student per month, created on the 1st of the month.
 * - The month the student joins is charged in full. The month they are archived is charged in full.
 * - No charges before the join month or after the archive month.
 * - The amount is the student's fee at the moment the charge is created, so later fee
 *   changes never touch old charges.
 * - If the due day doesn't exist in a month (e.g. 31 in February), the last day of that month is used.
 */
object Billing {

    fun dueDate(month: YearMonth, dueDay: Int): LocalDate {
        require(dueDay in 1..31) { "dueDay must be 1..31" }
        return month.atDay(minOf(dueDay, month.lengthOfMonth()))
    }

    /** All months that should have a charge by [today], oldest first. */
    fun billableMonths(student: BillingStudent, today: LocalDate): List<YearMonth> {
        val joinMonth = YearMonth.from(student.joinDate)
        val fromMonth = MonthKey.toYearMonth(maxOf(MonthKey.of(joinMonth), student.chargesFromMonth))
        val currentMonth = YearMonth.from(today)
        val lastMonth = student.archivedOn?.let { minOf(YearMonth.from(it), currentMonth) } ?: currentMonth
        if (student.joinDate.isAfter(today)) return emptyList()
        if (fromMonth.isAfter(lastMonth)) return emptyList()
        val out = ArrayList<YearMonth>()
        var m = fromMonth
        while (!m.isAfter(lastMonth)) {
            out.add(m)
            m = m.plusMonths(1)
        }
        return out
    }

    /** Charges that are missing. Running this again after saving them returns nothing (idempotent). */
    fun missingCharges(
        student: BillingStudent,
        existingMonths: Set<Int>,
        today: LocalDate,
    ): List<PlannedCharge> =
        billableMonths(student, today)
            .filter { MonthKey.of(it) !in existingMonths }
            .map { PlannedCharge(student.id, MonthKey.of(it), student.monthlyFee, dueDate(it, student.dueDay)) }
}

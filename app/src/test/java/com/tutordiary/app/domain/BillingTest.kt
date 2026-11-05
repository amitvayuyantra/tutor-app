package com.tutordiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BillingTest {

    private fun student(
        join: LocalDate,
        fee: Long = 1500,
        dueDay: Int = 5,
        archivedOn: LocalDate? = null,
        chargesFrom: Int = MonthKey.of(join),
    ) = BillingStudent(1, fee, dueDay, join, archivedOn, chargesFrom)

    private fun d(s: String) = LocalDate.parse(s)

    @Test fun createsOneChargePerMonthFromJoinMonthToCurrentMonth() {
        val planned = Billing.missingCharges(student(d("2026-01-10")), emptySet(), d("2026-03-05"))
        assertEquals(listOf(202601, 202602, 202603), planned.map { it.month })
        assertTrue(planned.all { it.amount == 1500L })
        assertEquals(d("2026-01-05"), planned[0].dueDate)
        assertEquals(d("2026-03-05"), planned[2].dueDate)
    }

    @Test fun runningTwiceCreatesNoDuplicates() {
        val s = student(d("2026-01-10"))
        val today = d("2026-03-05")
        val first = Billing.missingCharges(s, emptySet(), today)
        val second = Billing.missingCharges(s, first.map { it.month }.toSet(), today)
        assertTrue(second.isEmpty())
    }

    @Test fun catchesUpAllMissingMonthsAfterLongGap() {
        val planned = Billing.missingCharges(student(d("2026-01-01")), setOf(202601), d("2026-06-20"))
        assertEquals(listOf(202602, 202603, 202604, 202605, 202606), planned.map { it.month })
    }

    @Test fun dueDay31UsesLastDayOfShortMonths() {
        assertEquals(d("2026-01-31"), Billing.dueDate(YearMonth.of(2026, 1), 31))
        assertEquals(d("2026-02-28"), Billing.dueDate(YearMonth.of(2026, 2), 31))
        assertEquals(d("2026-04-30"), Billing.dueDate(YearMonth.of(2026, 4), 31))
        assertEquals(d("2026-02-28"), Billing.dueDate(YearMonth.of(2026, 2), 29))
    }

    @Test fun dueDayInLeapYearFebruary() {
        assertEquals(d("2028-02-29"), Billing.dueDate(YearMonth.of(2028, 2), 31))
        assertEquals(d("2028-02-29"), Billing.dueDate(YearMonth.of(2028, 2), 30))
        assertEquals(d("2028-02-29"), Billing.dueDate(YearMonth.of(2028, 2), 29))
    }

    @Test fun noChargesBeforeJoinDate() {
        // Joins later this month: nothing yet.
        assertTrue(Billing.missingCharges(student(d("2026-03-15")), emptySet(), d("2026-03-10")).isEmpty())
        // Joins on the last day of the month: that month is charged.
        assertEquals(listOf(202603), Billing.missingCharges(student(d("2026-03-31")), emptySet(), d("2026-03-31")).map { it.month })
        // Joins next month: nothing.
        assertTrue(Billing.missingCharges(student(d("2026-04-01")), emptySet(), d("2026-03-31")).isEmpty())
    }

    @Test fun noChargesAfterArchiveMonth() {
        val s = student(d("2026-01-01"), archivedOn = d("2026-04-02"))
        val months = Billing.missingCharges(s, emptySet(), d("2026-07-15")).map { it.month }
        assertEquals(listOf(202601, 202602, 202603, 202604), months)
    }

    @Test fun archivedOnLastDayOfMonthStillStopsAtThatMonth() {
        val s = student(d("2026-01-01"), archivedOn = d("2026-01-31"))
        assertEquals(listOf(202601), Billing.missingCharges(s, emptySet(), d("2026-05-01")).map { it.month })
    }

    @Test fun unarchivedStudentIsNotChargedForGapMonths() {
        val s = student(d("2026-01-01"), chargesFrom = 202606)
        val months = Billing.missingCharges(s, setOf(202601, 202602), d("2026-07-01")).map { it.month }
        assertEquals(listOf(202606, 202607), months)
    }

    @Test fun usesCurrentFeeForNewChargesOnly() {
        // Old months already exist (charged at old fee). Only the new month is planned, at the new fee.
        val s = student(d("2026-01-01"), fee = 2000)
        val planned = Billing.missingCharges(s, setOf(202601, 202602), d("2026-03-01"))
        assertEquals(listOf(202603), planned.map { it.month })
        assertEquals(2000L, planned.single().amount)
    }

    @Test fun yearBoundary() {
        val months = Billing.missingCharges(student(d("2026-11-20")), emptySet(), d("2027-02-01")).map { it.month }
        assertEquals(listOf(202611, 202612, 202701, 202702), months)
    }
}

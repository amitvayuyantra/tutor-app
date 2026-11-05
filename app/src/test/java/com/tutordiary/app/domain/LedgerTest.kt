package com.tutordiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerTest {

    private fun d(s: String) = LocalDate.parse(s)
    private val jan = ChargeLine(202601, 1000, d("2026-01-05"))
    private val feb = ChargeLine(202602, 1000, d("2026-02-05"))
    private val mar = ChargeLine(202603, 1000, d("2026-03-05"))

    @Test fun balanceIsChargesMinusWaiversMinusPayments() {
        val s = Ledger.summarize(listOf(jan, feb, mar), listOf(WaiverLine(202602, 400)), listOf(500, 300), d("2026-03-01"))
        assertEquals(3000, s.totalCharged)
        assertEquals(400, s.totalWaived)
        assertEquals(800, s.totalPaid)
        assertEquals(3000 - 400 - 800L, s.balance)
        assertEquals(1800, s.amountDue)
        assertEquals(0, s.credit)
    }

    @Test fun fullyPaidIsPaid() {
        val s = Ledger.summarize(listOf(jan, feb), emptyList(), listOf(2000), d("2026-03-01"))
        assertEquals(FeeStatus.PAID, s.status)
        assertTrue(s.unpaidMonths.isEmpty())
        assertEquals(0, s.balance)
    }

    @Test fun partialPaymentPaysOldestMonthFirst() {
        val s = Ledger.summarize(listOf(jan, feb), emptyList(), listOf(1300), d("2026-02-01"))
        assertEquals(listOf(202602), s.unpaidMonths.map { it.month })
        assertEquals(700, s.unpaidMonths.single().remaining)
        assertEquals(FeeStatus.PARTIAL, s.status)
    }

    @Test fun dueBeforeDueDateOverdueAfter() {
        val onDueDate = Ledger.summarize(listOf(mar), emptyList(), emptyList(), d("2026-03-05"))
        assertEquals(FeeStatus.DUE, onDueDate.status)
        val dayAfter = Ledger.summarize(listOf(mar), emptyList(), emptyList(), d("2026-03-06"))
        assertEquals(FeeStatus.OVERDUE, dayAfter.status)
    }

    @Test fun partialAfterDueDateIsOverdue() {
        val s = Ledger.summarize(listOf(jan), emptyList(), listOf(200), d("2026-02-01"))
        assertEquals(FeeStatus.OVERDUE, s.status)
        assertEquals(800, s.amountDue)
    }

    @Test fun advancePaymentShowsCredit() {
        val s = Ledger.summarize(listOf(jan), emptyList(), listOf(2500), d("2026-01-10"))
        assertEquals(-1500, s.balance)
        assertEquals(1500, s.credit)
        assertEquals(0, s.amountDue)
        assertEquals(FeeStatus.PAID, s.status)
    }

    @Test fun advanceIsUsedByLaterCharges() {
        val s = Ledger.summarize(listOf(jan, feb, mar), emptyList(), listOf(2500), d("2026-03-10"))
        assertEquals(500, s.amountDue)
        assertEquals(listOf(202603), s.unpaidMonths.map { it.month })
        assertEquals(500, s.unpaidMonths.single().remaining)
    }

    @Test fun fullWaiverClearsTheMonth() {
        val s = Ledger.summarize(listOf(jan, feb), listOf(WaiverLine(202601, 1000)), emptyList(), d("2026-03-01"))
        assertEquals(listOf(202602), s.unpaidMonths.map { it.month })
        assertEquals(1000, s.balance)
    }

    @Test fun waiverCannotMakeAMonthNegative() {
        val s = Ledger.summarize(listOf(jan), listOf(WaiverLine(202601, 5000)), emptyList(), d("2026-03-01"))
        assertEquals(1000, s.totalWaived)
        assertEquals(0, s.balance)
    }

    @Test fun noChargesNoPaymentsIsPaidWithZeroBalance() {
        val s = Ledger.summarize(emptyList(), emptyList(), emptyList(), d("2026-03-01"))
        assertEquals(FeeStatus.PAID, s.status)
        assertEquals(0, s.balance)
    }

    @Test fun oldestDueDateIsFirstUnpaidMonth() {
        val s = Ledger.summarize(listOf(feb, jan), emptyList(), emptyList(), d("2026-03-01"))
        assertEquals(d("2026-01-05"), s.oldestDueDate)
    }
}

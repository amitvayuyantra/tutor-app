package com.tutordiary.app.domain

import java.time.LocalDate

data class ChargeLine(val month: Int, val amount: Long, val dueDate: LocalDate)
data class WaiverLine(val month: Int, val amount: Long)

enum class FeeStatus { PAID, PARTIAL, DUE, OVERDUE }

data class MonthDue(
    val month: Int,
    val dueDate: LocalDate,
    /** Fee after waiver. */
    val netAmount: Long,
    /** What is still unpaid for this month. */
    val remaining: Long,
) {
    val partiallyPaid: Boolean get() = remaining in 1 until netAmount
}

data class LedgerSummary(
    val totalCharged: Long,
    val totalWaived: Long,
    val totalPaid: Long,
    /** charges - waivers - payments. Positive = student owes. Negative = advance (credit). */
    val balance: Long,
    /** Months that still have something unpaid, oldest first. */
    val unpaidMonths: List<MonthDue>,
    val status: FeeStatus,
) {
    val amountDue: Long get() = if (balance > 0) balance else 0
    val credit: Long get() = if (balance < 0) -balance else 0
    val oldestDueDate: LocalDate? get() = unpaidMonths.firstOrNull()?.dueDate
}

/**
 * Works out what a student owes.
 * Payments are not tied to a month. They pay off the oldest unpaid month first.
 */
object Ledger {

    fun summarize(
        charges: List<ChargeLine>,
        waivers: List<WaiverLine>,
        paymentAmounts: List<Long>,
        today: LocalDate,
    ): LedgerSummary {
        val waivedByMonth = waivers.groupBy { it.month }.mapValues { (_, w) -> w.sumOf { it.amount } }
        val totalCharged = charges.sumOf { it.amount }
        val totalPaid = paymentAmounts.sum()
        var totalWaived = 0L
        var pool = totalPaid
        val unpaid = ArrayList<MonthDue>()
        for (c in charges.sortedBy { it.month }) {
            val waived = minOf(waivedByMonth[c.month] ?: 0L, c.amount)
            totalWaived += waived
            val net = c.amount - waived
            val paidHere = minOf(pool, net)
            pool -= paidHere
            val remaining = net - paidHere
            if (remaining > 0) unpaid.add(MonthDue(c.month, c.dueDate, net, remaining))
        }
        val balance = totalCharged - totalWaived - totalPaid
        val status = when {
            unpaid.isEmpty() -> FeeStatus.PAID
            unpaid.any { it.dueDate.isBefore(today) } -> FeeStatus.OVERDUE
            unpaid.first().partiallyPaid -> FeeStatus.PARTIAL
            else -> FeeStatus.DUE
        }
        return LedgerSummary(totalCharged, totalWaived, totalPaid, balance, unpaid, status)
    }
}

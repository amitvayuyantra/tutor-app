package com.tutordiary.app.data.repo

import com.tutordiary.app.domain.ChargeLine
import com.tutordiary.app.domain.Ledger
import com.tutordiary.app.domain.LedgerSummary
import com.tutordiary.app.domain.WaiverLine
import java.time.LocalDate

/** Ledger for every student, computed in one pass. */
fun FeeData.ledgers(today: LocalDate): Map<Long, LedgerSummary> {
    val chargesBy = charges.groupBy { it.studentId }
    val waiversBy = waivers.groupBy { it.studentId }
    val paymentsBy = payments.groupBy { it.studentId }
    return students.associate { s ->
        s.id to Ledger.summarize(
            charges = chargesBy[s.id].orEmpty().map { ChargeLine(it.month, it.amount, LocalDate.ofEpochDay(it.dueDate)) },
            waivers = waiversBy[s.id].orEmpty().map { WaiverLine(it.month, it.amount) },
            paymentAmounts = paymentsBy[s.id].orEmpty().map { it.amount },
            today = today,
        )
    }
}

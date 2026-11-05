package com.tutordiary.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.tutordiary.app.R
import com.tutordiary.app.data.repo.Rule
import com.tutordiary.app.domain.FeeStatus
import com.tutordiary.app.domain.MonthKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** "05 Nov 2026" — the day-month-year order used in India. */
fun formatDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", locale))

fun formatEpochDay(epochDay: Long, locale: Locale): String = formatDate(LocalDate.ofEpochDay(epochDay), locale)

fun formatMonth(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

fun formatMonthKey(key: Int, locale: Locale): String = formatMonth(MonthKey.toYearMonth(key), locale)

fun statusLabel(status: FeeStatus): Int = when (status) {
    FeeStatus.PAID -> R.string.status_paid
    FeeStatus.PARTIAL -> R.string.status_partial
    FeeStatus.DUE -> R.string.status_due
    FeeStatus.OVERDUE -> R.string.status_overdue
}

fun ruleMessage(rule: Rule): Int = when (rule) {
    Rule.NAME_REQUIRED -> R.string.err_name_required
    Rule.PARENT_REQUIRED -> R.string.err_parent_required
    Rule.PHONE_INVALID -> R.string.err_phone_invalid
    Rule.FEE_INVALID -> R.string.err_fee_invalid
    Rule.DUE_DAY_INVALID -> R.string.err_due_day_invalid
    Rule.AMOUNT_NOT_POSITIVE -> R.string.err_amount_positive
    Rule.METHOD_INVALID -> R.string.err_generic
    Rule.NO_CHARGE_FOR_MONTH -> R.string.err_no_charge
    Rule.WAIVER_TOO_BIG -> R.string.err_waiver_too_big
    Rule.WAIVER_EXISTS -> R.string.err_waiver_exists
    Rule.NOTE_REQUIRED -> R.string.err_note_required
    Rule.NOT_FOUND -> R.string.err_not_found
}

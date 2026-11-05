package com.tutordiary.app.domain

import java.net.URLEncoder
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReminderData(
    val studentName: String,
    val parentName: String,
    val amountDue: Long,
    val monthsDue: List<YearMonth>,
    val tutorName: String,
    val upiId: String?,
)

/**
 * Fills a reminder template. Placeholders:
 * {student} {parent} {amount} {months} {upi} {tutor}
 * If the tutor has no UPI ID, every line that contains {upi} is removed.
 */
object Reminder {

    const val P_STUDENT = "{student}"
    const val P_PARENT = "{parent}"
    const val P_AMOUNT = "{amount}"
    const val P_MONTHS = "{months}"
    const val P_UPI = "{upi}"
    const val P_TUTOR = "{tutor}"

    fun monthsLabel(months: List<YearMonth>, locale: Locale): String {
        val f = DateTimeFormatter.ofPattern("MMM yyyy", locale)
        return months.joinToString(", ") { it.format(f) }
    }

    fun build(template: String, data: ReminderData, locale: Locale): String {
        val upi = data.upiId?.trim().orEmpty()
        val lines = template.lines().filter { upi.isNotEmpty() || !it.contains(P_UPI) }
        return lines.joinToString("\n")
            .replace(P_STUDENT, data.studentName.trim())
            .replace(P_PARENT, data.parentName.trim())
            .replace(P_AMOUNT, Money.format(data.amountDue))
            .replace(P_MONTHS, monthsLabel(data.monthsDue, locale))
            .replace(P_UPI, upi)
            .replace(P_TUTOR, data.tutorName.trim())
            .trim()
    }

    /** https://wa.me/919876543210?text=... */
    fun whatsAppLink(normalizedPhone: String, message: String): String {
        val text = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        return "https://wa.me/${PhoneNumbers.forWhatsApp(normalizedPhone)}?text=$text"
    }
}

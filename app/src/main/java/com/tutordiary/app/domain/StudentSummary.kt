package com.tutordiary.app.domain

import java.security.SecureRandom
import java.time.LocalDate

/**
 * The read-only view of one student that the tutor's phone shares online, and that the
 * student/parent sees in their app. It never contains the parent's phone number or the tutor's notes.
 */
data class StudentSummary(
    val key: String,
    val tutorUid: String,
    val studentName: String,
    val parentName: String,
    val tutorName: String,
    val upiId: String?,
    val monthlyFee: Long,
    val dueDay: Int,
    /** charges − waivers − payments. Positive = owes, negative = advance. */
    val balance: Long,
    val unpaid: List<UnpaidMonth>,
    /** Newest first. */
    val payments: List<PaymentLine>,
    /** Newest month first. */
    val attendance: List<AttendanceMonth>,
    val archived: Boolean,
    /** Tutor's date when this was last sent. */
    val updatedOn: Long,
) {
    data class UnpaidMonth(val month: Int, val dueDate: Long, val remaining: Long)
    data class PaymentLine(val date: Long, val amount: Long, val method: String)
    data class AttendanceMonth(val month: Int, val present: Int, val total: Int)

    val amountDue: Long get() = if (balance > 0) balance else 0
    val credit: Long get() = if (balance < 0) -balance else 0

    /** Status is worked out on the reader's phone, so "overdue" is right even if the tutor hasn't opened the app today. */
    fun status(today: LocalDate): FeeStatus = when {
        unpaid.isEmpty() -> FeeStatus.PAID
        unpaid.any { LocalDate.ofEpochDay(it.dueDate).isBefore(today) } -> FeeStatus.OVERDUE
        else -> FeeStatus.DUE
    }

    /** Only the fields the tutor writes. Linking fields (linkedUids, inviteCode) are managed separately. */
    fun toMap(): Map<String, Any?> = mapOf(
        "tutorUid" to tutorUid,
        "studentName" to studentName,
        "parentName" to parentName,
        "tutorName" to tutorName,
        "upiId" to upiId,
        "monthlyFee" to monthlyFee,
        "dueDay" to dueDay.toLong(),
        "balance" to balance,
        "unpaid" to unpaid.map { mapOf("m" to it.month.toLong(), "due" to it.dueDate, "left" to it.remaining) },
        "payments" to payments.map { mapOf("d" to it.date, "a" to it.amount, "k" to it.method) },
        "attendance" to attendance.map { mapOf("m" to it.month.toLong(), "p" to it.present.toLong(), "t" to it.total.toLong()) },
        "archived" to archived,
        "updatedOn" to updatedOn,
    )

    companion object {
        const val MAX_PAYMENTS = 24
        const val MAX_ATTENDANCE_MONTHS = 6

        fun keyFor(tutorUid: String, studentId: Long) = "${tutorUid}_$studentId"

        private fun Any?.long(): Long = (this as? Number)?.toLong() ?: 0L

        @Suppress("UNCHECKED_CAST")
        private fun Any?.maps(): List<Map<String, Any?>> = (this as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

        /** Reads what came back from the server. Missing or odd values become safe defaults. */
        fun fromMap(key: String, m: Map<String, Any?>): StudentSummary = StudentSummary(
            key = key,
            tutorUid = m["tutorUid"] as? String ?: "",
            studentName = m["studentName"] as? String ?: "",
            parentName = m["parentName"] as? String ?: "",
            tutorName = m["tutorName"] as? String ?: "",
            upiId = (m["upiId"] as? String)?.ifBlank { null },
            monthlyFee = m["monthlyFee"].long(),
            dueDay = m["dueDay"].long().toInt(),
            balance = m["balance"].long(),
            unpaid = m["unpaid"].maps().map { UnpaidMonth(it["m"].long().toInt(), it["due"].long(), it["left"].long()) },
            payments = m["payments"].maps().map { PaymentLine(it["d"].long(), it["a"].long(), it["k"] as? String ?: "OTHER") },
            attendance = m["attendance"].maps().map { AttendanceMonth(it["m"].long().toInt(), it["p"].long().toInt(), it["t"].long().toInt()) },
            archived = m["archived"] as? Boolean ?: false,
            updatedOn = m["updatedOn"].long(),
        )
    }
}

object InviteCodes {
    /** No 0/O, 1/I/L: easy to read out loud and type. */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6
    private val random = SecureRandom()

    fun generate(): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** "abc 12-3" -> "ABC123"; returns null unless it is a well-formed code. */
    fun normalize(input: String): String? {
        val c = input.uppercase().filter { it.isLetterOrDigit() }
        return if (c.length == LENGTH && c.all { it in ALPHABET }) c else null
    }
}

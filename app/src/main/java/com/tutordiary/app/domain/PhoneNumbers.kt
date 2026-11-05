package com.tutordiary.app.domain

/**
 * Indian mobile numbers: 10 digits starting with 6, 7, 8 or 9.
 * Everything is stored as "+91XXXXXXXXXX".
 */
object PhoneNumbers {

    /**
     * Returns "+91XXXXXXXXXX" or null if the number is not a valid Indian mobile.
     * Accepts spaces, dashes, brackets and these prefixes: +91, 91, 0091, 0.
     */
    fun normalize(input: String): String? {
        val compact = input.filter { it !in " -()" }
        if (compact.isEmpty()) return null
        val hasPlus = compact.startsWith("+")
        var digits = if (hasPlus) compact.substring(1) else compact
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        digits = when {
            hasPlus && digits.startsWith("91") && digits.length == 12 -> digits.substring(2)
            hasPlus -> return null // another country code
            digits.length == 14 && digits.startsWith("0091") -> digits.substring(4)
            digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            else -> digits
        }
        if (digits.length != 10) return null
        if (digits[0] !in '6'..'9') return null
        return "+91$digits"
    }

    /** "+919876543210" -> "919876543210" (format wa.me needs). */
    fun forWhatsApp(normalized: String): String = normalized.removePrefix("+")

    /** "+919876543210" -> "+91 98765 43210" for display. */
    fun display(normalized: String): String {
        val d = normalized.removePrefix("+91")
        return if (d.length == 10) "+91 ${d.substring(0, 5)} ${d.substring(5)}" else normalized
    }
}

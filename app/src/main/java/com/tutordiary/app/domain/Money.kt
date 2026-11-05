package com.tutordiary.app.domain

/**
 * Money is always whole rupees stored in a Long. Never Float or Double.
 */
object Money {

    /** 150000 -> "₹1,50,000" (Indian digit grouping). */
    fun format(rupees: Long): String {
        val negative = rupees < 0
        val digits = if (rupees == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(rupees).toString()
        val grouped = groupIndian(digits)
        return (if (negative) "-" else "") + "₹" + grouped
    }

    /** Groups digits the Indian way: last 3, then pairs. "150000" -> "1,50,000". */
    fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /**
     * Reads what the tutor typed. Accepts "1500", "1,500", "₹ 1,500", "Rs 1500".
     * Returns null for anything that is not a whole number of rupees (e.g. "15.50", "abc").
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim()
            .removePrefix("₹").removePrefix("Rs.").removePrefix("Rs").removePrefix("rs")
            .replace(",", "").replace(" ", "")
        if (cleaned.isEmpty() || cleaned.length > 12) return null
        if (!cleaned.all { it.isDigit() }) return null
        return cleaned.toLongOrNull()
    }
}

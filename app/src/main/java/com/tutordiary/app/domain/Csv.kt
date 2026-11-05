package com.tutordiary.app.domain

object Csv {
    /** Quotes a value when needed. Also blocks spreadsheet formula injection (=, +, -, @). */
    fun field(value: String?): String {
        var v = value.orEmpty()
        val plainNumber = v.matches(Regex("-?\\d+"))
        if (!plainNumber && v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
        val needsQuotes = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || v != v.trim()
        return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun row(values: List<String?>): String = values.joinToString(",") { field(it) }

    fun build(header: List<String>, rows: List<List<String?>>): String =
        buildString {
            append('﻿') // lets Excel open Hindi text correctly
            append(row(header)).append("\r\n")
            rows.forEach { append(row(it)).append("\r\n") }
        }
}

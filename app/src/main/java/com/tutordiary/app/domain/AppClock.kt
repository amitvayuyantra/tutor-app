package com.tutordiary.app.domain

import java.time.LocalDate

/** "Today" always means the phone's local date. Tests swap this for a fixed date. */
fun interface AppClock {
    fun today(): LocalDate
}

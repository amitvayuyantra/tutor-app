package com.tutordiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

class MoneyTest {
    @Test fun indianGrouping() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(999))
        assertEquals("₹1,000", Money.format(1000))
        assertEquals("₹1,50,000", Money.format(150000))
        assertEquals("₹12,34,56,789", Money.format(123456789))
        assertEquals("-₹1,500", Money.format(-1500))
    }

    @Test fun parsing() {
        assertEquals(1500L, Money.parse("1500"))
        assertEquals(150000L, Money.parse("1,50,000"))
        assertEquals(1500L, Money.parse("₹ 1,500"))
        assertEquals(1500L, Money.parse("Rs 1500"))
        assertNull(Money.parse("15.50"))
        assertNull(Money.parse("-200"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }
}

class PhoneNumbersTest {
    @Test fun acceptsCommonFormats() {
        val expected = "+919876543210"
        listOf("9876543210", "98765 43210", "+91 98765 43210", "+91-98765-43210", "919876543210",
            "09876543210", "0091 98765 43210", "(+91) 98765 43210").forEach {
            assertEquals("input: $it", expected, PhoneNumbers.normalize(it))
        }
    }

    @Test fun rejectsInvalid() {
        listOf("", "12345", "5876543210", "0123456789", "98765432101", "+1 415 555 0101",
            "98765abc10", "+44 7911 123456", "98765+43210").forEach {
            assertNull("input: $it", PhoneNumbers.normalize(it))
        }
    }

    @Test fun formatsForWhatsAppAndDisplay() {
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("+919876543210"))
        assertEquals("+91 98765 43210", PhoneNumbers.display("+919876543210"))
    }
}

class ReminderTest {
    private val template = "Namaste {parent} ji,\nFee for {student}: {amount} for {months}.\nUPI: {upi}\n- {tutor}"
    private val data = ReminderData("Aarav", "Mr Sharma", 3000, listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), "Priya", "priya@okaxis")

    @Test fun fillsAllPlaceholders() {
        val text = Reminder.build(template, data, Locale.ENGLISH)
        assertEquals("Namaste Mr Sharma ji,\nFee for Aarav: ₹3,000 for Oct 2026, Nov 2026.\nUPI: priya@okaxis\n- Priya", text)
    }

    @Test fun dropsUpiLineWhenNoUpi() {
        val text = Reminder.build(template, data.copy(upiId = null), Locale.ENGLISH)
        assertFalse(text.contains("UPI"))
        assertFalse(text.contains("{upi}"))
        assertTrue(text.endsWith("- Priya"))
    }

    @Test fun hindiMonths() {
        val label = Reminder.monthsLabel(listOf(YearMonth.of(2026, 11)), Locale.forLanguageTag("hi"))
        assertTrue(label.endsWith("2026"))
        assertFalse(label.contains("Nov"))
    }

    @Test fun whatsAppLinkIsEncoded() {
        val link = Reminder.whatsAppLink("+919876543210", "Hi & bye\nOK")
        assertEquals("https://wa.me/919876543210?text=Hi%20%26%20bye%0AOK", link)
    }
}

class CsvTest {
    @Test fun quotesAndEscapes() {
        assertEquals("plain", Csv.field("plain"))
        assertEquals("\"a,b\"", Csv.field("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.field("say \"hi\""))
        assertEquals("'=SUM(A1)", Csv.field("=SUM(A1)"))
        assertEquals("-500", Csv.field("-500"))
        assertEquals("", Csv.field(null))
    }

    @Test fun buildsRowsWithBom() {
        val out = Csv.build(listOf("Name", "Fee"), listOf(listOf("Aarav", "1500")))
        assertEquals("﻿Name,Fee\r\nAarav,1500\r\n", out)
    }
}

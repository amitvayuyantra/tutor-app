package com.tutordiary.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.tutordiary.app.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** People who installed version 1 must keep all their records when they update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun version1To2KeepsStudentsAndPayments() {
        helper.createDatabase("migrate.db", 1).apply {
            execSQL("INSERT INTO students (id, name, parentName, phone, batchId, monthlyFee, dueDay, joinDate, archivedOn, chargesFromMonth, notes, lastReminderOn) VALUES (1, 'Aarav', 'Mr Mehta', '+919876543210', NULL, 1500, 5, 20000, NULL, 202601, 'note', NULL)")
            execSQL("INSERT INTO payments (id, studentId, amount, date, method, note) VALUES (1, 1, 700, 20010, 'UPI', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migrate.db", 2, true)
        db.query("SELECT name, inviteCode FROM students").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Aarav", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT amount FROM payments").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(700L, c.getLong(0))
        }
    }
}

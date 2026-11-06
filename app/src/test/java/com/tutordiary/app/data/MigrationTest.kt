package com.tutordiary.app.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tutordiary.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * People who installed version 1 must keep all their records when they update.
 * Builds a real version-1 database from the saved schema file, then opens it with the current app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @Test fun version1To2KeepsStudentsAndPayments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val schema = Json.parseToJsonElement(File("schemas/com.tutordiary.app.data.db.AppDatabase/1.json").readText())
            .jsonObject["database"]!!.jsonObject
        val path = context.getDatabasePath("migrate.db").apply { parentFile?.mkdirs(); delete() }

        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            fun sql(s: String) = db.execSQL(s.replace("\${TABLE_NAME}", ""))
            for (entity in schema["entities"]!!.jsonArray) {
                val e = entity.jsonObject
                val table = e["tableName"]!!.jsonPrimitive.content
                db.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach { db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
            }
            schema["setupQueries"]!!.jsonArray.forEach { sql(it.jsonPrimitive.content) }
            db.execSQL("INSERT INTO students (id, name, parentName, phone, batchId, monthlyFee, dueDay, joinDate, archivedOn, chargesFromMonth, notes, lastReminderOn) VALUES (1, 'Aarav', 'Mr Mehta', '+919876543210', NULL, 1500, 5, 20000, NULL, 202601, 'note', NULL)")
            db.execSQL("INSERT INTO payments (id, studentId, amount, date, method, note) VALUES (1, 1, 700, 20010, 'UPI', NULL)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, "migrate.db").allowMainThreadQueries().build()
        try {
            val student = room.studentDao().get(1)!!
            assertEquals("Aarav", student.name)
            assertEquals("note", student.notes)
            assertNull(student.inviteCode)
            assertEquals(700L, room.paymentDao().all().single().amount)
        } finally {
            room.close()
        }
    }
}

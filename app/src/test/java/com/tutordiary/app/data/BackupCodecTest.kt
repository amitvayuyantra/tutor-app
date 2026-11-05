package com.tutordiary.app.data

import com.tutordiary.app.data.backup.AllData
import com.tutordiary.app.data.backup.BackupCodec
import com.tutordiary.app.data.backup.BackupProblem
import com.tutordiary.app.data.backup.DecodeResult
import com.tutordiary.app.data.db.AttendanceEntity
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.data.db.WaiverEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val sample = AllData(
        profile = TutorProfileEntity(1, "Priya", "Priya Classes", "priya@okaxis", "EN {student}", "HI {student}"),
        batches = listOf(BatchEntity(1, "Class 8 Maths", "Maths", "Mon, Wed", "5 PM", false)),
        students = listOf(
            StudentEntity(1, "Aarav", "Mr Sharma", "+919876543210", 1, 1500, 5, 20454, null, 202601, "Needs extra help", null)
        ),
        charges = listOf(ChargeEntity(1, 1, 202601, 1500, 20458)),
        waivers = listOf(WaiverEntity(1, 1, 202601, 500, "Diwali", 20460)),
        payments = listOf(PaymentEntity(1, 1, 1000, 20470, "UPI", "GPay")),
        attendance = listOf(AttendanceEntity(1, 1, 1, 20470, false)),
    )

    @Test fun roundTripKeepsEverything() {
        val text = BackupCodec.encode(sample, 20500)
        val result = BackupCodec.decode(text)
        assertTrue(result is DecodeResult.Ok)
        assertEquals(sample, (result as DecodeResult.Ok).data)
        assertTrue(text.contains("\"formatVersion\": 1"))
    }

    private fun problemFor(text: String) = (BackupCodec.decode(text) as DecodeResult.Bad).problem

    @Test fun rejectsNonJson() {
        assertEquals(BackupProblem.NotJson, problemFor("hello"))
    }

    @Test fun rejectsOtherApps() {
        assertEquals(BackupProblem.WrongApp, problemFor("""{"app":"other","formatVersion":1,"createdAtEpochDay":1}"""))
    }

    @Test fun rejectsNewerVersion() {
        assertEquals(BackupProblem.NewerVersion(99), problemFor("""{"app":"com.tutordiary.app","formatVersion":99,"createdAtEpochDay":1}"""))
    }

    @Test fun rejectsBrokenLinksAndBadValues() {
        fun bad(data: AllData) = BackupCodec.decode(BackupCodec.encode(data, 1)) is DecodeResult.Bad
        assertTrue(bad(sample.copy(payments = listOf(PaymentEntity(1, 99, 1000, 1, "UPI", null)))))
        assertTrue(bad(sample.copy(payments = listOf(PaymentEntity(1, 1, 0, 1, "UPI", null)))))
        assertTrue(bad(sample.copy(payments = listOf(PaymentEntity(1, 1, 10, 1, "CHEQUE", null)))))
        assertTrue(bad(sample.copy(charges = sample.charges + ChargeEntity(2, 1, 202601, 1500, 1))))
        assertTrue(bad(sample.copy(waivers = listOf(WaiverEntity(1, 1, 202601, 9999, "x", 1)))))
        assertTrue(bad(sample.copy(students = listOf(sample.students[0].copy(phone = "12345")))))
        assertTrue(bad(sample.copy(students = listOf(sample.students[0].copy(dueDay = 32)))))
        assertTrue(bad(sample.copy(students = listOf(sample.students[0].copy(batchId = 42)))))
    }

    @Test fun acceptsEmptyBackup() {
        val empty = AllData(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(BackupCodec.decode(BackupCodec.encode(empty, 1)) is DecodeResult.Ok)
    }
}

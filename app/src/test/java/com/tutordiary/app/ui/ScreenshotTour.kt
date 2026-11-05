package com.tutordiary.app.ui

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.tutordiary.app.MainActivity
import com.tutordiary.app.R
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.repo.StudentInput
import com.tutordiary.app.data.repo.TutorRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/**
 * Not a pass/fail test: walks through the app with sample data and saves a picture of each screen
 * to app/build/screenshots, in English, dark mode and Hindi. Phone size: 412 x 915 dp.
 */
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-xhdpi")
class ScreenshotTour {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var repo: TutorRepository

    @Before fun inject() = hilt.inject()

    @Test fun english() = tour("en-light")

    @Config(qualifiers = "+night")
    @Test fun dark() = tour("en-dark")

    @Config(qualifiers = "+hi")
    @Test fun hindi() = tour("hi")

    private fun t(@StringRes id: Int): String = compose.activity.getString(id)

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()

    private fun shot(prefix: String, name: String) {
        compose.waitForIdle()
        val roots = compose.onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        val bitmap = roots[count - 1].captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$prefix-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Saved $prefix-$name.png")
    }

    private fun seed() = runBlocking {
        val today = repo.today()
        fun monthsAgo(n: Long, day: Int = 1): LocalDate = today.minusMonths(n).withDayOfMonth(day)
        repo.saveProfile("Priya Sharma", "Priya Tuition Classes", "priya@okaxis",
            t(R.string.default_template_en), t(R.string.default_template_hi))
        val maths = repo.saveBatch(BatchEntity(name = "Class 8 Maths", subject = "Maths", classDays = "Mon, Wed, Fri", classTime = "5:00 PM"))
        val science = repo.saveBatch(BatchEntity(name = "Class 9 Science", subject = "Science", classDays = "Tue, Thu, Sat", classTime = "6:30 PM"))

        fun student(name: String, parent: String, phone: String, batch: Long, fee: Long, due: Int, join: LocalDate) =
            StudentInput(0, name, parent, phone, batch, fee, due, join, null)
        suspend fun pay(id: Long, amount: Long, daysAgo: Long, method: String) =
            repo.addPayment(PaymentEntity(studentId = id, amount = amount, date = today.minusDays(daysAgo).toEpochDay(), method = method, note = null))

        val aarav = repo.addStudent(student("Aarav Mehta", "Rakesh Mehta", "98765 43210", maths, 1500, 5, monthsAgo(3)))
        pay(aarav, 1500, 70, "UPI")
        val diya = repo.addStudent(student("Diya Patel", "Neha Patel", "91234 56789", maths, 1200, 10, monthsAgo(2)))
        pay(diya, 3600, 3, "CASH")
        val kabir = repo.addStudent(student("Kabir Singh", "Harpreet Singh", "99887 76655", science, 2000, 7, monthsAgo(1)))
        pay(kabir, 3000, 10, "UPI")
        repo.addStudent(student("Ananya Iyer", "Lakshmi Iyer", "90000 12345", science, 1800, 28, today.withDayOfMonth(1)))
        val rohan = repo.addStudent(student("Rohan Das", "Sunita Das", "88776 65544", maths, 1500, 5, monthsAgo(1)))
        pay(rohan, 6000, 20, "OTHER")

        // A few days of attendance for Class 8 Maths.
        for (d in 1L..6L) {
            repo.saveAttendance(maths, today.minusDays(d), mapOf(aarav to (d != 3L), diya to true, rohan to (d % 2 == 0L)))
        }
    }

    private fun tour(prefix: String) {
        waitFor(t(R.string.setup_title))
        shot(prefix, "01-setup")
        seed()

        waitFor(t(R.string.home_unpaid_list))
        waitFor("Aarav Mehta")
        shot(prefix, "02-home")

        click(t(R.string.tab_students))
        waitFor("Ananya Iyer")
        shot(prefix, "03-students")

        compose.onNode(hasText("Aarav Mehta") and hasClickAction()).performClick()
        waitFor(t(R.string.section_fees))
        shot(prefix, "04-student")

        click(t(R.string.send_reminder))
        waitFor(t(R.string.reminder_title))
        shot(prefix, "05-reminder")
        click(t(R.string.close))

        click(t(R.string.record_payment))
        waitFor(t(R.string.field_amount))
        shot(prefix, "06-payment")
        compose.onNode(hasContentDescription(t(R.string.cd_back))).performClick()
        waitFor(t(R.string.section_fees))
        compose.onNode(hasContentDescription(t(R.string.cd_back))).performClick()

        waitFor(t(R.string.tab_attendance))
        click(t(R.string.tab_attendance))
        waitFor(t(R.string.mark_all_present))
        shot(prefix, "07-attendance")

        click(t(R.string.tab_settings))
        waitFor(t(R.string.section_backup))
        shot(prefix, "08-settings")
    }
}

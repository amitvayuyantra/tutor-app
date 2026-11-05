package com.tutordiary.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.domain.ChargeLine
import com.tutordiary.app.domain.Ledger
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.students.AttendanceSummary
import com.tutordiary.app.ui.students.MonthLine
import com.tutordiary.app.ui.students.StudentDetailBody
import com.tutordiary.app.ui.students.StudentDetailState
import com.tutordiary.app.ui.theme.TutorDiaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/** Renders screen parts on their own and checks they settle (no endless redrawing). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h2000dp")
class ScreenPartsTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.parse("2026-11-05")
    private val student = StudentEntity(1, "Aarav", "Mr Sharma", "+919876543210", null, 1500, 5,
        today.toEpochDay(), null, 202611, "Needs help", today.toEpochDay())
    private val charge = ChargeEntity(1, 1, 202611, 1500, today.toEpochDay())
    private val ledger = Ledger.summarize(listOf(ChargeLine(202611, 1500, today)), emptyList(), listOf(500), today)
    private val state = StudentDetailState(
        loading = false,
        student = student,
        batchName = "Class 8",
        ledger = ledger,
        months = listOf(MonthLine(charge, null, 1000)),
        payments = listOf(PaymentEntity(1, 1, 500, today.toEpochDay(), "UPI", "GPay")),
        profile = TutorProfileEntity(1, "Priya", null, "priya@okaxis", "EN", "HI"),
    )

    @Test fun studentBodySettles() {
        compose.setContent {
            TutorDiaryTheme { StudentDetailBody(student, state, AttendanceSummary(YearMonth.of(2026, 11), 3, 1)) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Balance due").assertExists()
        compose.onNodeWithText("Present 3 of 4 days").assertExists()
    }

    @Test fun formFieldSettles() {
        compose.setContent {
            TutorDiaryTheme {
                com.tutordiary.app.ui.common.FormField("", {}, "Reason (required)")
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Reason (required)").assertExists()
    }

    @Test fun scaffoldWithSnackbarSettles() {
        compose.setContent {
            TutorDiaryTheme {
                AppScaffold(
                    title = "Aarav",
                    onBack = {},
                    snackbar = androidx.compose.runtime.remember { SnackbarHostState() },
                    actions = { IconButton(onClick = {}) { Icon(Icons.Filled.Edit, contentDescription = "Edit") } },
                ) { Text("content") }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("content").assertExists()
    }
}

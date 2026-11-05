package com.tutordiary.app.ui

import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.tutordiary.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Opens the real app and taps through it like a tutor would:
 * setup → add student → record payment → open WhatsApp reminder → waiver → batches → attendance → settings.
 */
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w420dp-h2000dp")
class AppFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun SemanticsNodeInteractionsProvider.clickButton(text: String) =
        onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()

    @Test
    fun tutorCanSetUpAddStudentRecordPaymentAndRemind() {
        // 1. First-launch setup
        println("STEP 1: First-launch setup")
        waitFor("Welcome to Tutor Diary")
        compose.onNodeWithText("Your name").performTextInput("Priya")
        compose.onNodeWithText("UPI ID (optional)").performTextInput("priya@okaxis")
        compose.onNodeWithText("Start").performClick()

        // 2. Home, empty
        println("STEP 2: Home, empty")
        waitFor("Expected this month")
        waitFor("No students yet")
        compose.clickButton("Add student")

        // 3. Add a student (bad phone first, to see validation)
        println("STEP 3: Add a student (bad phone first, to see validation)")
        waitFor("New student")
        compose.onNodeWithText("Student name").performTextInput("Aarav")
        compose.onNodeWithText("Parent name").performTextInput("Mr Sharma")
        compose.onNodeWithText("Parent WhatsApp number").performTextInput("12345")
        compose.onNodeWithText("Monthly fee (₹)").performTextInput("1500")
        compose.clickButton("Save")
        waitFor("Enter a valid 10-digit Indian mobile number.")
        compose.onNodeWithText("Parent WhatsApp number").performTextClearance()
        compose.onNodeWithText("Parent WhatsApp number").performTextInput("98765 43210")
        compose.clickButton("Save")

        // 4. Back on home: the student owes this month's fee
        println("STEP 4: Back on home: the student owes this month's fee")
        waitFor("Students with unpaid fees")
        waitFor("Aarav")
        assertTrue(compose.onAllNodesWithText("₹1,500").fetchSemanticsNodes().isNotEmpty())

        // 5. Record a part payment
        println("STEP 5: Record a part payment")
        compose.clickButton("Record payment")
        waitFor("Amount (₹)")
        compose.onNodeWithText("Amount (₹)").performTextInput("500")
        compose.clickButton("UPI")
        compose.clickButton("Save")
        waitFor("Students with unpaid fees")
        waitFor("₹1,000")

        // 6. Reminder: preview contains the details, then opens wa.me
        println("STEP 6: Reminder: preview contains the details, then opens wa.me")
        compose.clickButton("Send reminder")
        waitFor("Fee reminder")
        waitFor("priya@okaxis", substring = true)
        waitFor("Amount due: ₹1,000", substring = true)
        compose.clickButton("Open WhatsApp")
        val started: Intent? = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertTrue(started?.dataString.orEmpty().startsWith("https://wa.me/919876543210?text="))

        // 7. Student page: last reminder recorded, waive the month
        println("STEP 7: Student page: last reminder recorded, waive the month")
        compose.onNode(hasText("Aarav") and hasClickAction()).performClick()
        waitFor("Last reminder opened", substring = true)
        compose.clickButton("Waive fee")
        waitFor("Waive fee for a month")
        compose.onNodeWithText("Reason (required)").performTextInput("Diwali")
        compose.clickButton("Save")
        waitFor("Advance paid (credit)")
        waitFor("₹500")

        // 8. Batches and attendance
        println("STEP 8: Batches and attendance")
        compose.onNode(hasContentDescription("Go back")).performClick()
        waitFor("Expected this month")
        compose.clickButton("Attendance")
        waitFor("No batches yet")
        compose.clickButton("Create batch")
        waitFor("Batches")
        compose.clickButton("New batch")
        compose.onNodeWithText("Batch name").performTextInput("Class 8 Maths")
        compose.clickButton("Save")
        waitFor("Class 8 Maths")
        compose.onNode(hasContentDescription("Go back")).performClick()
        waitFor("No active students in this batch.")

        // 9. Settings
        println("STEP 9: Settings")
        compose.clickButton("Settings")
        waitFor("Backup and export")
        waitFor("No backup made yet")
        compose.clickButton("Edit reminder messages")
        waitFor("English message")
        waitFor("{student}", substring = true)
    }
}

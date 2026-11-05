package com.tutordiary.app.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.tutordiary.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Opens a student's page straight after adding them, and reports anything that keeps changing. */
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w420dp-h2000dp")
class StudentPageTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()

    @Test
    fun studentPageSettles() {
        waitFor("Welcome to Tutor Diary")
        compose.onNodeWithText("Your name").performTextInput("Priya")
        compose.onNodeWithText("Start").performClick()
        waitFor("No students yet")
        click("Add student")
        waitFor("New student")
        compose.onNodeWithText("Student name").performTextInput("Aarav")
        compose.onNodeWithText("Parent name").performTextInput("Mr Sharma")
        compose.onNodeWithText("Parent WhatsApp number").performTextInput("9876543210")
        compose.onNodeWithText("Monthly fee (₹)").performTextInput("1500")
        click("Save")
        waitFor("Students with unpaid fees")

        // Record every state change from here on.
        val changes = ConcurrentHashMap<String, AtomicInteger>()
        val handle = Snapshot.registerApplyObserver { changed, _ ->
            changed.forEach { obj ->
                val key = obj.toString().take(400)
                changes.getOrPut(key) { AtomicInteger() }.incrementAndGet()
            }
        }
        try {
            compose.onNode(hasText("Aarav") and hasClickAction()).performClick()
            waitFor("Balance due")
        } catch (t: Throwable) {
            println("STATE CHANGES WHILE STUCK (most frequent first):")
            changes.entries.sortedByDescending { it.value.get() }.take(15).forEach {
                println("  ${it.value.get()} x ${it.key}")
            }
            throw t
        } finally {
            handle.dispose()
        }
    }
}

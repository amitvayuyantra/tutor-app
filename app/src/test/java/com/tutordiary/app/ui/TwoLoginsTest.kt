package com.tutordiary.app.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.tutordiary.app.MainActivity
import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.cloud.AuthUser
import com.tutordiary.app.data.cloud.CloudStore
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.repo.StudentInput
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.sync.TutorSync
import com.tutordiary.app.testing.FakeAuth
import com.tutordiary.app.testing.FakeCloud
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/**
 * Tutor signs in, invites a parent with a code, records a payment, signs out.
 * Parent signs in on the same (test) phone, enters the code and sees only that student's fees.
 */
@HiltAndroidTest
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w420dp-h2000dp")
class TwoLoginsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var authService: AuthService
    @Inject lateinit var cloudStore: CloudStore
    @Inject lateinit var repo: TutorRepository
    private val auth get() = authService as FakeAuth
    private val cloud get() = cloudStore as FakeCloud

    @Before fun setUp() {
        hilt.inject()
        TutorSync.DEBOUNCE_MS = 50
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        try { node.performScrollTo() } catch (e: AssertionError) { }
        node.performClick()
    }

    private fun pressBack() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun tutorInvitesAndParentSeesOnlyTheirFees() {
        // ---- Tutor ----
        auth.nextUser = AuthUser("tutor-uid", "priya@gmail.com", "Priya")
        waitFor("Who is using this phone?")
        click("I'm the tutor")
        click("Sign in with Google")
        waitFor("Welcome to Tutor Diary")
        compose.onNodeWithText("Your name").performTextInput("Priya")
        compose.onNodeWithText("UPI ID (optional)").performTextInput("priya@okaxis")
        click("Start")
        waitFor("Expected this month")

        val (aarav, other) = runBlocking {
            val today = repo.today()
            repo.addStudent(StudentInput(0, "Aarav Mehta", "Rakesh Mehta", "9876543210", null, 1500, 5, today.withDayOfMonth(1), "private note")) to
                repo.addStudent(StudentInput(0, "Other Child", "Someone", "9123456789", null, 2000, 5, today.withDayOfMonth(1), null))
        }
        waitFor("Aarav Mehta")
        compose.onNode(hasText("Aarav Mehta") and hasClickAction()).performClick()
        waitFor("Fees by month")
        click("Manage student app access")
        waitFor("Make invite code")
        click("Make invite code")
        waitFor("Send code on WhatsApp")
        val code = cloud.invites.keys.single()
        assertEquals(6, code.length)
        waitFor(code.chunked(3).joinToString(" "))

        // A payment recorded later reaches the shared record automatically.
        runBlocking { repo.addPayment(PaymentEntity(studentId = aarav, amount = 500, date = repo.today().toEpochDay(), method = "UPI", note = null)) }
        compose.waitUntil(10_000) { cloud.docsNow.values.singleOrNull()?.summary?.balance == 1000L }
        assertFalse("other students are never shared", cloud.docsNow.keys.any { it.endsWith("_$other") })

        // Sign out from Settings.
        pressBack()
        waitFor("Fees by month")
        pressBack()
        waitFor("Expected this month")
        click("Settings")
        waitFor("Signed in as priya@gmail.com")
        click("Sign out")
        waitFor("Your records stay on this phone", substring = true)
        val signOutButtons = compose.onAllNodes(hasText("Sign out") and hasClickAction())
        val n = signOutButtons.fetchSemanticsNodes().size
        assertTrue(n >= 2)
        signOutButtons[n - 1].performClick() // the confirm button in the pop-up
        waitFor("Who is using this phone?")

        // ---- Parent ----
        auth.nextUser = AuthUser("parent-uid", "rakesh@gmail.com", "Rakesh")
        click("I'm a student or parent")
        waitFor("Student / parent sign-in")
        click("Sign in with Google")
        waitFor("Enter your code")

        // Wrong code first.
        compose.onNodeWithText("6-letter code").performTextInput("ZZZZZZ")
        click("Join")
        waitFor("This code doesn't work. Ask your tutor for a new one.")

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(code)
        click("Join")
        waitFor("Balance due")
        waitFor("Aarav Mehta")
        waitFor("₹1,000")
        waitFor("Tutor: Priya")
        waitFor("Pay by UPI to: priya@okaxis")
        assertTrue(compose.onAllNodesWithText("private note", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Other Child").fetchSemanticsNodes().isEmpty())
        assertNotNull(cloud.docsNow.values.single().linkedUids.singleOrNull { it == "parent-uid" })

        // The same code can't be used by a second person.
        runBlocking {
            assertEquals(com.tutordiary.app.data.cloud.ClaimResult.AlreadyUsed, cloud.claimInvite(code, "someone-else"))
        }
    }
}

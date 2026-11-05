package com.tutordiary.app.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tutordiary.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LanguageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test @Config(qualifiers = "en")
    fun englishText() {
        assertEquals("Home", context.getString(R.string.tab_home))
        assertEquals("Months due: 2", context.getString(R.string.label_months_due, 2))
    }

    @Test @Config(qualifiers = "hi")
    fun hindiText() {
        assertEquals("होम", context.getString(R.string.tab_home))
        assertEquals("बकाया महीने: 2", context.getString(R.string.label_months_due, 2))
        // Both reminder templates are available whatever the app language.
        assertTrue(context.getString(R.string.default_template_hi).contains("{student}"))
        assertTrue(context.getString(R.string.default_template_en).contains("{upi}"))
    }
}

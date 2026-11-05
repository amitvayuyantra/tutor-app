@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tutordiary.app.R
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.db.TutorProfileEntity
import com.tutordiary.app.domain.LedgerSummary
import com.tutordiary.app.domain.MonthKey
import com.tutordiary.app.domain.Reminder
import com.tutordiary.app.domain.ReminderData
import java.util.Locale

/**
 * Shows the reminder text, lets the tutor pick English or Hindi, then opens WhatsApp.
 * Nothing is ever sent automatically: the tutor presses Send inside WhatsApp.
 */
@Composable
fun ReminderDialog(
    student: StudentEntity,
    ledger: LedgerSummary,
    profile: TutorProfileEntity,
    onOpened: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appLocale = currentLocale()
    var hindi by rememberSaveable { mutableStateOf(appLocale.language == "hi") }
    var failed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val message = remember(hindi, ledger, profile, student) {
        Reminder.build(
            template = if (hindi) profile.templateHi else profile.templateEn,
            data = ReminderData(
                studentName = student.name,
                parentName = student.parentName,
                amountDue = ledger.amountDue,
                monthsDue = ledger.unpaidMonths.map { MonthKey.toYearMonth(it.month) },
                tutorName = profile.businessName ?: profile.tutorName,
                upiId = profile.upiId,
            ),
            locale = if (hindi) Locale.forLanguageTag("hi-IN") else Locale.forLanguageTag("en-IN"),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reminder_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !hindi, onClick = { hindi = false }, label = { Text(stringResource(R.string.lang_english)) })
                    FilterChip(selected = hindi, onClick = { hindi = true }, label = { Text(stringResource(R.string.lang_hindi)) })
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Text(message, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.reminder_send_hint), style = MaterialTheme.typography.bodySmall)
                if (failed) {
                    Text(stringResource(R.string.whatsapp_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (copied) {
                    Text(stringResource(R.string.reminder_copied), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = {
                        if (openWhatsApp(context, student.phone, message)) {
                            onOpened()
                            onDismiss()
                        } else {
                            failed = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.open_whatsapp)) }
                OutlinedButton(
                    onClick = { copyText(context, message); copied = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.copy)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private fun openWhatsApp(context: Context, phone: String, message: String): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Reminder.whatsAppLink(phone, message)))
    context.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("reminder", text))
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutordiary.app.R
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.domain.Money
import com.tutordiary.app.domain.PhoneNumbers
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.ConfirmDialog
import com.tutordiary.app.ui.common.EmptyState
import com.tutordiary.app.ui.common.FieldKeyboard
import com.tutordiary.app.ui.common.FormField
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.ReminderDialog
import com.tutordiary.app.ui.common.SectionCard
import com.tutordiary.app.ui.common.SectionTitle
import com.tutordiary.app.ui.common.Selector
import com.tutordiary.app.ui.common.SmallChip
import com.tutordiary.app.ui.common.StatusChip
import com.tutordiary.app.ui.common.currentLocale
import com.tutordiary.app.ui.common.formatDate
import com.tutordiary.app.ui.common.formatEpochDay
import com.tutordiary.app.ui.common.formatMonth
import com.tutordiary.app.ui.common.formatMonthKey
import com.tutordiary.app.ui.theme.AmountStyle

internal enum class Confirm { ARCHIVE, UNARCHIVE, DELETE }

@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecordPayment: (Long) -> Unit,
    onEditPayment: (studentId: Long, paymentId: Long) -> Unit,
    vm: StudentDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val attendance by vm.attendance.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var showReminder by rememberSaveable { mutableStateOf(false) }
    var showWaiver by rememberSaveable { mutableStateOf(false) }
    var removeWaiverId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(context.getString(it)); vm.message.value = null }
    }

    val student = state.student
    AppScaffold(
        title = student?.name ?: stringResource(R.string.student_details),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (student != null) {
                IconButton(onClick = { onEdit(student.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.student_edit))
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox()
            student == null -> EmptyState(stringResource(R.string.err_not_found), "")
            else -> StudentDetailBody(
                student = student,
                state = state,
                attendance = attendance,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRecordPayment = { onRecordPayment(student.id) },
                onSendReminder = { showReminder = true },
                onWaive = { showWaiver = true },
                onRemoveWaiver = { removeWaiverId = it },
                onEditPayment = { onEditPayment(student.id, it) },
                onShiftMonth = vm::shiftAttendanceMonth,
                onConfirm = { confirm = it },
            )
        }
    }

    // ---- Dialogs ----
    when (confirm) {
        Confirm.ARCHIVE -> ConfirmDialog(
            stringResource(R.string.archive_confirm_title), stringResource(R.string.archive_confirm_body),
            stringResource(R.string.archive), onConfirm = { vm.archive(); confirm = null }, onDismiss = { confirm = null },
        )
        Confirm.UNARCHIVE -> ConfirmDialog(
            stringResource(R.string.unarchive), stringResource(R.string.unarchive_confirm_body),
            stringResource(R.string.unarchive), onConfirm = { vm.unarchive(); confirm = null }, onDismiss = { confirm = null },
        )
        Confirm.DELETE -> ConfirmDialog(
            stringResource(R.string.delete_forever_title),
            stringResource(R.string.delete_forever_body, student?.name ?: ""),
            stringResource(R.string.delete_forever_confirm), destructive = true,
            onConfirm = { confirm = null; vm.deleteForever(onBack) }, onDismiss = { confirm = null },
        )
        null -> Unit
    }
    removeWaiverId?.let { id ->
        ConfirmDialog(
            stringResource(R.string.remove_waiver), stringResource(R.string.remove_waiver_confirm),
            stringResource(R.string.remove_waiver), destructive = true,
            onConfirm = { vm.deleteWaiver(id); removeWaiverId = null }, onDismiss = { removeWaiverId = null },
        )
    }
    val ledger = state.ledger
    val profile = state.profile
    if (showReminder && student != null && ledger != null && profile != null) {
        ReminderDialog(student, ledger, profile, onOpened = vm::markReminderOpened, onDismiss = { showReminder = false })
    }
    if (showWaiver) {
        WaiverDialog(
            months = state.months.filter { it.waiver == null },
            onSave = { month, amount, note -> vm.addWaiver(month, amount, note) { showWaiver = false } },
            onDismiss = { showWaiver = false },
        )
    }
}

/** The scrolling content of a student's page. Stateless, so it can be tested on its own. */
@Composable
internal fun StudentDetailBody(
    student: StudentEntity,
    state: StudentDetailState,
    attendance: AttendanceSummary?,
    modifier: Modifier = Modifier,
    onRecordPayment: () -> Unit = {},
    onSendReminder: () -> Unit = {},
    onWaive: () -> Unit = {},
    onRemoveWaiver: (Long) -> Unit = {},
    onEditPayment: (Long) -> Unit = {},
    onShiftMonth: (Long) -> Unit = {},
    onConfirm: (Confirm) -> Unit = {},
) {
    val locale = currentLocale()
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ledger = state.ledger
        // ---- Balance ----
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when {
                        ledger == null || (ledger.amountDue == 0L && ledger.credit == 0L) ->
                            Text(stringResource(R.string.all_clear), style = AmountStyle)
                        ledger.amountDue > 0 -> {
                            Text(stringResource(R.string.balance_due), style = MaterialTheme.typography.labelLarge)
                            Text(Money.format(ledger.amountDue), style = AmountStyle)
                        }
                        else -> {
                            Text(stringResource(R.string.advance_credit), style = MaterialTheme.typography.labelLarge)
                            Text(Money.format(ledger.credit), style = AmountStyle)
                        }
                    }
                }
                if (ledger != null) StatusChip(ledger.status)
            }
            if (ledger != null && ledger.unpaidMonths.isNotEmpty()) {
                Text(
                    stringResource(R.string.label_months_due, ledger.unpaidMonths.size) + " · " +
                        stringResource(R.string.due_since, formatDate(ledger.oldestDueDate!!, locale)),
                )
            }
            Text(
                student.lastReminderOn?.let { stringResource(R.string.last_reminder, formatEpochDay(it, locale)) }
                    ?: stringResource(R.string.last_reminder_never),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRecordPayment, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.record_payment))
                }
                OutlinedButton(
                    onClick = onSendReminder,
                    enabled = state.profile != null && (ledger?.amountDue ?: 0) > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.send_reminder)) }
            }
            Text(stringResource(R.string.reminder_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- Details ----
        SectionCard {
            if (student.archivedOn != null) {
                SmallChip(stringResource(R.string.archived_on, formatEpochDay(student.archivedOn, locale)))
            }
            Text(stringResource(R.string.parent_label, student.parentName))
            Text(stringResource(R.string.phone_label, PhoneNumbers.display(student.phone)))
            Text(stringResource(R.string.batch_label, state.batchName ?: stringResource(R.string.no_batch)))
            Text(stringResource(R.string.fee_label, Money.format(student.monthlyFee), student.dueDay))
            Text(stringResource(R.string.joined_label, formatEpochDay(student.joinDate, locale)))
            student.notes?.let { Text(stringResource(R.string.notes_label, it)) }
        }

        // ---- Fees by month ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.section_fees), Modifier.weight(1f))
            if (state.months.any { it.waiver == null }) {
                TextButton(onClick = onWaive) { Text(stringResource(R.string.waive_month)) }
            }
        }
        if (state.months.isEmpty()) Text(stringResource(R.string.no_charges))
        state.months.forEach { line ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMonthKey(line.charge.month, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (line.remaining > 0) stringResource(R.string.remaining_label, Money.format(line.remaining))
                        else stringResource(R.string.paid_full),
                        color = if (line.remaining > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    stringResource(R.string.month_fee_line, Money.format(line.charge.amount), formatEpochDay(line.charge.dueDate, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                line.waiver?.let { w ->
                    Text(stringResource(R.string.waived_label, Money.format(w.amount)) + " — " + w.note, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onRemoveWaiver(w.id) }) { Text(stringResource(R.string.remove_waiver)) }
                }
            }
        }

        // ---- Payments ----
        SectionTitle(stringResource(R.string.section_payments))
        if (state.payments.isEmpty()) Text(stringResource(R.string.no_payments))
        state.payments.forEach { p -> PaymentRow(p, locale) { onEditPayment(p.id) } }

        // ---- Attendance ----
        SectionTitle(stringResource(R.string.section_attendance))
        attendance?.let { a ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onShiftMonth(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_month))
                    }
                    Text(formatMonth(a.month, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onShiftMonth(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
                    }
                }
                if (a.present + a.absent == 0) Text(stringResource(R.string.attendance_none))
                else Text(stringResource(R.string.attendance_summary, a.present, a.present + a.absent), style = MaterialTheme.typography.bodyLarge)
            }
        }

        // ---- Archive / delete ----
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (student.archivedOn == null) {
            OutlinedButton(onClick = { onConfirm(Confirm.ARCHIVE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.archive))
            }
        } else {
            OutlinedButton(onClick = { onConfirm(Confirm.UNARCHIVE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.unarchive))
            }
        }
        TextButton(onClick = { onConfirm(Confirm.DELETE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PaymentRow(p: PaymentEntity, locale: java.util.Locale, onClick: () -> Unit) {
    val method = stringResource(
        when (p.method) {
            "CASH" -> R.string.method_cash
            "UPI" -> R.string.method_upi
            else -> R.string.method_other
        }
    )
    SectionCard(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatEpochDay(p.date, locale), style = MaterialTheme.typography.titleMedium)
                Text(method + (p.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            }
            Text(Money.format(p.amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun WaiverDialog(months: List<MonthLine>, onSave: (Int, Long, String) -> Unit, onDismiss: () -> Unit) {
    val locale = currentLocale()
    var month by rememberSaveable { mutableStateOf(months.firstOrNull()?.charge?.month) }
    val charge = months.firstOrNull { it.charge.month == month }?.charge
    var amount by rememberSaveable(month) { mutableStateOf(charge?.amount?.toString() ?: "") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.waiver_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (months.isEmpty()) {
                    Text(stringResource(R.string.waiver_no_months))
                } else {
                    Selector(
                        label = stringResource(R.string.field_month),
                        options = months.map { it.charge.month to formatMonthKey(it.charge.month, locale) },
                        selected = month,
                        onSelect = { month = it; error = null },
                    )
                    FormField(amount, { amount = it; error = null }, stringResource(R.string.field_waiver_amount), keyboard = FieldKeyboard.Number)
                    FormField(note, { note = it; error = null }, stringResource(R.string.field_waiver_note), keyboard = FieldKeyboard.Text)
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = months.isNotEmpty(),
                onClick = {
                    val m = month
                    val a = Money.parse(amount)
                    when {
                        m == null || charge == null -> error = R.string.err_no_charge
                        a == null -> error = R.string.err_amount_invalid
                        a <= 0 -> error = R.string.err_amount_positive
                        a > charge.amount -> error = R.string.err_waiver_too_big
                        note.isBlank() -> error = R.string.err_note_required
                        else -> onSave(m, a, note)
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

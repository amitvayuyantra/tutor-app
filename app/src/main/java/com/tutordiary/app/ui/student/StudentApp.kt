@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.student

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.cloud.ClaimResult
import com.tutordiary.app.data.cloud.CloudStore
import com.tutordiary.app.domain.AppClock
import com.tutordiary.app.domain.InviteCodes
import com.tutordiary.app.domain.Money
import com.tutordiary.app.domain.StudentSummary
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.ConfirmDialog
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.SectionCard
import com.tutordiary.app.ui.common.SectionTitle
import com.tutordiary.app.ui.common.SmallChip
import com.tutordiary.app.ui.common.StatusChip
import com.tutordiary.app.ui.common.currentLocale
import com.tutordiary.app.ui.common.formatEpochDay
import com.tutordiary.app.ui.common.formatMonthKey
import com.tutordiary.app.ui.theme.AmountStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/** Read-only app for a student or parent. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudentViewModel @Inject constructor(
    private val auth: AuthService,
    private val cloud: CloudStore,
    private val clock: AppClock,
) : ViewModel() {

    /** Null while loading. */
    val records: StateFlow<List<StudentSummary>?> = auth.user
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else cloud.observeMyStudents(u.uid) }
        .map<List<StudentSummary>, List<StudentSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val email: String? get() = auth.user.value?.email
    fun today(): LocalDate = clock.today()

    val joining = MutableStateFlow(false)
    val joinError = MutableStateFlow<Int?>(null)

    fun join(input: String, onDone: () -> Unit) {
        val code = InviteCodes.normalize(input)
        if (code == null) { joinError.value = R.string.join_bad_code; return }
        val uid = auth.user.value?.uid ?: return
        joining.value = true
        joinError.value = null
        viewModelScope.launch {
            val result = withTimeoutOrNull(20_000) { cloud.claimInvite(code, uid) } ?: ClaimResult.NoInternet
            joining.value = false
            when (result) {
                ClaimResult.Ok -> onDone()
                ClaimResult.NotFound -> joinError.value = R.string.join_not_found
                ClaimResult.AlreadyUsed -> joinError.value = R.string.join_used
                ClaimResult.NoInternet -> joinError.value = R.string.signin_no_internet
                is ClaimResult.Failed -> joinError.value = R.string.signin_failed
            }
        }
    }

    fun leave(key: String) {
        val uid = auth.user.value?.uid ?: return
        viewModelScope.launch { runCatching { cloud.leave(key, uid) } }
    }
}

@Composable
fun StudentApp(onSignOut: () -> Unit, vm: StudentViewModel = hiltViewModel()) {
    val records by vm.records.collectAsStateWithLifecycle()
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val list = records
    val open = list?.firstOrNull { it.key == openKey }
    when {
        list == null -> LoadingBox()
        adding || list.isEmpty() -> JoinScreen(
            vm = vm,
            canGoBack = list.isNotEmpty(),
            onBack = { adding = false },
            onDone = { adding = false },
            onSignOut = onSignOut,
        )
        open != null -> StudentRecordScreen(open, vm.today(), onBack = { openKey = null }, onLeave = { vm.leave(open.key); openKey = null })
        list.size == 1 && openKey == null && !adding -> StudentRecordScreen(
            list.first(), vm.today(), onBack = null, onLeave = { vm.leave(list.first().key) },
            menu = { StudentMenu(vm.email, onAdd = { adding = true }, onSignOut = onSignOut) },
        )
        else -> RecordListScreen(list, vm.today(), vm.email, onOpen = { openKey = it }, onAdd = { adding = true }, onSignOut = onSignOut)
    }
}

@Composable
private fun StudentMenu(email: String?, onAdd: () -> Unit, onSignOut: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (email != null) DropdownMenuItem(text = { Text(email) }, onClick = {}, enabled = false)
        DropdownMenuItem(text = { Text(stringResource(R.string.join_another)) }, onClick = { open = false; onAdd() }, modifier = Modifier.heightIn(min = 48.dp))
        DropdownMenuItem(text = { Text(stringResource(R.string.sign_out)) }, onClick = { open = false; onSignOut() }, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
private fun JoinScreen(vm: StudentViewModel, canGoBack: Boolean, onBack: () -> Unit, onDone: () -> Unit, onSignOut: () -> Unit) {
    val busy by vm.joining.collectAsStateWithLifecycle()
    val error by vm.joinError.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    AppScaffold(
        title = stringResource(R.string.join_title),
        onBack = if (canGoBack) onBack else null,
        actions = { if (!canGoBack) TextButton(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.join_body), style = MaterialTheme.typography.bodyLarge)
            vm.email?.let { Text(stringResource(R.string.signed_in_as, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(
                value = code,
                onValueChange = { v -> code = v.uppercase().filter { it.isLetterOrDigit() }.take(InviteCodes.LENGTH); vm.joinError.value = null },
                label = { Text(stringResource(R.string.join_code_label)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.join(code, onDone) },
                enabled = !busy && code.length == InviteCodes.LENGTH,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(if (busy) R.string.join_busy else R.string.join_button)) }
        }
    }
}

@Composable
private fun RecordListScreen(
    list: List<StudentSummary>, today: LocalDate, email: String?,
    onOpen: (String) -> Unit, onAdd: () -> Unit, onSignOut: () -> Unit,
) {
    AppScaffold(title = stringResource(R.string.student_home_title), actions = { StudentMenu(email, onAdd, onSignOut) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.key }) { s ->
                Card(
                    onClick = { onOpen(s.key) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.studentName, style = MaterialTheme.typography.titleMedium)
                            Text(s.tutorName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusChip(s.status(today))
                            if (s.amountDue > 0) Text(Money.format(s.amountDue), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentRecordScreen(
    s: StudentSummary,
    today: LocalDate,
    onBack: (() -> Unit)?,
    onLeave: () -> Unit,
    menu: @Composable () -> Unit = {},
) {
    val locale = currentLocale()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var copied by remember { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { snackbar.showSnackbar(context.getString(R.string.upi_copied)); copied = false } }

    AppScaffold(title = s.studentName, onBack = onBack, snackbar = snackbar, actions = menu) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.tutor_label, s.tutorName), style = MaterialTheme.typography.bodyLarge)
            if (s.archived) SmallChip(stringResource(R.string.status_archived))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        when {
                            s.amountDue > 0 -> {
                                Text(stringResource(R.string.balance_due), style = MaterialTheme.typography.labelLarge)
                                Text(Money.format(s.amountDue), style = AmountStyle)
                            }
                            s.credit > 0 -> {
                                Text(stringResource(R.string.advance_credit), style = MaterialTheme.typography.labelLarge)
                                Text(Money.format(s.credit), style = AmountStyle)
                            }
                            else -> Text(stringResource(R.string.all_clear), style = AmountStyle)
                        }
                    }
                    StatusChip(s.status(today))
                }
                Text(stringResource(R.string.fee_label, Money.format(s.monthlyFee), s.dueDay), style = MaterialTheme.typography.bodyMedium)
                s.upiId?.let { upi ->
                    Text(stringResource(R.string.pay_upi_to, upi), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(
                        onClick = { copyToClipboard(context, upi); copied = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.copy_upi)) }
                }
                Text(stringResource(R.string.updated_on, formatEpochDay(s.updatedOn, locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (s.unpaid.isNotEmpty()) {
                SectionTitle(stringResource(R.string.unpaid_months))
                s.unpaid.forEach { m ->
                    SectionCard {
                        Row {
                            Text(formatMonthKey(m.month, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.remaining_label, Money.format(m.remaining)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(stringResource(R.string.due_on, formatEpochDay(m.dueDate, locale)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            SectionTitle(stringResource(R.string.section_payments))
            if (s.payments.isEmpty()) Text(stringResource(R.string.no_payments))
            s.payments.forEach { p ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatEpochDay(p.date, locale), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when (p.method) { "CASH" -> R.string.method_cash; "UPI" -> R.string.method_upi; else -> R.string.method_other }), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(Money.format(p.amount), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            SectionTitle(stringResource(R.string.section_attendance))
            if (s.attendance.isEmpty()) Text(stringResource(R.string.attendance_none_yet))
            s.attendance.forEach { a ->
                SectionCard {
                    Row {
                        Text(formatMonthKey(a.month, locale), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.attendance_summary, a.present, a.total), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text(stringResource(R.string.student_view_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { confirmLeave = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.leave_record), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.leave_record),
            body = stringResource(R.string.leave_record_body, s.studentName),
            confirmLabel = stringResource(R.string.leave_record),
            destructive = true,
            onConfirm = { confirmLeave = false; onLeave() },
            onDismiss = { confirmLeave = false },
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("UPI", text))
}

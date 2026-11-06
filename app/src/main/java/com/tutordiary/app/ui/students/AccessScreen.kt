@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.students

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.sync.InviteResult
import com.tutordiary.app.data.sync.TutorSync
import com.tutordiary.app.domain.Reminder
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.ConfirmDialog
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.SectionCard
import com.tutordiary.app.ui.common.WarningCard
import com.tutordiary.app.ui.common.copyText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccessViewModel @Inject constructor(
    private val repo: TutorRepository,
    private val sync: TutorSync,
    auth: AuthService,
    savedState: SavedStateHandle,
) : ViewModel() {
    val studentId: Long = savedState.get<Long>("studentId") ?: 0L
    val signedIn: Boolean = auth.user.value != null

    val student = repo.observeStudent(studentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val linkedCount: StateFlow<Int> = repo.observeStudent(studentId)
        .flatMapLatest { s -> if (s?.inviteCode == null) flowOf(0) else sync.linkedCount(studentId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<Int?>(null)

    fun makeCode() {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            error.value = when (sync.invite(studentId)) {
                is InviteResult.Ok -> null
                InviteResult.NotSignedIn -> R.string.access_need_signin
                InviteResult.NoInternet -> R.string.access_no_internet
            }
            busy.value = false
        }
    }

    fun removeAccess() {
        viewModelScope.launch { sync.removeAccess(studentId) }
    }
}

@Composable
fun AccessScreen(onBack: () -> Unit, vm: AccessViewModel = hiltViewModel()) {
    val student by vm.student.collectAsStateWithLifecycle()
    val linked by vm.linkedCount.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var copied by remember { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var waFailed by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { snackbar.showSnackbar(context.getString(R.string.reminder_copied)); copied = false } }

    AppScaffold(title = stringResource(R.string.access_title), onBack = onBack, snackbar = snackbar) { padding ->
        val s = student
        if (s == null) LoadingBox() else Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.access_explain), style = MaterialTheme.typography.bodyLarge)
            if (!vm.signedIn) WarningCard(stringResource(R.string.access_need_signin))

            val code = s.inviteCode
            if (code == null) {
                Button(
                    onClick = vm::makeCode,
                    enabled = vm.signedIn && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(if (busy) R.string.access_making else R.string.access_make_code)) }
            } else {
                val codeLabel = stringResource(R.string.access_code_label)
                SectionCard {
                    Text(codeLabel, style = MaterialTheme.typography.labelLarge)
                    Text(
                        code.chunked(3).joinToString(" "),
                        fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.semantics { contentDescription = "$codeLabel: ${code.toList().joinToString(" ")}" },
                    )
                    Text(
                        if (linked == 0) stringResource(R.string.access_nobody_yet) else stringResource(R.string.access_joined, linked),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val message = stringResource(R.string.access_whatsapp_message, s.parentName, s.name, code)
                Button(
                    onClick = {
                        waFailed = !openWhatsApp(context, s.phone, message)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.access_send_whatsapp)) }
                OutlinedButton(
                    onClick = { copyText(context, message); copied = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.copy)) }
                if (waFailed) Text(stringResource(R.string.whatsapp_failed), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.access_code_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = vm::makeCode,
                    enabled = vm.signedIn && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(if (busy) R.string.access_making else R.string.access_new_code)) }
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.access_remove), color = MaterialTheme.colorScheme.error)
                }
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.access_what_they_see), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.access_remove),
            body = stringResource(R.string.access_remove_body),
            confirmLabel = stringResource(R.string.access_remove),
            destructive = true,
            onConfirm = { confirmRemove = false; vm.removeAccess() },
            onDismiss = { confirmRemove = false },
        )
    }
}

private fun openWhatsApp(context: android.content.Context, phone: String, message: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Reminder.whatsAppLink(phone, message))))
    true
} catch (e: ActivityNotFoundException) {
    false
}

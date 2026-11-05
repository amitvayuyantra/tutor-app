@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutordiary.app.BuildConfig
import com.tutordiary.app.R
import com.tutordiary.app.domain.FeeStatus
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.ConfirmDialog
import com.tutordiary.app.ui.common.SectionCard
import com.tutordiary.app.ui.common.SectionTitle
import com.tutordiary.app.ui.common.Selector
import com.tutordiary.app.ui.common.WarningCard
import com.tutordiary.app.ui.common.currentLocale
import com.tutordiary.app.ui.common.formatDate
import com.tutordiary.app.ui.common.formatEpochDay
import com.tutordiary.app.ui.setup.ProfileFields

@Composable
fun SettingsScreen(
    onOpenTemplates: () -> Unit,
    onOpenBatches: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = currentLocale()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(context.getString(it)); vm.message.value = null }
    }

    // Profile fields start from the saved profile once it loads.
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var business by rememberSaveable { mutableStateOf("") }
    var upi by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(profile) {
        val p = profile
        if (p != null && name == null) { name = p.tutorName; business = p.businessName.orEmpty(); upi = p.upiId.orEmpty() }
    }

    val labels = csvLabels()
    val today = vm.today().toString()
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::createBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::readBackup)
    }
    val studentsCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportStudents(it, labels) }
    }
    val balancesCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportBalances(it, labels) }
    }
    val defaultEn = stringResource(R.string.default_template_en)
    val defaultHi = stringResource(R.string.default_template_hi)

    AppScaffold(title = stringResource(R.string.tab_settings), snackbar = snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Backup first: it matters most ----
            SectionTitle(stringResource(R.string.section_backup))
            WarningCard(stringResource(R.string.backup_warning))
            Text(
                lastBackup?.let { stringResource(R.string.last_backup, formatEpochDay(it, locale)) }
                    ?: stringResource(R.string.last_backup_never),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = { backupLauncher.launch("tutor-diary-backup-$today.json") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.create_backup))
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.restore_backup)) }
            OutlinedButton(onClick = { studentsCsvLauncher.launch("tutor-diary-students-$today.csv") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.export_students))
            }
            OutlinedButton(onClick = { balancesCsvLauncher.launch("tutor-diary-fee-balances-$today.csv") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.export_balances))
            }

            // ---- Profile ----
            SectionTitle(stringResource(R.string.section_profile))
            SectionCard {
                ProfileFields(
                    name = name.orEmpty(), onName = { name = it },
                    business = business, onBusiness = { business = it },
                    upi = upi, onUpi = { upi = it },
                    nameError = null,
                )
                Button(
                    onClick = { vm.saveProfile(name.orEmpty(), business, upi, defaultEn, defaultHi) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.save_profile)) }
            }

            // ---- Reminders, batches ----
            SectionTitle(stringResource(R.string.section_templates))
            OutlinedButton(onClick = onOpenTemplates, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.edit_templates))
            }
            OutlinedButton(onClick = onOpenBatches, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.manage_batches))
            }

            // ---- Language ----
            SectionTitle(stringResource(R.string.section_language))
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val selected = when {
                currentTag.startsWith("hi") -> "hi"
                currentTag.startsWith("en") -> "en"
                else -> ""
            }
            Selector(
                label = stringResource(R.string.section_language),
                options = listOf("" to stringResource(R.string.lang_system), "en" to stringResource(R.string.lang_english), "hi" to stringResource(R.string.lang_hindi)),
                selected = selected,
                onSelect = { tag ->
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                    )
                },
            )

            // ---- About ----
            SectionTitle(stringResource(R.string.section_about))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
        }
    }

    pending?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.restore_confirm_title),
            body = stringResource(R.string.restore_confirm_body, p.students, p.payments, formatDate(p.createdOn, locale)),
            confirmLabel = stringResource(R.string.restore_confirm),
            destructive = true,
            onConfirm = vm::confirmRestore,
            onDismiss = { vm.pendingRestore.value = null },
        )
    }
}

@Composable
private fun csvLabels(): CsvLabels = CsvLabels(
    studentsHeader = listOf(
        stringResource(R.string.csv_name), stringResource(R.string.csv_parent), stringResource(R.string.csv_phone),
        stringResource(R.string.csv_batch), stringResource(R.string.csv_fee), stringResource(R.string.csv_due_day),
        stringResource(R.string.csv_join), stringResource(R.string.csv_status), stringResource(R.string.csv_notes),
    ),
    balancesHeader = listOf(
        stringResource(R.string.csv_name), stringResource(R.string.csv_parent), stringResource(R.string.csv_phone),
        stringResource(R.string.csv_charged), stringResource(R.string.csv_waived), stringResource(R.string.csv_paid),
        stringResource(R.string.csv_balance), stringResource(R.string.csv_credit), stringResource(R.string.csv_months_due),
        stringResource(R.string.csv_status),
    ),
    active = stringResource(R.string.csv_active),
    archived = stringResource(R.string.status_archived),
    status = mapOf(
        FeeStatus.PAID to stringResource(R.string.status_paid),
        FeeStatus.PARTIAL to stringResource(R.string.status_partial),
        FeeStatus.DUE to stringResource(R.string.status_due),
        FeeStatus.OVERDUE to stringResource(R.string.status_overdue),
    ),
)

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutordiary.app.R
import com.tutordiary.app.domain.Money
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.EmptyState
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.ReminderDialog
import com.tutordiary.app.ui.common.SectionTitle
import com.tutordiary.app.ui.common.StatusChip
import com.tutordiary.app.ui.common.WarningCard
import com.tutordiary.app.ui.common.currentLocale
import com.tutordiary.app.ui.common.formatDate
import com.tutordiary.app.ui.theme.AmountStyle

@Composable
fun HomeScreen(
    onOpenStudent: (Long) -> Unit,
    onRecordPayment: (Long) -> Unit,
    onAddStudent: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var reminderFor by remember { mutableStateOf<UnpaidRow?>(null) }
    val locale = currentLocale()

    AppScaffold(title = stringResource(R.string.app_name)) { padding ->
        if (state.loading) LoadingBox() else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.showBackupWarning) {
                item { WarningCard(stringResource(R.string.home_backup_banner), stringResource(R.string.home_backup_banner_action), onOpenSettings) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile(stringResource(R.string.home_expected), Money.format(state.expectedThisMonth), Modifier.weight(1f))
                    SummaryTile(stringResource(R.string.home_collected), Money.format(state.collectedThisMonth), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile(stringResource(R.string.home_unpaid), Money.format(state.totalUnpaid), Modifier.weight(1f))
                    SummaryTile(stringResource(R.string.home_overdue), state.overdueCount.toString(), Modifier.weight(1f))
                }
            }
            if (!state.hasStudents) {
                item {
                    EmptyState(
                        stringResource(R.string.home_no_students),
                        stringResource(R.string.home_no_students_body),
                        stringResource(R.string.add_student),
                        onAddStudent,
                    )
                }
            } else {
                item { SectionTitle(stringResource(R.string.home_unpaid_list)) }
                if (state.unpaid.isEmpty()) {
                    item { Text(stringResource(R.string.home_all_paid), style = MaterialTheme.typography.bodyLarge) }
                }
                items(state.unpaid, key = { it.student.id }) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenStudent(row.student.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.student.name, style = MaterialTheme.typography.titleMedium)
                                    Text(row.student.parentName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusChip(row.ledger.status)
                            }
                            Text(Money.format(row.ledger.amountDue), style = AmountStyle)
                            Text(
                                stringResource(R.string.label_months_due, row.ledger.unpaidMonths.size) + " · " +
                                    stringResource(R.string.due_since, formatDate(row.ledger.oldestDueDate!!, locale)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRecordPayment(row.student.id) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.record_payment)) }
                                OutlinedButton(
                                    onClick = { reminderFor = row },
                                    enabled = state.profile != null,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.send_reminder)) }
                            }
                        }
                    }
                }
            }
        }
    }

    val r = reminderFor
    val profile = state.profile
    if (r != null && profile != null) {
        ReminderDialog(
            student = r.student,
            ledger = r.ledger,
            profile = profile,
            onOpened = { vm.markReminderOpened(r.student.id) },
            onDismiss = { reminderFor = null },
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = AmountStyle, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

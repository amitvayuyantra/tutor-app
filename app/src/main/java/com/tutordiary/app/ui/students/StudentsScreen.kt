@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.data.repo.ledgers
import com.tutordiary.app.domain.LedgerSummary
import com.tutordiary.app.domain.Money
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.EmptyState
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.SmallChip
import com.tutordiary.app.ui.common.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StudentRow(val student: StudentEntity, val ledger: LedgerSummary?, val batchName: String?)

data class StudentsState(
    val loading: Boolean = true,
    val totalStudents: Int = 0,
    val rows: List<StudentRow> = emptyList(),
)

@HiltViewModel
class StudentsViewModel @Inject constructor(repo: TutorRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val showArchived = MutableStateFlow(false)

    val state: StateFlow<StudentsState> = combine(
        repo.observeFeeData(), repo.observeBatches(), query, showArchived,
    ) { data, batches, q, archived ->
        val ledgers = data.ledgers(repo.today())
        val batchNames = batches.associate { it.id to it.name }
        val needle = q.trim().lowercase()
        val digits = needle.filter(Char::isDigit)
        val rows = data.students
            .filter { (it.archivedOn != null) == archived }
            .filter {
                needle.isEmpty() || it.name.lowercase().contains(needle) ||
                    it.parentName.lowercase().contains(needle) || (digits.length >= 3 && it.phone.contains(digits))
            }
            .map { StudentRow(it, ledgers[it.id], it.batchId?.let(batchNames::get)) }
        StudentsState(loading = false, totalStudents = data.students.size, rows = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudentsState())
}

@Composable
fun StudentsScreen(
    onOpenStudent: (Long) -> Unit,
    onAddStudent: () -> Unit,
    onOpenBatches: () -> Unit,
    vm: StudentsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val showArchived by vm.showArchived.collectAsStateWithLifecycle()

    AppScaffold(
        title = stringResource(R.string.tab_students),
        actions = { TextButton(onClick = onOpenBatches) { Text(stringResource(R.string.batches)) } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddStudent,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_student)) },
            )
        },
    ) { padding ->
        if (state.loading) LoadingBox() else Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_students)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                },
                singleLine = true,
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = showArchived,
                    onClick = { vm.showArchived.value = !showArchived },
                    label = { Text(stringResource(R.string.show_archived)) },
                )
            }
            when {
                state.totalStudents == 0 -> EmptyState(
                    stringResource(R.string.students_empty_title),
                    stringResource(R.string.students_empty_body),
                )
                state.rows.isEmpty() -> EmptyState(stringResource(R.string.no_students_found), "")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows, key = { it.student.id }) { row -> StudentCard(row) { onOpenStudent(row.student.id) } }
                }
            }
        }
    }
}

@Composable
private fun StudentCard(row: StudentRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.student.name, style = MaterialTheme.typography.titleMedium)
                Text(row.student.parentName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(row.batchName ?: stringResource(R.string.no_batch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val ledger = row.ledger
                if (row.student.archivedOn != null) SmallChip(stringResource(R.string.status_archived))
                if (ledger != null) {
                    StatusChip(ledger.status)
                    when {
                        ledger.amountDue > 0 -> Text(Money.format(ledger.amountDue), style = MaterialTheme.typography.titleMedium)
                        ledger.credit > 0 -> Text(stringResource(R.string.credit_label, Money.format(ledger.credit)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

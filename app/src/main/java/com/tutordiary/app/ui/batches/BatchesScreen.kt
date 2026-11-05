@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.batches

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.EmptyState
import com.tutordiary.app.ui.common.FieldKeyboard
import com.tutordiary.app.ui.common.FormField
import com.tutordiary.app.ui.common.SectionTitle
import com.tutordiary.app.ui.common.SmallChip
import com.tutordiary.app.ui.common.ruleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatchRow(val batch: BatchEntity, val activeStudents: Int)

@HiltViewModel
class BatchesViewModel @Inject constructor(private val repo: TutorRepository) : ViewModel() {
    val rows: StateFlow<List<BatchRow>?> = combine(repo.observeBatches(), repo.observeStudents()) { batches, students ->
        batches.map { b -> BatchRow(b, students.count { it.batchId == b.id && it.archivedOn == null }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message = MutableStateFlow<String?>(null)

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repo.setBatchArchived(id, archived) }
    }
}

@Composable
fun BatchesScreen(onBack: () -> Unit, onEditBatch: (Long) -> Unit, vm: BatchesViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var archiveRow by remember { mutableStateOf<BatchRow?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var blockedCount by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(blockedCount) {
        blockedCount?.let { snackbar.showSnackbar(context.getString(R.string.batch_has_students, it)); blockedCount = null }
    }

    AppScaffold(
        title = stringResource(R.string.batches_title),
        onBack = onBack,
        snackbar = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditBatch(0) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.batch_new)) },
            )
        },
    ) { padding ->
        val list = rows
        if (list != null && list.isEmpty()) {
            Column(Modifier.padding(padding)) { EmptyState(stringResource(R.string.batches_title), stringResource(R.string.batches_empty)) }
        } else if (list != null) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val active = list.filter { !it.batch.archived }
                val archived = list.filter { it.batch.archived }
                items(active, key = { it.batch.id }) { row ->
                    BatchCard(row, onEdit = { onEditBatch(row.batch.id) }, actionLabel = stringResource(R.string.archive)) {
                        if (row.activeStudents > 0) blockedCount = row.activeStudents else archiveRow = row
                    }
                }
                if (archived.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.archived_section)) }
                    items(archived, key = { it.batch.id }) { row ->
                        BatchCard(row, onEdit = { onEditBatch(row.batch.id) }, actionLabel = stringResource(R.string.unarchive)) {
                            vm.setArchived(row.batch.id, false)
                        }
                    }
                }
            }
        }
    }

    archiveRow?.let { row ->
        com.tutordiary.app.ui.common.ConfirmDialog(
            title = row.batch.name,
            body = stringResource(R.string.batch_archive_confirm),
            confirmLabel = stringResource(R.string.archive),
            onConfirm = { vm.setArchived(row.batch.id, true); archiveRow = null },
            onDismiss = { archiveRow = null },
        )
    }
}

@Composable
private fun BatchCard(row: BatchRow, onEdit: () -> Unit, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.batch.name, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(row.batch.subject, row.batch.classDays, row.batch.classTime).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
                SmallChip(stringResource(R.string.batch_students_count, row.activeStudents))
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

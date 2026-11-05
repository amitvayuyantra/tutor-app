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

    fun save(batch: BatchEntity, onDone: () -> Unit, onError: (Int) -> Unit) {
        viewModelScope.launch {
            try { repo.saveBatch(batch); onDone() } catch (e: RuleException) { onError(ruleMessage(e.rule)) }
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repo.setBatchArchived(id, archived) }
    }
}

@Composable
fun BatchesScreen(onBack: () -> Unit, vm: BatchesViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BatchEntity?>(null) }
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
                onClick = { editing = BatchEntity(name = "", subject = null, classDays = null, classTime = null) },
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
                    BatchCard(row, onEdit = { editing = row.batch }, actionLabel = stringResource(R.string.archive)) {
                        if (row.activeStudents > 0) blockedCount = row.activeStudents else archiveRow = row
                    }
                }
                if (archived.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.archived_section)) }
                    items(archived, key = { it.batch.id }) { row ->
                        BatchCard(row, onEdit = { editing = row.batch }, actionLabel = stringResource(R.string.unarchive)) {
                            vm.setArchived(row.batch.id, false)
                        }
                    }
                }
            }
        }
    }

    editing?.let { b -> BatchDialog(b, onSave = { batch, onError -> vm.save(batch, { editing = null }, onError) }, onDismiss = { editing = null }) }
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

@Composable
private fun BatchDialog(batch: BatchEntity, onSave: (BatchEntity, (Int) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(batch.name) }
    var subject by rememberSaveable { mutableStateOf(batch.subject.orEmpty()) }
    var days by rememberSaveable { mutableStateOf(batch.classDays.orEmpty()) }
    var time by rememberSaveable { mutableStateOf(batch.classTime.orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (batch.id == 0L) R.string.batch_new else R.string.batch_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField(name, { name = it; error = null }, stringResource(R.string.field_batch_name), error = error?.let { stringResource(it) })
                FormField(subject, { subject = it }, stringResource(R.string.field_subject))
                FormField(days, { days = it }, stringResource(R.string.field_class_days), help = stringResource(R.string.field_class_days_hint))
                FormField(time, { time = it }, stringResource(R.string.field_class_time), help = stringResource(R.string.field_class_time_hint), keyboard = FieldKeyboard.Text)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = R.string.err_name_required
                else onSave(batch.copy(name = name, subject = subject, classDays = days, classTime = time)) { error = it }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.attendance

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.db.StudentEntity
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.DateField
import com.tutordiary.app.ui.common.EmptyState
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.Selector
import com.tutordiary.app.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SheetRow(val student: StudentEntity, val present: Boolean)

data class SheetState(
    val batchId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = false,
    val rows: List<SheetRow> = emptyList(),
    val alreadySaved: Boolean = false,
    val savedMessage: Boolean = false,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(private val repo: TutorRepository) : ViewModel() {

    val batches: StateFlow<List<BatchEntity>?> = repo.observeBatches()
        .map { list -> list.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sheet = MutableStateFlow(SheetState(date = repo.today()))
    val sheet: StateFlow<SheetState> = _sheet.asStateFlow()
    private var loadJob: Job? = null

    fun ensureBatch(list: List<BatchEntity>) {
        val current = _sheet.value.batchId
        if (list.isNotEmpty() && (current == null || list.none { it.id == current })) selectBatch(list.first().id)
    }

    fun selectBatch(id: Long) { _sheet.update { it.copy(batchId = id) }; load() }
    fun selectDate(date: LocalDate) { _sheet.update { it.copy(date = date) }; load() }

    private fun load() {
        val s = _sheet.value
        val batchId = s.batchId ?: return
        loadJob?.cancel()
        _sheet.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val rows = repo.attendanceSheet(batchId, s.date)
            _sheet.update {
                it.copy(loading = false, rows = rows.map { r -> SheetRow(r.student, r.present) }, alreadySaved = rows.any { r -> r.saved })
            }
        }
    }

    fun toggle(studentId: Long, present: Boolean) = _sheet.update { s ->
        s.copy(rows = s.rows.map { if (it.student.id == studentId) it.copy(present = present) else it })
    }

    fun markAllPresent() = _sheet.update { s -> s.copy(rows = s.rows.map { it.copy(present = true) }) }

    fun save() {
        val s = _sheet.value
        val batchId = s.batchId ?: return
        viewModelScope.launch {
            repo.saveAttendance(batchId, s.date, s.rows.associate { it.student.id to it.present })
            _sheet.update { it.copy(alreadySaved = true, savedMessage = true) }
        }
    }

    fun messageShown() = _sheet.update { it.copy(savedMessage = false) }
}

@Composable
fun AttendanceScreen(onOpenBatches: () -> Unit, vm: AttendanceViewModel = hiltViewModel()) {
    val batches by vm.batches.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(batches) { batches?.let(vm::ensureBatch) }
    LaunchedEffect(sheet.savedMessage) {
        if (sheet.savedMessage) { snackbar.showSnackbar(context.getString(R.string.attendance_saved)); vm.messageShown() }
    }

    AppScaffold(title = stringResource(R.string.tab_attendance), snackbar = snackbar) { padding ->
        val list = batches
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> Column(Modifier.padding(padding)) {
                EmptyState(
                    stringResource(R.string.attendance_no_batches),
                    stringResource(R.string.attendance_no_batches_body),
                    stringResource(R.string.create_batch),
                    onOpenBatches,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Selector(
                        label = stringResource(R.string.attendance_batch),
                        options = list.map { it.id to it.name },
                        selected = sheet.batchId ?: -1L,
                        onSelect = vm::selectBatch,
                    )
                }
                item { DateField(stringResource(R.string.attendance_date), sheet.date, vm::selectDate) }
                item {
                    Text(stringResource(R.string.attendance_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sheet.loading) {
                    item { LoadingBox() }
                } else if (sheet.rows.isEmpty()) {
                    item { EmptyState(stringResource(R.string.attendance_no_students), "") }
                } else {
                    item {
                        if (sheet.alreadySaved) Text(stringResource(R.string.attendance_editing_saved), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val present = sheet.rows.count { it.present }
                            Text(
                                stringResource(R.string.attendance_count, present, sheet.rows.size - present),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = vm::markAllPresent) { Text(stringResource(R.string.mark_all_present)) }
                        }
                    }
                    items(sheet.rows, key = { it.student.id }) { row ->
                        val colors = LocalStatusColors.current
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = row.present,
                                role = Role.Switch,
                                onValueChange = { vm.toggle(row.student.id, it) },
                            ),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.student.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(if (row.present) R.string.present else R.string.absent),
                                        color = if (row.present) colors.paid else colors.overdue,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Switch(checked = row.present, onCheckedChange = null)
                            }
                        }
                    }
                    item {
                        Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp)) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

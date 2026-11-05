@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.students

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.db.BatchEntity
import com.tutordiary.app.data.repo.Rule
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.StudentInput
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.domain.Money
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.DateField
import com.tutordiary.app.ui.common.FieldKeyboard
import com.tutordiary.app.ui.common.FormField
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.Selector
import com.tutordiary.app.ui.common.ruleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class StudentForm(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val parentName: String = "",
    val phone: String = "",
    val batchId: Long? = null,
    val fee: String = "",
    val dueDay: String = "5",
    val joinDate: LocalDate = LocalDate.now(),
    val notes: String = "",
    val errors: Map<String, Int> = emptyMap(),
    val generalError: Int? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class StudentEditViewModel @Inject constructor(
    private val repo: TutorRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("studentId") ?: 0L
    private val _form = MutableStateFlow(StudentForm(joinDate = repo.today()))
    val form: StateFlow<StudentForm> = _form.asStateFlow()

    /** Active batches, plus the student's current batch even if it is archived. */
    val batches: StateFlow<List<BatchEntity>> = repo.observeBatches()
        .map { list -> list.filter { !it.archived || it.id == _form.value.batchId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (id == 0L) {
                _form.update { it.copy(loaded = true) }
            } else {
                val s = repo.getStudent(id)
                if (s == null) _form.update { it.copy(loaded = true, generalError = ruleMessage(Rule.NOT_FOUND)) }
                else _form.value = StudentForm(
                    loaded = true, isNew = false, name = s.name, parentName = s.parentName, phone = s.phone,
                    batchId = s.batchId, fee = s.monthlyFee.toString(), dueDay = s.dueDay.toString(),
                    joinDate = LocalDate.ofEpochDay(s.joinDate), notes = s.notes.orEmpty(),
                )
            }
        }
    }

    fun edit(change: (StudentForm) -> StudentForm) = _form.update { change(it).copy(errors = emptyMap(), generalError = null) }

    fun save() {
        val f = _form.value
        val errors = mutableMapOf<String, Int>()
        if (f.name.isBlank()) errors["name"] = R.string.err_name_required
        if (f.parentName.isBlank()) errors["parent"] = R.string.err_parent_required
        if (com.tutordiary.app.domain.PhoneNumbers.normalize(f.phone) == null) errors["phone"] = R.string.err_phone_invalid
        val fee = Money.parse(f.fee)
        if (fee == null) errors["fee"] = R.string.err_fee_invalid
        val dueDay = f.dueDay.trim().toIntOrNull()
        if (dueDay == null || dueDay !in 1..31) errors["dueDay"] = R.string.err_due_day_invalid
        if (errors.isNotEmpty()) { _form.update { it.copy(errors = errors) }; return }

        val input = StudentInput(
            id = id, name = f.name, parentName = f.parentName, phone = f.phone, batchId = f.batchId,
            monthlyFee = fee!!, dueDay = dueDay!!, joinDate = f.joinDate, notes = f.notes,
        )
        viewModelScope.launch {
            try {
                if (id == 0L) repo.addStudent(input) else repo.updateStudent(input)
                _form.update { it.copy(saved = true) }
            } catch (e: RuleException) {
                _form.update { it.copy(generalError = ruleMessage(e.rule)) }
            }
        }
    }
}

@Composable
fun StudentEditScreen(onDone: () -> Unit, onBack: () -> Unit, vm: StudentEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val batches by vm.batches.collectAsStateWithLifecycle()
    LaunchedEffect(f.saved) { if (f.saved) onDone() }

    AppScaffold(
        title = stringResource(if (f.isNew) R.string.student_new else R.string.student_edit),
        onBack = onBack,
    ) { padding ->
        if (!f.loaded) LoadingBox() else Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormField(f.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(R.string.field_student_name),
                error = f.errors["name"]?.let { stringResource(it) })
            FormField(f.parentName, { v -> vm.edit { it.copy(parentName = v) } }, stringResource(R.string.field_parent_name),
                error = f.errors["parent"]?.let { stringResource(it) })
            FormField(f.phone, { v -> vm.edit { it.copy(phone = v) } }, stringResource(R.string.field_whatsapp),
                error = f.errors["phone"]?.let { stringResource(it) }, help = stringResource(R.string.field_whatsapp_help),
                keyboard = FieldKeyboard.Phone)
            val noBatch = stringResource(R.string.no_batch)
            Selector(
                label = stringResource(R.string.field_batch),
                options = listOf<Pair<Long?, String>>(null to noBatch) + batches.map { it.id to it.name },
                selected = f.batchId,
                onSelect = { v -> vm.edit { it.copy(batchId = v) } },
            )
            FormField(f.fee, { v -> vm.edit { it.copy(fee = v) } }, stringResource(R.string.field_fee),
                error = f.errors["fee"]?.let { stringResource(it) },
                help = if (f.isNew) null else stringResource(R.string.fee_change_note),
                keyboard = FieldKeyboard.Number)
            FormField(f.dueDay, { v -> vm.edit { it.copy(dueDay = v.filter(Char::isDigit).take(2)) } }, stringResource(R.string.field_due_day),
                error = f.errors["dueDay"]?.let { stringResource(it) }, help = stringResource(R.string.field_due_day_help),
                keyboard = FieldKeyboard.Number)
            DateField(stringResource(R.string.field_join_date), f.joinDate, { d -> vm.edit { it.copy(joinDate = d) } })
            FormField(f.notes, { v -> vm.edit { it.copy(notes = v) } }, stringResource(R.string.field_notes),
                keyboard = FieldKeyboard.Text, singleLine = false, minLines = 2)
            f.generalError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

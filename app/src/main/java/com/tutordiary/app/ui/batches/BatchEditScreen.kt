@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.batches

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
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.FieldKeyboard
import com.tutordiary.app.ui.common.FormField
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.ruleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BatchForm(
    val loaded: Boolean = false,
    val batch: BatchEntity = BatchEntity(name = "", subject = null, classDays = null, classTime = null),
    val error: Int? = null,
    val done: Boolean = false,
)

@HiltViewModel
class BatchEditViewModel @Inject constructor(
    private val repo: TutorRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val batchId: Long = savedState.get<Long>("batchId") ?: 0L
    private val _form = MutableStateFlow(BatchForm())
    val form: StateFlow<BatchForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            if (batchId == 0L) _form.update { it.copy(loaded = true) }
            else {
                val b = repo.getBatch(batchId)
                _form.update { if (b == null) it.copy(loaded = true, error = ruleMessage(Rule.NOT_FOUND)) else it.copy(loaded = true, batch = b) }
            }
        }
    }

    fun edit(change: (BatchEntity) -> BatchEntity) = _form.update { it.copy(batch = change(it.batch), error = null) }

    fun save() {
        val b = _form.value.batch
        if (b.name.isBlank()) { _form.update { it.copy(error = R.string.err_name_required) }; return }
        viewModelScope.launch {
            try {
                repo.saveBatch(b)
                _form.update { it.copy(done = true) }
            } catch (e: RuleException) {
                _form.update { it.copy(error = ruleMessage(e.rule)) }
            }
        }
    }
}

@Composable
fun BatchEditScreen(onDone: () -> Unit, vm: BatchEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.done) { if (f.done) onDone() }
    val b = f.batch

    AppScaffold(title = stringResource(if (vm.batchId == 0L) R.string.batch_new else R.string.batch_edit), onBack = onDone) { padding ->
        if (!f.loaded) LoadingBox() else Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormField(b.name, { v -> vm.edit { it.copy(name = v) } }, stringResource(R.string.field_batch_name),
                error = f.error?.let { stringResource(it) })
            FormField(b.subject.orEmpty(), { v -> vm.edit { it.copy(subject = v) } }, stringResource(R.string.field_subject))
            FormField(b.classDays.orEmpty(), { v -> vm.edit { it.copy(classDays = v) } }, stringResource(R.string.field_class_days),
                help = stringResource(R.string.field_class_days_hint))
            FormField(b.classTime.orEmpty(), { v -> vm.edit { it.copy(classTime = v) } }, stringResource(R.string.field_class_time),
                help = stringResource(R.string.field_class_time_hint), keyboard = FieldKeyboard.Text)
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.save)) }
        }
    }
}

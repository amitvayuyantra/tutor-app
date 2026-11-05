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
import com.tutordiary.app.data.db.ChargeEntity
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.domain.Money
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.EmptyState
import com.tutordiary.app.ui.common.FieldKeyboard
import com.tutordiary.app.ui.common.FormField
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.Selector
import com.tutordiary.app.ui.common.currentLocale
import com.tutordiary.app.ui.common.formatMonthKey
import com.tutordiary.app.ui.common.ruleMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WaiverForm(
    val loaded: Boolean = false,
    /** Months that have a fee and no waiver yet, newest first. */
    val months: List<ChargeEntity> = emptyList(),
    val month: Int? = null,
    val amount: String = "",
    val note: String = "",
    val error: Int? = null,
    val done: Boolean = false,
)

@HiltViewModel
class WaiverViewModel @Inject constructor(
    private val repo: TutorRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val studentId: Long = savedState.get<Long>("studentId") ?: 0L
    private val _form = MutableStateFlow(WaiverForm())
    val form: StateFlow<WaiverForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val data = repo.observeFeeData().first()
            val waived = data.waivers.filter { it.studentId == studentId }.map { it.month }.toSet()
            val months = data.charges.filter { it.studentId == studentId && it.month !in waived }.sortedByDescending { it.month }
            val first = months.firstOrNull()
            _form.value = WaiverForm(loaded = true, months = months, month = first?.month, amount = first?.amount?.toString().orEmpty())
        }
    }

    fun selectMonth(month: Int) = _form.update { f ->
        f.copy(month = month, amount = f.months.firstOrNull { it.month == month }?.amount?.toString().orEmpty(), error = null)
    }

    fun edit(change: (WaiverForm) -> WaiverForm) = _form.update { change(it).copy(error = null) }

    fun save() {
        val f = _form.value
        val charge = f.months.firstOrNull { it.month == f.month }
        val amount = Money.parse(f.amount)
        val error = when {
            charge == null -> R.string.err_no_charge
            amount == null -> R.string.err_amount_invalid
            amount <= 0 -> R.string.err_amount_positive
            amount > charge.amount -> R.string.err_waiver_too_big
            f.note.isBlank() -> R.string.err_note_required
            else -> null
        }
        if (error != null) { _form.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            try {
                repo.addWaiver(studentId, charge!!.month, amount!!, f.note)
                _form.update { it.copy(done = true) }
            } catch (e: RuleException) {
                _form.update { it.copy(error = ruleMessage(e.rule)) }
            }
        }
    }
}

@Composable
fun WaiverScreen(onDone: () -> Unit, vm: WaiverViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val locale = currentLocale()
    LaunchedEffect(f.done) { if (f.done) onDone() }

    AppScaffold(title = stringResource(R.string.waiver_title), onBack = onDone) { padding ->
        when {
            !f.loaded -> LoadingBox()
            f.months.isEmpty() -> Column(Modifier.padding(padding)) { EmptyState(stringResource(R.string.waiver_no_months), "") }
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Selector(
                    label = stringResource(R.string.field_month),
                    options = f.months.map { it.month to formatMonthKey(it.month, locale) },
                    selected = f.month ?: -1,
                    onSelect = vm::selectMonth,
                )
                FormField(f.amount, { v -> vm.edit { it.copy(amount = v) } }, stringResource(R.string.field_waiver_amount), keyboard = FieldKeyboard.Number)
                FormField(f.note, { v -> vm.edit { it.copy(note = v) } }, stringResource(R.string.field_waiver_note), keyboard = FieldKeyboard.Text)
                f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

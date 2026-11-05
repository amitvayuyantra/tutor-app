@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tutordiary.app.R
import com.tutordiary.app.data.db.PaymentEntity
import com.tutordiary.app.data.db.PaymentMethod
import com.tutordiary.app.data.repo.Rule
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.data.repo.TutorRepository
import com.tutordiary.app.domain.Money
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.ConfirmDialog
import com.tutordiary.app.ui.common.DateField
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
import java.time.LocalDate
import javax.inject.Inject

data class PaymentForm(
    val loaded: Boolean = false,
    val studentName: String = "",
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val method: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    val error: Int? = null,
    val done: Boolean = false,
)

@HiltViewModel
class PaymentEditViewModel @Inject constructor(
    private val repo: TutorRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val studentId: Long = savedState.get<Long>("studentId") ?: 0L
    val paymentId: Long = savedState.get<Long>("paymentId") ?: 0L
    private val _form = MutableStateFlow(PaymentForm(date = repo.today()))
    val form: StateFlow<PaymentForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val student = repo.getStudent(studentId)
            if (student == null) { _form.update { it.copy(loaded = true, error = ruleMessage(Rule.NOT_FOUND)) }; return@launch }
            if (paymentId == 0L) {
                _form.update { it.copy(loaded = true, studentName = student.name) }
            } else {
                val p = repo.getPayment(paymentId)
                if (p == null) _form.update { it.copy(loaded = true, studentName = student.name, error = ruleMessage(Rule.NOT_FOUND)) }
                else _form.value = PaymentForm(
                    loaded = true, studentName = student.name, amount = p.amount.toString(),
                    date = LocalDate.ofEpochDay(p.date), method = PaymentMethod.parse(p.method) ?: PaymentMethod.OTHER,
                    note = p.note.orEmpty(),
                )
            }
        }
    }

    fun edit(change: (PaymentForm) -> PaymentForm) = _form.update { change(it).copy(error = null) }

    fun save() {
        val f = _form.value
        val amount = Money.parse(f.amount)
        if (amount == null) { _form.update { it.copy(error = R.string.err_amount_invalid) }; return }
        if (amount <= 0) { _form.update { it.copy(error = R.string.err_amount_positive) }; return }
        val p = PaymentEntity(
            id = paymentId, studentId = studentId, amount = amount, date = f.date.toEpochDay(),
            method = f.method.name, note = f.note,
        )
        viewModelScope.launch {
            try {
                if (paymentId == 0L) repo.addPayment(p) else repo.updatePayment(p)
                _form.update { it.copy(done = true) }
            } catch (e: RuleException) {
                _form.update { it.copy(error = ruleMessage(e.rule)) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repo.deletePayment(paymentId)
            _form.update { it.copy(done = true) }
        }
    }
}

@Composable
fun PaymentEditScreen(onDone: () -> Unit, vm: PaymentEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(f.done) { if (f.done) onDone() }
    val isNew = vm.paymentId == 0L

    AppScaffold(
        title = stringResource(if (isNew) R.string.payment_new else R.string.payment_edit),
        onBack = onDone,
    ) { padding ->
        if (!f.loaded) LoadingBox() else Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(f.studentName, style = MaterialTheme.typography.titleLarge)
            FormField(f.amount, { v -> vm.edit { it.copy(amount = v) } }, stringResource(R.string.field_amount), keyboard = FieldKeyboard.Number)
            DateField(stringResource(R.string.field_payment_date), f.date, { d -> vm.edit { it.copy(date = d) } })
            Text(stringResource(R.string.field_method), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { m ->
                    FilterChip(
                        selected = f.method == m,
                        onClick = { vm.edit { it.copy(method = m) } },
                        label = {
                            Text(stringResource(when (m) {
                                PaymentMethod.CASH -> R.string.method_cash
                                PaymentMethod.UPI -> R.string.method_upi
                                PaymentMethod.OTHER -> R.string.method_other
                            }))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            FormField(f.note, { v -> vm.edit { it.copy(note = v) } }, stringResource(R.string.field_payment_note), keyboard = FieldKeyboard.Text)
            f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.save)) }
            if (!isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delete_payment), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_payment),
            stringResource(R.string.delete_payment_confirm, Money.parse(f.amount)?.let(Money::format) ?: f.amount),
            stringResource(R.string.delete), destructive = true,
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

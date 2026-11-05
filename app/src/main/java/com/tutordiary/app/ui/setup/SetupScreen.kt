@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tutordiary.app.R
import com.tutordiary.app.data.repo.RuleException
import com.tutordiary.app.ui.common.ruleMessage

@Composable
fun SetupScreen(onSave: (name: String, business: String, upi: String, onError: (RuleException) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var business by rememberSaveable { mutableStateOf("") }
    var upi by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyLarge)
            ProfileFields(
                name = name, onName = { name = it; error = null },
                business = business, onBusiness = { business = it },
                upi = upi, onUpi = { upi = it },
                nameError = error,
            )
            Button(
                onClick = {
                    if (name.isBlank()) error = R.string.err_name_required
                    else onSave(name, business, upi) { error = ruleMessage(it.rule) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.setup_start)) }
        }
    }
}

@Composable
fun ProfileFields(
    name: String, onName: (String) -> Unit,
    business: String, onBusiness: (String) -> Unit,
    upi: String, onUpi: (String) -> Unit,
    nameError: Int?,
) {
    com.tutordiary.app.ui.common.FormField(
        value = name, onValueChange = onName, label = stringResource(R.string.field_tutor_name),
        error = nameError?.let { stringResource(it) },
    )
    com.tutordiary.app.ui.common.FormField(
        value = business, onValueChange = onBusiness, label = stringResource(R.string.field_business_name),
    )
    com.tutordiary.app.ui.common.FormField(
        value = upi, onValueChange = onUpi, label = stringResource(R.string.field_upi),
        help = stringResource(R.string.field_upi_help),
        keyboard = com.tutordiary.app.ui.common.FieldKeyboard.Email,
    )
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tutordiary.app.ui.settings

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutordiary.app.R
import com.tutordiary.app.ui.common.AppScaffold
import com.tutordiary.app.ui.common.FieldKeyboard
import com.tutordiary.app.ui.common.FormField
import com.tutordiary.app.ui.common.LoadingBox
import com.tutordiary.app.ui.common.SectionCard

@Composable
fun TemplatesScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val defaultEn = stringResource(R.string.default_template_en)
    val defaultHi = stringResource(R.string.default_template_hi)
    var en by rememberSaveable { mutableStateOf<String?>(null) }
    var hi by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(profile) {
        val p = profile
        if (p != null && en == null) { en = p.templateEn; hi = p.templateHi }
    }

    AppScaffold(title = stringResource(R.string.templates_title), onBack = onBack) { padding ->
        val enText = en
        val hiText = hi
        if (enText == null || hiText == null) LoadingBox() else Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard { Text(stringResource(R.string.template_help), style = MaterialTheme.typography.bodyMedium) }
            FormField(enText, { en = it }, stringResource(R.string.template_en), keyboard = FieldKeyboard.Text, singleLine = false, minLines = 6)
            TextButton(onClick = { en = defaultEn }) { Text(stringResource(R.string.reset_default)) }
            FormField(hiText, { hi = it }, stringResource(R.string.template_hi), keyboard = FieldKeyboard.Text, singleLine = false, minLines = 6)
            TextButton(onClick = { hi = defaultHi }) { Text(stringResource(R.string.reset_default)) }
            Button(
                onClick = {
                    vm.saveTemplates(
                        enText.ifBlank { defaultEn },
                        hiText.ifBlank { defaultHi },
                        onDone = onBack,
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

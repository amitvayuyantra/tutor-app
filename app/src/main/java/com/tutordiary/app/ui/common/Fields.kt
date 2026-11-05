package com.tutordiary.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

enum class FieldKeyboard { Text, Name, Number, Phone, Email }

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    help: String? = null,
    keyboard: FieldKeyboard = FieldKeyboard.Name,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            help != null -> ({ Text(help) })
            else -> null
        },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = when (keyboard) {
                FieldKeyboard.Number -> KeyboardType.Number
                FieldKeyboard.Phone -> KeyboardType.Phone
                FieldKeyboard.Email -> KeyboardType.Email
                else -> KeyboardType.Text
            },
            capitalization = when (keyboard) {
                FieldKeyboard.Name -> KeyboardCapitalization.Words
                FieldKeyboard.Text -> KeyboardCapitalization.Sentences
                else -> KeyboardCapitalization.None
            },
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

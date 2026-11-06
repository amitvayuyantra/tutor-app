package com.tutordiary.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tutordiary.app.R
import com.tutordiary.app.data.sync.Role
import com.tutordiary.app.ui.common.WarningCard

@Composable
fun RoleChoiceScreen(onChoose: (Role) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.role_question), style = MaterialTheme.typography.titleLarge)
            RoleCard(
                title = stringResource(R.string.role_tutor),
                body = stringResource(R.string.role_tutor_body),
                onClick = { onChoose(Role.TUTOR) },
            )
            RoleCard(
                title = stringResource(R.string.role_student),
                body = stringResource(R.string.role_student_body),
                onClick = { onChoose(Role.STUDENT) },
            )
        }
    }
}

@Composable
private fun RoleCard(title: String, body: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun SignInScreen(
    role: Role,
    configured: Boolean,
    busy: Boolean,
    error: Int?,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (role == Role.TUTOR) R.string.signin_title_tutor else R.string.signin_title_student),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (role == Role.TUTOR) R.string.signin_body_tutor else R.string.signin_body_student),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.size(8.dp))
            if (!configured) {
                WarningCard(stringResource(R.string.signin_not_configured))
            }
            Button(
                onClick = onSignIn,
                enabled = configured && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(stringResource(if (busy) R.string.signin_busy else R.string.signin_google)) }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            Text(stringResource(R.string.signin_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!configured && role == Role.TUTOR) {
                OutlinedButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.signin_continue_offline))
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.signin_change_role)) }
        }
    }
}

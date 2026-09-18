package com.offcampus.app.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("OffCampus", style = MaterialTheme.typography.displayLarge)

            val needsProfile = uiState as? AuthUiState.NeedsProfile
            when {
                // A magic link just signed this person in, but there's no Rider doc for them
                // yet — the one bit password sign-up would have collected (their name) still
                // needs asking, one time, before the rest of the app can treat them as signed in.
                needsProfile != null -> NameStep(form = form, viewModel = viewModel)
                form.method == AuthMethod.MAGIC_LINK -> MagicLinkStep(form = form, viewModel = viewModel)
                else -> PasswordStep(form = form, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun NameStep(form: AuthFormState, viewModel: AuthViewModel) {
    Text(
        "Almost there — what's your name?",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
    )
    OutlinedTextField(
        value = form.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    ErrorText(form.errorMessage)
    Button(
        onClick = { viewModel.completeProfile(form.name) },
        enabled = !form.isSubmitting,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
    ) {
        SubmitLabel(form.isSubmitting, "Continue")
    }
}

@Composable
private fun MagicLinkStep(form: AuthFormState, viewModel: AuthViewModel) {
    if (form.magicLinkSent) {
        Text(
            "Check your inbox",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        Text(
            "We sent a sign-in link to ${form.email}. Open it on this device to continue — " +
                "no password needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        ErrorText(form.errorMessage)
        TextButton(
            onClick = { viewModel.onMethodChange(AuthMethod.MAGIC_LINK) }, // clears magicLinkSent
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use a different email")
        }
    } else {
        Text(
            "Sign in with your college email — no password needed.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        OutlinedTextField(
            value = form.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("College email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        ErrorText(form.errorMessage)
        Button(
            onClick = viewModel::sendMagicLink,
            enabled = !form.isSubmitting,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            SubmitLabel(form.isSubmitting, "Send magic link")
        }
    }
    MethodToggle(form = form, viewModel = viewModel)
}

@Composable
private fun PasswordStep(form: AuthFormState, viewModel: AuthViewModel) {
    Text(
        if (form.mode == AuthMode.SIGN_IN) "Sign in with your college email." else "Create your account.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
    )

    if (form.mode == AuthMode.SIGN_UP) {
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
    }

    OutlinedTextField(
        value = form.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("College email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
    OutlinedTextField(
        value = form.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    ErrorText(form.errorMessage)

    Button(
        onClick = viewModel::submit,
        enabled = !form.isSubmitting,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
    ) {
        SubmitLabel(form.isSubmitting, if (form.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
    }
    TextButton(
        onClick = {
            viewModel.onModeChange(if (form.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Text(
            if (form.mode == AuthMode.SIGN_IN) "New here? Create an account"
            else "Already have an account? Sign in"
        )
    }
    MethodToggle(form = form, viewModel = viewModel)
}

/** Switches between password auth and the magic-link flow — sits at the bottom of both, since
 * neither is the "primary" one and a user might arrive already knowing which they'd rather use. */
@Composable
private fun MethodToggle(form: AuthFormState, viewModel: AuthViewModel) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        OutlinedButton(
            onClick = {
                viewModel.onMethodChange(
                    if (form.method == AuthMethod.PASSWORD) AuthMethod.MAGIC_LINK else AuthMethod.PASSWORD
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (form.method == AuthMethod.PASSWORD) "Use email link instead of a password"
                else "Use a password instead"
            )
        }
    }
}

@Composable
private fun ErrorText(message: String?) {
    message?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun SubmitLabel(isSubmitting: Boolean, label: String) {
    if (isSubmitting) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Text(label)
    }
}

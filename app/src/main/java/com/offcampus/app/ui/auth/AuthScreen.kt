package com.offcampus.app.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("OffCampus", style = MaterialTheme.typography.displayLarge)
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

            form.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (form.mode == AuthMode.SIGN_IN) "Sign in" else "Create account")
                }
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
        }
    }
}

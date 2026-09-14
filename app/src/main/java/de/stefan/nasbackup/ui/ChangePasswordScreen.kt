package de.stefan.nasbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.stefan.nasbackup.data.CredentialStore
import de.stefan.nasbackup.net.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_PASSWORD_LEN = 8

@Composable
fun ChangePasswordScreen(creds: CredentialStore.Creds, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newPassRepeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val newPassOk = newPass.length >= MIN_PASSWORD_LEN && newPass == newPassRepeat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Passwort ändern", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = oldPass,
            onValueChange = { oldPass = it },
            label = { Text("Aktuelles Passwort") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = newPass,
            onValueChange = { newPass = it },
            label = { Text("Neues Passwort (mind. $MIN_PASSWORD_LEN Zeichen)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = newPassRepeat,
            onValueChange = { newPassRepeat = it },
            label = { Text("Neues Passwort wiederholen") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !busy && oldPass.isNotBlank() && newPassOk,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        WebDavClient(creds.baseUrl, creds.user, oldPass).changePassword(newPass)
                    }
                    busy = false
                    result.fold(
                        onSuccess = {
                            CredentialStore.save(ctx, creds.copy(password = newPass))
                            onDone()
                        },
                        onFailure = { error = it.message ?: "Fehlgeschlagen" }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Ändere..." else "Passwort ändern")
        }

        if (busy) {
            CircularProgressIndicator()
        }

        OutlinedButton(
            enabled = !busy,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abbrechen") }

        PoweredByScheidl()
    }
}

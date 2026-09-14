package de.stefan.nasbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.stefan.nasbackup.data.CredentialStore
import de.stefan.nasbackup.net.WebDavClient
import de.stefan.nasbackup.work.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("http://192.168.178.47:8080") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Anmelden", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Die Anmeldung erfolgt direkt am eigenen Server. " +
                "Es wird kein Konto bei einem Dritten angelegt.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server-Adresse") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("E-Mail-Adresse") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Passwort") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !busy && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        WebDavClient(url, user, pass).checkLogin()
                    }
                    busy = false
                    result.fold(
                        onSuccess = {
                            CredentialStore.save(
                                ctx,
                                CredentialStore.Creds(url, user, pass)
                            )
                            SyncScheduler.schedulePeriodic(ctx)
                            onLoggedIn()
                        },
                        onFailure = { error = it.message ?: "Verbindung fehlgeschlagen" }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Prüfe..." else "Verbindung testen und anmelden")
        }

        if (busy) {
            CircularProgressIndicator()
        }

        PoweredByScheidl()
    }
}

package de.stefan.nasbackup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.stefan.nasbackup.data.CredentialStore

@Composable
fun RootScreen() {
    val ctx = LocalContext.current
    var creds by remember { mutableStateOf(CredentialStore.load(ctx)) }

    val current = creds
    if (current == null) {
        LoginScreen(onLoggedIn = { creds = CredentialStore.load(ctx) })
    } else {
        StatusScreen(
            creds = current,
            onLoggedOut = {
                CredentialStore.clear(ctx)
                creds = null
            }
        )
    }
}

package de.stefan.nasbackup.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.stefan.nasbackup.data.CredentialStore
import de.stefan.nasbackup.data.FolderPrefs
import de.stefan.nasbackup.data.UploadLog
import de.stefan.nasbackup.work.SyncScheduler
import de.stefan.nasbackup.work.UploadWorker

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

@Composable
fun StatusScreen(
    creds: CredentialStore.Creds,
    onLoggedOut: () -> Unit
) {
    val ctx = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            mediaPermissions().all {
                ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var uploadedCount by remember { mutableIntStateOf(UploadLog.count(ctx)) }
    var selectedFolders by remember { mutableStateOf(FolderPrefs.selected(ctx)) }
    var pickingFolders by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // POST_NOTIFICATIONS darf fehlen, der Medienzugriff nicht.
        hasPermission = granted
            .filterKeys { it != Manifest.permission.POST_NOTIFICATIONS }
            .all { it.value }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(mediaPermissions())
    }

    val manualWork by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData(SyncScheduler.MANUAL_WORK)
        .observeAsState()
    val periodicWork by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData(SyncScheduler.PERIODIC_WORK)
        .observeAsState()

    if (pickingFolders) {
        FolderPickerScreen(onDone = {
            selectedFolders = FolderPrefs.selected(ctx)
            pickingFolders = false
        })
        return
    }

    if (changingPassword) {
        ChangePasswordScreen(creds = creds, onDone = { changingPassword = false })
        return
    }

    val info: WorkInfo? = manualWork?.firstOrNull()
    val periodicInfo: WorkInfo? = periodicWork?.firstOrNull()
    val running = info?.state == WorkInfo.State.RUNNING || periodicInfo?.state == WorkInfo.State.RUNNING

    LaunchedEffect(info?.state) {
        if (info?.state?.isFinished == true) {
            uploadedCount = UploadLog.count(ctx)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("NasBackup", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Server", style = MaterialTheme.typography.labelMedium)
                Text(creds.baseUrl)
                Text(creds.user, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (!hasPermission) {
            Text(
                "Ohne Zugriff auf Fotos und Videos kann nichts gesichert werden.",
                color = MaterialTheme.colorScheme.error
            )
            Button(
                onClick = { permissionLauncher.launch(mediaPermissions()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Zugriff erlauben") }
        }

        Text("Bereits gesichert: $uploadedCount Dateien")

        Text(
            if (selectedFolders.isEmpty()) "Ordner: keine ausgewählt – es wird nichts gesichert"
            else "Ordner: ${selectedFolders.size} ausgewählt (${selectedFolders.joinToString(", ")})",
            color = if (selectedFolders.isEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(
            onClick = { pickingFolders = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ordner auswählen") }

        when (info?.state) {
            WorkInfo.State.RUNNING -> {
                val done = info.progress.getInt(UploadWorker.PROGRESS_DONE, 0)
                val total = info.progress.getInt(UploadWorker.PROGRESS_TOTAL, 0)
                Text(if (total > 0) "Läuft: $done von $total" else "Läuft: wird vorbereitet")
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            WorkInfo.State.SUCCEEDED -> {
                val up = info.outputData.getInt(UploadWorker.RESULT_UPLOADED, 0)
                val failed = info.outputData.getInt(UploadWorker.RESULT_FAILED, 0)
                Text(
                    if (failed > 0) "Letzter Lauf: $up hochgeladen, $failed fehlgeschlagen"
                    else "Letzter Lauf: $up hochgeladen"
                )
            }

            WorkInfo.State.FAILED -> {
                val msg = info.outputData.getString(UploadWorker.RESULT_ERROR)
                Text(
                    "Fehlgeschlagen: ${msg ?: "unbekannter Fehler"}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            WorkInfo.State.ENQUEUED -> Text("Wartet auf WLAN")

            else -> Text("Automatisch alle 6 Stunden im WLAN")
        }

        Button(
            enabled = hasPermission && !running,
            onClick = { SyncScheduler.runNow(ctx) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Jetzt sichern") }

        if (running) {
            OutlinedButton(
                onClick = { SyncScheduler.cancelRunning(ctx) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Abbrechen") }
        }

        OutlinedButton(
            onClick = { changingPassword = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Passwort ändern") }

        OutlinedButton(
            onClick = {
                SyncScheduler.cancelAll(ctx)
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abmelden") }

        PoweredByScheidl()
    }
}

package de.stefan.nasbackup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.stefan.nasbackup.data.FolderPrefs
import de.stefan.nasbackup.media.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FolderPickerScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var buckets by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf(FolderPrefs.selected(ctx)) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        buckets = withContext(Dispatchers.IO) { MediaScanner.listBuckets(ctx) }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ordner auswählen", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Nur angehakte Ordner werden gesichert. Leer = nichts wird gesichert.",
            style = MaterialTheme.typography.bodySmall
        )

        if (loading) {
            CircularProgressIndicator()
        } else if (buckets.isEmpty()) {
            Text("Keine Fotos oder Videos auf dem Gerät gefunden.")
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = buckets.isNotEmpty() && selected.containsAll(buckets),
                    onCheckedChange = { checked ->
                        selected = if (checked) buckets.toSet() else emptySet()
                    }
                )
                Text("Alle")
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(buckets) { bucket ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = bucket in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + bucket else selected - bucket
                            }
                        )
                        Text(bucket)
                    }
                }
            }
        }

        Button(
            onClick = {
                FolderPrefs.save(ctx, selected)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Speichern") }
    }
}

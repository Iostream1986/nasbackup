package de.stefan.nasbackup.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import de.stefan.nasbackup.data.CredentialStore
import de.stefan.nasbackup.net.RemoteMediaItem
import de.stefan.nasbackup.net.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.File

/**
 * Zeigt alle Fotos/Videos des Kontos vom Server -- ueber alle Geraete-
 * Unterordner hinweg (PROPFIND auf der Konto-Wurzel), gruppiert nach
 * demselben Ordnernamen (Bucket, z.B. "Facebook", "Camera"), der auch beim
 * Hochladen verwendet wird. Tippen zeigt eine Datei nur temporaer an,
 * Lang-Druck laedt eine einzelne Datei dauerhaft herunter, Mehrfachauswahl
 * darueber. Noch kein automatischer Download, das ist bewusst der naechste,
 * groessere Schritt.
 */
@Composable
fun CloudGalleryScreen(creds: CredentialStore.Creds, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<RemoteMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var batchWorking by remember { mutableStateOf(false) }

    val client = remember { WebDavClient(creds.baseUrl, creds.user, creds.password) }

    val imageLoader = remember {
        val authHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(creds.user, creds.password))
                    .build()
                chain.proceed(request)
            }
            .build()

        ImageLoader.Builder(ctx)
            .okHttpClient(authHttp)
            .build()
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            client.listRecursive(creds.user)
                .onSuccess { list ->
                    items = list
                        .filter {
                            val m = it.mimeType.orEmpty()
                            m.startsWith("image/") || m.startsWith("video/")
                        }
                        .sortedByDescending { it.lastModified }
                }
                .onFailure { error = it.message ?: "Unbekannter Fehler" }
        }
        loading = false
    }

    // Erstes Pfadsegment ist das Konto, zweites derselbe Ordnername (Bucket),
    // der beim Hochladen genutzt wurde -- siehe UploadWorker.
    val grouped = items
        .groupBy { it.relativePath.split("/").getOrNull(1) ?: "Sonstige" }
        .toSortedMap()

    fun downloadSelected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(ctx, "Herunterladen braucht Android 10+", Toast.LENGTH_LONG).show()
            return
        }
        val toDownload = items.filter { it.relativePath in selected }
        if (toDownload.isEmpty()) return
        batchWorking = true
        scope.launch {
            var ok = 0
            withContext(Dispatchers.IO) {
                toDownload.forEach { if (saveToDevice(ctx, client, it)) ok++ }
            }
            batchWorking = false
            Toast.makeText(ctx, "$ok von ${toDownload.size} heruntergeladen", Toast.LENGTH_SHORT).show()
            selected = emptySet()
            selectionMode = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Cloud-Galerie", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Alle Geräte dieses Kontos, gruppiert nach Ordner. Antippen: ansehen. " +
                "Lang drücken: einzeln herunterladen.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) selected = emptySet()
            }) {
                Text(if (selectionMode) "Auswahl beenden" else "Mehrere auswählen")
            }
            if (selectionMode && selected.isNotEmpty()) {
                Button(enabled = !batchWorking, onClick = { downloadSelected() }) {
                    Text(if (batchWorking) "Lädt…" else "Herunterladen (${selected.size})")
                }
            }
        }

        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(
                "Fehler beim Laden: $error",
                color = MaterialTheme.colorScheme.error
            )
            items.isEmpty() -> Text("Keine Dateien auf dem Server gefunden.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                grouped.forEach { (bucket, bucketItems) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header-$bucket") {
                        Text(
                            bucket,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(bucketItems, key = { it.relativePath }) { item ->
                        CloudTile(
                            item = item,
                            client = client,
                            imageLoader = imageLoader,
                            selectionMode = selectionMode,
                            selected = item.relativePath in selected,
                            onToggleSelect = {
                                selected = if (item.relativePath in selected) {
                                    selected - item.relativePath
                                } else {
                                    selected + item.relativePath
                                }
                            }
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Zurück")
        }
    }
}

private enum class TileLoadState { LOADING, SUCCESS, ERROR }

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CloudTile(
    item: RemoteMediaItem,
    client: WebDavClient,
    imageLoader: ImageLoader,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                enabled = !downloading,
                onClick = {
                    if (selectionMode) {
                        onToggleSelect()
                        return@combinedClickable
                    }
                    downloading = true
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { downloadToCache(ctx, client, item) }
                        downloading = false
                        if (file != null) openFile(ctx, file, item.mimeType)
                    }
                },
                onLongClick = {
                    if (selectionMode) {
                        onToggleSelect()
                        return@combinedClickable
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Toast.makeText(ctx, "Herunterladen braucht Android 10+", Toast.LENGTH_LONG).show()
                        return@combinedClickable
                    }
                    downloading = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveToDevice(ctx, client, item) }
                        downloading = false
                        Toast.makeText(
                            ctx,
                            if (ok) "Heruntergeladen: ${item.name}" else "Download fehlgeschlagen",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
    ) {
        val isVideo = item.mimeType?.startsWith("video/") == true

        if (isVideo) {
            // Keine Vorschau: dafuer muesste die komplette Videodatei geladen
            // werden, nur um ein Standbild rauszuziehen -- bei vielen/grossen
            // Videos sorgt das fuer gefuehlt haengende Downloads.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF303030)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color.White, fontSize = 28.sp)
            }
        } else {
            var loadState by remember(item.relativePath) { mutableStateOf(TileLoadState.LOADING) }
            AsyncImage(
                model = item.href,
                imageLoader = imageLoader,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { loadState = TileLoadState.LOADING },
                onSuccess = { loadState = TileLoadState.SUCCESS },
                onError = { loadState = TileLoadState.ERROR }
            )
            when (loadState) {
                TileLoadState.LOADING -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp)
                )
                TileLoadState.ERROR -> Text(
                    "⚠",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                TileLoadState.SUCCESS -> Unit
            }
        }

        if (downloading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )
        }
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

private fun downloadToCache(ctx: Context, client: WebDavClient, item: RemoteMediaItem): File? {
    val dir = File(ctx.cacheDir, "cloud_gallery").apply { mkdirs() }
    val target = File(dir, item.name)
    return client.download(item.relativePath, target)
        .map { target }
        .getOrNull()
}

private fun openFile(ctx: Context, file: File, mimeType: String?) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(intent)
}

/**
 * Speichert dauerhaft in die MediaStore-Galerie des Geraets (Pictures/NasBackup
 * bzw. Movies/NasBackup) -- bewusste, manuelle Nutzeraktion (Lang-Druck oder
 * Mehrfachauswahl), kein automatischer Rueck-Sync (siehe ENTSCHEIDUNGEN.md).
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun saveToDevice(ctx: Context, client: WebDavClient, item: RemoteMediaItem): Boolean {
    val isVideo = item.mimeType?.startsWith("video/") == true
    val collection = if (isVideo) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val relativeDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType ?: "application/octet-stream")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativeDir/NasBackup")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = ctx.contentResolver.insert(collection, values) ?: return false

    val ok = runCatching {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            client.downloadTo(item.relativePath, out).getOrThrow()
        } ?: throw Exception("Kein OutputStream")
    }.isSuccess

    if (ok) {
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
    } else {
        ctx.contentResolver.delete(uri, null, null)
    }
    return ok
}

package de.stefan.nasbackup.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import de.stefan.nasbackup.data.UploadLog
import de.stefan.nasbackup.media.MediaItem
import de.stefan.nasbackup.media.MediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rein lokale Ansicht: zeigt, was auf dem Geraet in den ausgewaehlten Ordnern
 * liegt und markiert pro Kachel, ob es laut UploadLog schon oben ist. Fragt
 * dafuer nichts vom Server ab -- das ist ein spaeterer, groesserer Schritt.
 */
@Composable
fun GalleryScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val imageLoader = remember {
        ImageLoader.Builder(ctx)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { MediaScanner.scan(ctx).asReversed() }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Galerie", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Neueste zuerst. Grün = hochgeladen, Grau = noch ausstehend.",
            style = MaterialTheme.typography.bodySmall
        )

        when {
            loading -> CircularProgressIndicator()
            items.isEmpty() -> Text("Keine Dateien in den ausgewählten Ordnern.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(items, key = { it.key }) { item ->
                    GalleryTile(item = item, imageLoader = imageLoader)
                }
            }
        }

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Zurück")
        }
    }
}

@Composable
private fun GalleryTile(item: MediaItem, imageLoader: ImageLoader) {
    val ctx = LocalContext.current
    val done = remember(item.key) { UploadLog.isDone(ctx, item.key) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, item.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(intent)
            }
    ) {
        AsyncImage(
            model = item.uri,
            imageLoader = imageLoader,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(
                    color = if (done) Color(0xCC2E7D32) else Color(0xCC616161)
                )
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = if (done) "✓" else "…",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

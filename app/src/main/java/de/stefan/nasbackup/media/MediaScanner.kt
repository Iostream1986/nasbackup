package de.stefan.nasbackup.media

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import de.stefan.nasbackup.data.FolderPrefs

data class MediaItem(
    val key: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateAddedSeconds: Long,
    val bucket: String
)

/**
 * Liest Fotos und Videos ueber den MediaStore, nicht ueber das Dateisystem.
 * Direkter Dateizugriff funktioniert seit Android 10 nicht mehr zuverlaessig.
 */
object MediaScanner {

    fun scan(ctx: Context): List<MediaItem> {
        val selected = FolderPrefs.selected(ctx)
        val result = mutableListOf<MediaItem>()
        result += query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "img")
        result += query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "vid")
        val filtered = if (selected.isEmpty()) result else result.filter { it.bucket in selected }
        return filtered.sortedBy { it.dateAddedSeconds }
    }

    /** Fuer die Ordner-Auswahl in den Einstellungen: alle vorhandenen Ordnernamen. */
    fun listBuckets(ctx: Context): List<String> {
        val buckets = sortedSetOf<String>()
        buckets += bucketNames(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        buckets += bucketNames(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return buckets.toList()
    }

    private fun bucketNames(ctx: Context, collection: Uri): Set<String> {
        val names = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        ctx.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val col = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                c.getString(col)?.let { names += it }
            }
        }
        return names
    }

    private fun query(ctx: Context, collection: Uri, prefix: String): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )

        val items = mutableListOf<MediaItem>()
        val cursor: Cursor = ctx.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} ASC"
        ) ?: return items

        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val size = c.getLong(sizeCol)
                if (size <= 0L) continue

                items += MediaItem(
                    key = "$prefix:$id",
                    uri = Uri.withAppendedPath(collection, id.toString()),
                    displayName = c.getString(nameCol) ?: "$prefix-$id",
                    sizeBytes = size,
                    mimeType = c.getString(mimeCol),
                    dateAddedSeconds = c.getLong(dateCol),
                    bucket = c.getString(bucketCol) ?: "Sonstige"
                )
            }
        }
        return items
    }
}

package de.stefan.nasbackup.media

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

data class MediaItem(
    val key: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val dateAddedSeconds: Long
)

/**
 * Liest Fotos und Videos ueber den MediaStore, nicht ueber das Dateisystem.
 * Direkter Dateizugriff funktioniert seit Android 10 nicht mehr zuverlaessig.
 */
object MediaScanner {

    fun scan(ctx: Context): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        result += query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "img")
        result += query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "vid")
        return result.sortedBy { it.dateAddedSeconds }
    }

    private fun query(ctx: Context, collection: Uri, prefix: String): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED
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
                    dateAddedSeconds = c.getLong(dateCol)
                )
            }
        }
        return items
    }
}

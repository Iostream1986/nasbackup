package de.stefan.nasbackup.data

import android.content.Context

/**
 * Welche Medienordner gesichert werden sollen. Leere Auswahl heisst:
 * alle Ordner. Nur fuers Testen gedacht, deshalb unverschluesselte Prefs.
 */
object FolderPrefs {

    private const val FILE = "nas_folder_prefs"
    private const val KEY_SELECTED = "selected_buckets"

    fun selected(ctx: Context): Set<String> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(KEY_SELECTED, emptySet())
            ?: emptySet()

    fun save(ctx: Context, buckets: Set<String>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SELECTED, buckets)
            .apply()
    }
}

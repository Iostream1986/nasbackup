package de.stefan.nasbackup.data

import android.content.Context

/**
 * Merkt sich, welche Medien schon oben liegen.
 *
 * Bewusst simpel als String-Set. Traegt bis in den Bereich einiger
 * zehntausend Dateien. Danach gehoert hier eine Room-Datenbank hin.
 */
object UploadLog {

    private const val FILE = "upload_log"
    private const val KEY = "done"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun done(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet()

    fun isDone(ctx: Context, id: String): Boolean = done(ctx).contains(id)

    fun markDone(ctx: Context, id: String) {
        // getStringSet liefert eine Referenz, die nicht veraendert werden darf.
        val updated = HashSet(done(ctx))
        updated.add(id)
        prefs(ctx).edit().putStringSet(KEY, updated).apply()
    }

    fun count(ctx: Context): Int = done(ctx).size

    fun reset(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }
}

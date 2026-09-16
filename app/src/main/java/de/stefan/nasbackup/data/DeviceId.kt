package de.stefan.nasbackup.data

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Stabiler, pfadtauglicher Geraete-Bezeichner. Trennt Uploads mehrerer
 * Geraete desselben Kontos, damit gleichnamige Dateien (z.B. WhatsApp-Bilder
 * mit geraeteeigener Nummerierung, die bei 1 neu anfaengt) sich nicht
 * gegenseitig als "schon hochgeladen" ausbremsen.
 */
object DeviceId {

    fun forPath(ctx: Context): String {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unbekannt"
        val model = PathSegment.sanitize(Build.MODEL, "geraet")
        return "$model-${androidId.take(6)}"
    }
}

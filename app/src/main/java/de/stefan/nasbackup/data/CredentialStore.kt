package de.stefan.nasbackup.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Zugangsdaten liegen verschluesselt im App-eigenen Speicher.
 * Der Schluessel steckt im Android Keystore und verlaesst das Geraet nie.
 *
 * Es gibt bewusst KEINE eigene Nutzerverwaltung: die Anmeldung geht direkt
 * gegen den WebDAV-Server.
 */
object CredentialStore {

    data class Creds(
        val baseUrl: String,
        val user: String,
        val password: String
    )

    private const val FILE = "nas_credentials"

    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(ctx: Context, creds: Creds) {
        prefs(ctx).edit()
            .putString("url", creds.baseUrl.trimEnd('/'))
            .putString("user", creds.user)
            .putString("pass", creds.password)
            .apply()
    }

    fun load(ctx: Context): Creds? {
        val p = prefs(ctx)
        val url = p.getString("url", null) ?: return null
        val user = p.getString("user", null) ?: return null
        val pass = p.getString("pass", null) ?: return null
        return Creds(url, user, pass)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}

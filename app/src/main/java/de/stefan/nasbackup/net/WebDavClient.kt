package de.stefan.nasbackup.net

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimaler WebDAV-Client. Nur vier Methoden werden gebraucht:
 *
 *   PROPFIND  - Anmeldung pruefen
 *   MKCOL     - Ordner anlegen
 *   HEAD      - pruefen ob eine Datei schon existiert
 *   PUT       - hochladen
 */
class WebDavClient(
    baseUrl: String,
    user: String,
    password: String
) {
    private val base = baseUrl.trimEnd('/')
    private val auth = Credentials.basic(user, password)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private fun url(path: String): String {
        val clean = path.trim('/')
        if (clean.isEmpty()) return "$base/"
        // Jedes Segment einzeln kodieren, damit Leerzeichen und Umlaute
        // in Dateinamen die URL nicht zerlegen.
        val encoded = clean.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        return "$base/$encoded"
    }

    private fun builder(path: String) = Request.Builder()
        .url(url(path))
        .header("Authorization", auth)

    fun checkLogin(): Result<Unit> = runCatching {
        val request = builder("/")
            .method("PROPFIND", null)
            .header("Depth", "0")
            .build()
        http.newCall(request).execute().use { response ->
            when (response.code) {
                207, 200 -> Unit
                401 -> throw Exception("Benutzername oder Passwort falsch")
                404 -> throw Exception("Pfad auf dem Server nicht gefunden")
                else -> throw Exception("Server antwortet mit ${response.code}")
            }
        }
    }

    /** 405 heisst: Ordner existiert bereits. Das ist der Normalfall. */
    fun makeDir(path: String): Result<Unit> = runCatching {
        val request = builder(path).method("MKCOL", null).build()
        http.newCall(request).execute().use { response ->
            when (response.code) {
                201, 405, 301 -> Unit
                409 -> throw Exception("Uebergeordneter Ordner fehlt: $path")
                else -> throw Exception("MKCOL $path -> ${response.code}")
            }
        }
    }

    /** Legt alle Ebenen an, z.B. "DCIM/2026-09". */
    fun makeDirs(path: String): Result<Unit> = runCatching {
        var current = ""
        for (segment in path.trim('/').split('/')) {
            if (segment.isEmpty()) continue
            current = if (current.isEmpty()) segment else "$current/$segment"
            makeDir(current).getOrThrow()
        }
    }

    fun exists(path: String): Boolean = runCatching {
        val request = builder(path).head().build()
        http.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    fun upload(
        path: String,
        length: Long,
        mimeType: String?,
        openStream: () -> InputStream
    ): Result<Unit> = runCatching {
        val body = StreamBody(mimeType, length, openStream)
        val request = builder(path).put(body).build()
        http.newCall(request).execute().use { response ->
            if (response.code !in listOf(200, 201, 204)) {
                throw Exception("PUT $path -> ${response.code}")
            }
        }
    }

    /**
     * Streamt direkt aus dem ContentResolver zum Server, ohne die Datei
     * vorher in den Speicher zu laden. Wichtig bei grossen Videos.
     */
    private class StreamBody(
        private val mimeType: String?,
        private val length: Long,
        private val opener: () -> InputStream
    ) : RequestBody() {
        override fun contentType() =
            (mimeType ?: "application/octet-stream").toMediaTypeOrNull()

        override fun contentLength(): Long = length

        override fun writeTo(sink: BufferedSink) {
            opener().use { input -> sink.writeAll(input.source()) }
        }
    }
}

package de.stefan.nasbackup.net

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** Eine Datei aus einem PROPFIND-Listing (siehe [WebDavClient.listRecursive]). */
data class RemoteMediaItem(
    /** Absolute URL, direkt fuer GET/Thumbnails nutzbar. */
    val href: String,
    /** Dekodierter Pfad relativ zur Server-Wurzel, z.B. "user/DCIM/Pixel-8-abc123/2026-09/IMG.jpg". */
    val relativePath: String,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long
)

/**
 * Minimaler WebDAV-Client. Kern-Methoden:
 *
 *   MKCOL     - Ordner anlegen (auch fuer den Login-Check genutzt, siehe unten)
 *   HEAD      - pruefen ob eine Datei schon existiert
 *   PUT       - hochladen
 *   PROPFIND  - Ordner rekursiv auflisten
 *   GET       - herunterladen
 */
class WebDavClient(
    baseUrl: String,
    user: String,
    password: String
) {
    private val base = baseUrl.trimEnd('/')
    private val userName = user
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

    /**
     * Prueft die Anmeldung per MKCOL auf den eigenen Nutzerordner (statt
     * PROPFIND auf "/"): Der Server isoliert Nutzer so, dass der reine
     * Root-Zugriff fuer niemanden erlaubt ist (siehe server/serve.py,
     * IsolatingHtpasswdDC). MKCOL auf den eigenen Ordner ist idempotent
     * (405 = existiert schon) und legt ihn beim allerersten Login gleich an.
     */
    fun checkLogin(): Result<Unit> = runCatching {
        val request = builder(userName)
            .method("MKCOL", null)
            .build()
        http.newCall(request).execute().use { response ->
            when (response.code) {
                201, 405, 301 -> Unit
                401 -> throw Exception("Benutzername oder Passwort falsch")
                else -> throw Exception("Server antwortet mit ${response.code}")
            }
        }
    }

    /**
     * Aendert das eigene Passwort. Der Server prueft dafuer das aktuell in
     * diesem Client hinterlegte (alte) Passwort per Basic Auth, siehe
     * server/serve.py -> /_password.
     */
    fun changePassword(newPassword: String): Result<Unit> = runCatching {
        val json = JSONObject().put("new_password", newPassword).toString()
        val request = Request.Builder()
            .url("$base/_password")
            .header("Authorization", auth)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            when (response.code) {
                200 -> Unit
                401 -> throw Exception("Aktuelles Passwort falsch")
                400 -> throw Exception(
                    response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: "Neues Passwort ungueltig"
                )
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
     * Listet einen Ordner rekursiv (PROPFIND, Depth: infinity) und liefert
     * alle enthaltenen Dateien (keine Ordner) ueber alle Ebenen hinweg --
     * bei "<konto>/DCIM" also automatisch ueber alle Geraete-Unterordner
     * hinweg, nicht nur das aktuelle Geraet.
     */
    fun listRecursive(path: String): Result<List<RemoteMediaItem>> = runCatching {
        val body = """<?xml version="1.0" encoding="utf-8" ?>
            |<propfind xmlns="DAV:"><allprop/></propfind>
        """.trimMargin().toRequestBody("application/xml; charset=utf-8".toMediaType())

        val request = builder(path)
            .method("PROPFIND", body)
            .header("Depth", "infinity")
            .build()

        val xml = http.newCall(request).execute().use { response ->
            if (response.code != 207) throw Exception("PROPFIND $path -> ${response.code}")
            response.body?.string() ?: throw Exception("Leere Antwort")
        }

        parseMultistatus(xml)
    }

    /** Laedt eine Datei in eine lokale Datei herunter. */
    fun download(path: String, target: File): Result<Unit> = runCatching {
        target.outputStream().use { out -> downloadTo(path, out).getOrThrow() }
    }

    /** Wie [download], schreibt aber in einen beliebigen Sink (z.B. einen MediaStore-Eintrag). */
    fun downloadTo(path: String, sink: OutputStream): Result<Unit> = runCatching {
        val request = builder(path).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("GET $path -> ${response.code}")
            val responseBody = response.body ?: throw Exception("Leere Antwort")
            responseBody.byteStream().copyTo(sink)
        }
    }

    private fun parseMultistatus(xml: String): List<RemoteMediaItem> {
        val origin = URI(base).let { "${it.scheme}://${it.authority}" }

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val responses = doc.getElementsByTagNameNS("DAV:", "response")

        return (0 until responses.length).mapNotNull { i ->
            val node = responses.item(i)
            val href = node.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent
                ?: return@mapNotNull null

            val isCollection = node.getElementsByTagNameNS("DAV:", "collection").length > 0
            if (isCollection) return@mapNotNull null

            val size = node.getElementsByTagNameNS("DAV:", "getcontentlength")
                .item(0)?.textContent?.toLongOrNull() ?: 0L
            val mimeType = node.getElementsByTagNameNS("DAV:", "getcontenttype")
                .item(0)?.textContent
            val lastModified = node.getElementsByTagNameNS("DAV:", "getlastmodified")
                .item(0)?.textContent?.let {
                    runCatching {
                        java.time.ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli()
                    }.getOrNull()
                } ?: 0L

            val decodedPath = URLDecoder.decode(href, "UTF-8").trim('/')
            val name = decodedPath.substringAfterLast('/')

            RemoteMediaItem(
                href = if (href.startsWith("http")) href else "$origin$href",
                relativePath = decodedPath,
                name = name,
                mimeType = mimeType,
                size = size,
                lastModified = lastModified
            )
        }
    }

    private fun org.w3c.dom.Node.getElementsByTagNameNS(ns: String, name: String): org.w3c.dom.NodeList =
        (this as org.w3c.dom.Element).getElementsByTagNameNS(ns, name)

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

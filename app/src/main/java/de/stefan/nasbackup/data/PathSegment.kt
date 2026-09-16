package de.stefan.nasbackup.data

/** Macht beliebige Namen (Geraetemodell, Medien-Ordnername, ...) pfadtauglich. */
object PathSegment {
    fun sanitize(raw: String, fallback: String): String =
        raw.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { fallback }
}

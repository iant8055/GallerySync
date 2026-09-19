package com.gallery.sync.domain.backup

/**
 * A OneDrive listing as text, so the Restore tab can keep it across the app being closed.
 *
 * ### Why it exists
 *
 * The listing is the slow part of the Restore tab, and holding it in memory stopped a tab switch from
 * reading the drive again. But a closed app forgets memory, so every launch started the tab from
 * nothing. Ian, 19 Sept 2026: *"when you close the app the RESTORE tab loses its list."* Written to a
 * file, it is there straight away on the next launch and is refreshed behind it.
 *
 * ### Why hand-written and plain
 *
 * One line per file, tab-separated, with the three characters that would break that (backslash, tab,
 * newline) escaped. Pure Kotlin, so it is tested on the JVM without a device, and a file that is cut
 * short, from an older version or from anything else decodes to `null` rather than to a wrong list.
 * `null` costs one read of the drive. A wrong list would offer files that are not there.
 */
object DriveListingCodec {

    private const val VERSION = "v1"
    private const val NO_SIZE = "-"

    /** What was stored: the listing, when it was read, and which OneDrive folder it was read under. */
    data class Decoded(val listing: DriveListing, val listedAtMillis: Long, val root: String)

    fun encode(listing: DriveListing, listedAtMillis: Long, root: String): String = buildString {
        append(VERSION).append('\t')
            .append(listedAtMillis).append('\t')
            .append(if (listing.couldNotList) "1" else "0").append('\t')
            .append(escape(root)).append('\n')

        for ((folder, files) in listing.folders) {
            append("F\t").append(escape(folder)).append('\n')
            for ((name, ref) in files) {
                append("R\t").append(escape(name)).append('\t')
                    .append(escape(ref.id)).append('\t')
                    .append(ref.sizeBytes?.toString() ?: NO_SIZE).append('\t')
                    .append(escape(ref.mimeType)).append('\t')
                    .append(ref.createdAtEpochMillis).append('\n')
            }
        }
    }

    /** `null` for anything that is not exactly what [encode] writes. */
    fun decode(text: String): Decoded? = try {
        val lines = text.split('\n').filter { it.isNotEmpty() }
        val header = lines.firstOrNull()?.split('\t')
        if (header == null || header.size != 4 || header[0] != VERSION) {
            null
        } else {
            val folders = LinkedHashMap<String, MutableMap<String, RemoteFileRef>>()
            var current: MutableMap<String, RemoteFileRef>? = null
            var valid = true

            for (line in lines.drop(1)) {
                val cells = line.split('\t')
                when (cells.firstOrNull()) {
                    "F" -> if (cells.size == 2) {
                        current = LinkedHashMap<String, RemoteFileRef>().also { folders[unescape(cells[1])] = it }
                    } else {
                        valid = false
                    }

                    "R" -> if (cells.size == 6 && current != null) {
                        current[unescape(cells[1])] = RemoteFileRef(
                            id = unescape(cells[2]),
                            sizeBytes = if (cells[3] == NO_SIZE) null else cells[3].toLong(),
                            mimeType = unescape(cells[4]),
                            createdAtEpochMillis = cells[5].toLong()
                        )
                    } else {
                        valid = false
                    }

                    else -> valid = false
                }
                if (!valid) break
            }

            if (!valid) {
                null
            } else {
                Decoded(
                    listing = DriveListing(folders, couldNotList = header[2] == "1"),
                    listedAtMillis = header[1].toLong(),
                    root = unescape(header[3])
                )
            }
        }
    } catch (_: NumberFormatException) {
        null
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private fun unescape(text: String): String {
        if ('\\' !in text) return text
        return buildString(text.length) {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    when (val next = text[i + 1]) {
                        't' -> append('\t')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        else -> append(next)
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }
}

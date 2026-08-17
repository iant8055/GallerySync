package com.gallery.sync.ui.common

import android.content.Context
import com.gallery.sync.R
import java.util.Locale

/**
 * Human-readable byte size.
 *
 * Goes through string resources so the number/unit arrangement can be adapted per language, and
 * formats the decimal with the device's locale — a great many locales write 1,5 GB rather than
 * 1.5 GB, and hardcoding a full stop looks broken to those readers.
 *
 * Binary units, matching what file managers show.
 */
fun formatBytes(context: Context, bytes: Long): String = when {
    bytes < 1024 ->
        context.getString(R.string.size_bytes, bytes)

    bytes < 1024 * 1024 ->
        context.getString(R.string.size_kilobytes, bytes / 1024)

    bytes < 1024L * 1024 * 1024 ->
        context.getString(R.string.size_megabytes, bytes / (1024 * 1024))

    else -> context.getString(
        R.string.size_gigabytes,
        String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024 * 1024))
    )
}

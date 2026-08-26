package com.gallery.sync.ui.common

import android.content.Context
import com.gallery.sync.R
import java.util.Locale

/**
 * Human-readable byte size, in **decimal** units — 1 KB is 1000 bytes, not 1024.
 *
 * Goes through string resources so the number/unit arrangement can be adapted per language, and
 * formats the decimal with the device's locale — a great many locales write 1,5 GB rather than
 * 1.5 GB, and hardcoding a full stop looks broken to those readers.
 *
 * ### Why decimal, changed 26 Aug 2026
 *
 * This divided by 1024 and labelled the result "GB", which is GiB wearing the wrong name. The effect
 * is that every size in this app read about 7% smaller than the same file in Samsung Gallery, My
 * Files and Android's own UI, which are decimal — `android.text.format.Formatter.formatFileSize`
 * is decimal precisely so apps agree with the system.
 *
 * **That matters more here than in most apps, because this one really does shrink files.** Proxying
 * rewrites a photo at 2048px. A user who compares our number against the gallery's and finds ours
 * smaller has every reason to conclude we shrank their video. Ian hit exactly that on 26 Aug 2026
 * with a 2 GB clip that was byte-for-byte intact: 2,032,370,426 bytes reads as 1.9 binary and 2.0
 * decimal, and the gap looked like loss.
 *
 * An app whose whole principle is feeding the existing gallery rather than replacing it must not
 * contradict that gallery about a file's size. Matching the platform is the point; being technically
 * defensible about GiB is not.
 */
fun formatBytes(context: Context, bytes: Long): String = when {
    bytes < KB ->
        context.getString(R.string.size_bytes, bytes)

    bytes < MB ->
        context.getString(R.string.size_kilobytes, bytes / KB)

    bytes < GB ->
        context.getString(R.string.size_megabytes, bytes / MB)

    else -> context.getString(
        R.string.size_gigabytes,
        String.format(Locale.getDefault(), "%.1f", bytes / GB.toDouble())
    )
}

private const val KB = 1_000L
private const val MB = 1_000_000L
private const val GB = 1_000_000_000L

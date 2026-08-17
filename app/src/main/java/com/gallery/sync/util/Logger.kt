package com.gallery.sync.util

import android.util.Log
import com.gallery.sync.BuildConfig

/**
 * The single logging entry point for the app.
 *
 * This is the only file under `app/src/main/` permitted to reference [android.util.Log].
 *
 * It is deliberately an `object` rather than an injected dependency: the ContentProvider needs to
 * log during `onCreate`, which runs before Hilt can field-inject it.
 */
object Logger {

    fun v(tag: String, message: String) {
        if (!isLoggable(Level.VERBOSE, BuildConfig.DEBUG)) return
        Log.v(formatTag(tag), message)
    }

    fun d(tag: String, message: String) {
        if (!isLoggable(Level.DEBUG, BuildConfig.DEBUG)) return
        Log.d(formatTag(tag), message)
    }

    fun i(tag: String, message: String) {
        if (!isLoggable(Level.INFO, BuildConfig.DEBUG)) return
        Log.i(formatTag(tag), message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!isLoggable(Level.WARN, BuildConfig.DEBUG)) return
        val formatted = formatTag(tag)
        if (throwable == null) Log.w(formatted, message) else Log.w(formatted, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!isLoggable(Level.ERROR, BuildConfig.DEBUG)) return
        val formatted = formatTag(tag)
        if (throwable == null) Log.e(formatted, message) else Log.e(formatted, message, throwable)
    }
}

/** Log severity, ordered from least to most severe. */
internal enum class Level {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/** Prefix applied to every tag so the whole app is greppable in logcat. */
internal const val TAG_PREFIX = "GallerySync/"

/** Long tags are hostile in logcat, so the final tag is capped at this length. */
internal const val MAX_TAG_LENGTH = 23

/**
 * Prefixes [tag] with [TAG_PREFIX] and truncates the result to at most [MAX_TAG_LENGTH] chars.
 *
 * Pure and `internal` so unit tests can exercise it without an Android device.
 */
internal fun formatTag(tag: String): String = (TAG_PREFIX + tag).take(MAX_TAG_LENGTH)

/**
 * Whether [level] should be emitted for a build where `BuildConfig.DEBUG` is [isDebugBuild].
 *
 * VERBOSE and DEBUG are release-build no-ops; INFO, WARN and ERROR always emit.
 *
 * Pure and `internal` so unit tests can exercise it without an Android device.
 */
internal fun isLoggable(level: Level, isDebugBuild: Boolean): Boolean = when (level) {
    Level.VERBOSE, Level.DEBUG -> isDebugBuild
    Level.INFO, Level.WARN, Level.ERROR -> true
}

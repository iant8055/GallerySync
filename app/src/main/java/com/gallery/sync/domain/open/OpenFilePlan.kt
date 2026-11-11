package com.gallery.sync.domain.open

/**
 * What a long-press on a file does (TASK-028, Ian, 11 Nov 2026). A pure function of what is known at the moment
 * of the press, so every row of the task's decisions table is a unit test rather than a thumb on a phone.
 *
 * GallerySync draws nothing here. Every answer ends in another app showing the file — the phone's own gallery or
 * video player, or the Cloud's app — or in a refusal with a reason. **No in-app viewer**: if this ever needs one,
 * it is the wrong build (CLAUDE.md, *GallerySync is invisible*).
 */
object OpenFilePlan {

    /**
     * Above this, on mobile data, the user is asked before a temporary download (decision 7). Decimal, as
     * `formatBytes` prints sizes, so the number in the question and the line agree: exactly 750 MB does not ask.
     */
    const val ASK_ON_MOBILE_ABOVE_BYTES = 750L * 1000 * 1000

    /**
     * The first step for a long-press.
     *
     * A file on the phone opens there whatever the setting, an optimised one included (decisions 2 and 3): the
     * setting is only for files that are not on the phone. A Cloud with no link at all downloads with no
     * question (decision 5).
     */
    fun firstStep(
        onPhone: Boolean,
        setting: OpenNotOnPhone,
        cloudHasLinks: Boolean,
        canDownload: Boolean,
        network: NetworkKind,
        mobileDataAllowed: Boolean,
        sizeBytes: Long
    ): OpenStep = when {
        onPhone -> OpenStep.OpenOnPhone
        network == NetworkKind.NONE -> OpenStep.Refuse(RefuseReason.NO_NETWORK)
        setting == OpenNotOnPhone.CLOUD && cloudHasLinks -> OpenStep.FetchLink
        else -> downloadStep(canDownload, network, mobileDataAllowed, sizeBytes)
    }

    /**
     * Whether a temporary download may start, and whether to ask first. Also what the user's *Download* leads to
     * after a link could not be fetched (decision 8), under the same rules.
     *
     * The download obeys the app's own mobile-data switch (decision 6): off, and on mobile data, it does not
     * happen at all. On Wi-Fi it never asks, whatever the size.
     */
    fun downloadStep(
        canDownload: Boolean,
        network: NetworkKind,
        mobileDataAllowed: Boolean,
        sizeBytes: Long
    ): OpenStep = when {
        !canDownload -> OpenStep.Refuse(RefuseReason.CANNOT_DOWNLOAD)
        network == NetworkKind.NONE -> OpenStep.Refuse(RefuseReason.NO_NETWORK)
        network == NetworkKind.MOBILE && !mobileDataAllowed -> OpenStep.Refuse(RefuseReason.MOBILE_DATA_OFF)
        network == NetworkKind.MOBILE && sizeBytes > ASK_ON_MOBILE_ABOVE_BYTES -> OpenStep.Download(askFirst = true)
        else -> OpenStep.Download(askFirst = false)
    }
}

/** Settings → General: what a long-press does with a file that is not on the phone. */
enum class OpenNotOnPhone {
    /** The default (Ian, 11 Nov 2026): hand the file to its Cloud's app or page. */
    CLOUD,

    /** Fetch a copy into the app's cache and open that. Never written to the gallery. */
    DOWNLOAD;

    companion object {
        val DEFAULT = CLOUD

        fun fromNameOrDefault(name: String?): OpenNotOnPhone = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** The connection the phone has right now, as far as a download is concerned. */
enum class NetworkKind { WIFI, MOBILE, NONE }

sealed interface OpenStep {
    /** Hand the file on the phone to the phone's default app. */
    data object OpenOnPhone : OpenStep

    /** Ask the Cloud for a link and open it; if that fails, ask Cancel or Download. */
    data object FetchLink : OpenStep

    /** Download a temporary copy, asking first when [askFirst]. */
    data class Download(val askFirst: Boolean) : OpenStep

    data class Refuse(val reason: RefuseReason) : OpenStep
}

enum class RefuseReason {
    /** No connection at all. */
    NO_NETWORK,

    /** On mobile data with *Use mobile data* off. */
    MOBILE_DATA_OFF,

    /** This Cloud cannot hand the file back to the app (Google Photos, today). */
    CANNOT_DOWNLOAD
}

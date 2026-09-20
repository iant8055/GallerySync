package com.gallery.sync.data.local.entity

/**
 * What has already been settled about the OneDrive copy of a file that is no longer on the phone.
 *
 * `null` means nothing has: the file is a candidate for the "files deleted from this phone" window.
 * Either value takes it out of that window, and both are cleared again when the file returns to the
 * phone, so a file deleted a second time is offered a second time. Ian, 19 Sept 2026: *"cancelled
 * files are left out unless they are deleted again."*
 *
 * Bookkeeping only. Neither value removes, sends or changes a file anywhere.
 */
enum class CloudCopyDecision {

    /**
     * Archive took this file off the phone on purpose, because the album's mode said to. Its OneDrive
     * copy is the only copy and is exactly what the user asked to have kept there, so it is never
     * offered for removal. Written by the Archive tab when a removal completes.
     */
    ARCHIVED,

    /**
     * The user was shown the file in the window and chose to leave its OneDrive copy alone, by not
     * ticking it. Left out from then on, until the file is back on the phone and gone again.
     */
    KEPT
}

package com.gallery.sync.domain.billing

import android.app.Activity

/**
 * Whether GallerySync's Pro features are unlocked, and how to unlock them.
 *
 * CLAUDE.md's monetization section: this is the single source of truth for purchase state.
 * [isPurchased] queries Play's own local purchase cache fresh every call rather than persisting a
 * flag of its own, so nothing here can drift from what Play actually thinks is owned. [PRO_UNLOCK]
 * gates Google Photos sync; OneDrive and the ContentProvider are never gated behind it.
 */
interface BillingRepository {

    /** Whether [PRO_UNLOCK] is owned. */
    suspend fun isPurchased(): Boolean

    /** Runs the interactive purchase flow, hosting Play's UI from [activity]. */
    suspend fun purchase(activity: Activity): PurchaseOutcome

    companion object {
        /** Play Console product ID. Unlocks every second backup destination at once — see TASK-026. */
        const val PRO_UNLOCK = "pro_unlock"
    }
}

/** Outcome of an interactive purchase attempt. */
sealed interface PurchaseOutcome {

    data object Success : PurchaseOutcome

    /** Already owned — Play answered `ITEM_ALREADY_OWNED` rather than starting a new purchase. */
    data object AlreadyOwned : PurchaseOutcome

    /** The user backed out of Play's purchase sheet. Not an error — no message should be shown. */
    data object Cancelled : PurchaseOutcome

    data class Failed(val debugMessage: String) : PurchaseOutcome
}

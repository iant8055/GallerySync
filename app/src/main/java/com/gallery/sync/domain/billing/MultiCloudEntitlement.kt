package com.gallery.sync.domain.billing

import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.domain.billing.MultiCloudTrial.isActive
import javax.inject.Inject

/**
 * Whether sending to a second cloud is allowed right now: `pro_unlock` bought, or the 30-day trial
 * running. The one question the upload path and the UI both ask.
 *
 * Purchase state itself still comes only from [BillingRepository] — CLAUDE.md's rule that it is the
 * single source of truth. The trial is not a purchase and is not folded into it; this class is where
 * the two are combined, and nothing else should combine them.
 */
class MultiCloudEntitlement @Inject constructor(
    private val billing: BillingRepository,
    private val settings: BackupSettings
) {

    suspend fun isEntitled(now: Long = System.currentTimeMillis()): Boolean =
        billing.isPurchased() || trialState(now).isActive

    suspend fun trialState(now: Long = System.currentTimeMillis()): MultiCloudTrial.State =
        MultiCloudTrial.stateOf(settings.current().multiCloudTrialStartedAtEpochMillis, now)

    /** Starts the trial if it never has been. Safe to call twice: the original start stands. */
    suspend fun startTrial(now: Long = System.currentTimeMillis()): MultiCloudTrial.State {
        settings.startMultiCloudTrial(now)
        return trialState(now)
    }
}

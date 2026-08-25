package com.gallery.sync.util

import android.content.Context
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the phone is plugged in.
 *
 * Wrapped rather than read inline so a ViewModel can be tested without a device, and so the one
 * place that answers this question is the same for the worker and the screen. Two readings that
 * could disagree would put the app in the position of saying it is waiting for charge while it
 * uploads, or the reverse.
 */
@Singleton
class ChargingState @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** False when the service is unavailable — the cautious answer, since it only delays a run. */
    fun isCharging(): Boolean =
        context.getSystemService(BatteryManager::class.java)?.isCharging ?: false
}

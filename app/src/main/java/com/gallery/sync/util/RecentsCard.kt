package com.gallery.sync.util

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes the app's card out of Recents while the first backup runs, and puts it back afterwards.
 *
 * ### The case this exists for
 *
 * A swipe out of Recents kills the process, and Android then treats the app as force-stopped and
 * stops dispatching its jobs until someone opens it again. Measured on the Moto G, 4 Sept 2026: five
 * minutes untouched after a Close uploaded 151 files, and the same five minutes after a swipe
 * uploaded none. A delayed start armed before a swipe never fires at all, because a pending delay
 * has no run in flight to hold up — which is why a foreground service does not fix this case either.
 *
 * No card means no swipe target. That is the whole mechanism: this does not protect the process, it
 * removes the gesture that kills it.
 *
 * ### Why it is bounded to the first backup
 *
 * The user is gated out of the app until the first backup and its optimising finish, so hiding the
 * card during that phase is a state that ends by itself. An app permanently missing from Recents is
 * worse than the problem being solved, so every path that leaves setup restores it, and
 * [MainActivity][com.gallery.sync.MainActivity] restores it on every launch as well — a crash mid-run
 * has nothing left running to clear the flag, and the next launch is the first moment anything can.
 *
 * ### Why the runtime API and not the manifest
 *
 * `android:excludeFromRecents` is permanent and applies to every launch. This is a state with a
 * beginning and an end, so it has to be one too.
 *
 * Reopening does not depend on the card either way: the launcher icon relaunches the activity, and
 * the tour resumes on the progress card re-attached to the live chain. Card geometry is not an
 * option — Recents cards are drawn by the launcher, and an app controls only label, icon and colour.
 */
@Singleton
class RecentsCard @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Removes the card, so there is nothing to swipe. */
    fun hide() = setExcluded(true)

    /** Puts the card back. Safe to call when it was never hidden. */
    fun show() = setExcluded(false)

    /**
     * Applies the flag to every task this app owns.
     *
     * All of them rather than the current one: the flag lives on the task record, so a task left
     * behind by a process that died mid-setup would otherwise keep the app missing from Recents with
     * nothing able to explain why.
     *
     * Failures are logged and swallowed. A task can disappear between listing and setting, and
     * neither a hidden card nor a visible one is worth taking the app down for.
     */
    private fun setExcluded(excluded: Boolean) {
        val tasks = context.getSystemService(ActivityManager::class.java)?.appTasks

        if (tasks.isNullOrEmpty()) {
            // Ordinary when no activity exists — a worker calling this has no task to change.
            Logger.d(TAG, "no app task to ${if (excluded) "hide" else "restore"}")
            return
        }

        var applied = 0
        tasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(excluded) }
                .onSuccess { applied++ }
                .onFailure { Logger.w(TAG, "could not set excludeFromRecents=$excluded: ${it.message}") }
        }

        // The call above is made every time and the log line is not.
        //
        // Walking the wizard emitted six identical "restored" lines, one per step, because the state
        // that drives this changes with the step. Guarding the *call* on a remembered value would be
        // the obvious tidy-up and would be wrong: task ids churn — #126, #127, #128 across three
        // relaunches on the Moto G, 5 Sept 2026 — so a remembered "already hidden" would skip a new
        // task record that genuinely needs hiding, leaving a swipeable card during a backup. That is
        // the one failure this class exists to prevent, and it would be silent.
        //
        // So the binder call stays unconditional, which is cheap and always correct, and only the
        // reporting is deduplicated.
        if (lastLogged != excluded) {
            lastLogged = excluded
            Logger.i(TAG, "recents card ${if (excluded) "hidden" else "restored"} on $applied task(s)")
        }
    }

    /** What was last written to the log, never consulted to decide whether to act. */
    private var lastLogged: Boolean? = null

    private companion object {
        const val TAG = "RecentsCard"
    }
}

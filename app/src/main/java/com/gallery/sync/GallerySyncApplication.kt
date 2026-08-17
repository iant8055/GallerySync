package com.gallery.sync

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Also supplies WorkManager's configuration, so workers can be constructor-injected.
 *
 * This requires WorkManager's automatic startup to be disabled in the manifest — otherwise it
 * initialises itself with the default factory before Hilt is ready, and [BackupWorker] fails to
 * construct at the moment it is needed.
 */
@HiltAndroidApp
class GallerySyncApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

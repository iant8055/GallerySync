package com.gallery.sync.data.remote.auth

import android.app.Activity
import com.gallery.sync.data.local.DriveListingStore
import com.gallery.sync.data.remote.cloud.CloudConnection
import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.domain.backup.BackupLocation
import javax.inject.Inject

/**
 * OneDrive as a [CloudConnection], over the MSAL sign-in that already exists.
 *
 * Listed beside the other clouds because the free tier is **one cloud, the user's own** (Ian,
 * 24 Sept 2026), and OneDrive is one of the choices rather than a requirement. It is still the only
 * cloud with Restore, Archive and Sync, and its uploads still go through their own richer path in the
 * engine — this is only how the screens connect and disconnect it.
 */
class OneDriveCloudConnection @Inject constructor(
    private val signIn: OneDriveSignIn,
    private val driveListingStore: DriveListingStore
) : CloudConnection {

    override val location = BackupLocation.ONEDRIVE

    override val kind = ConnectionKind.OAUTH

    override val isOfferedInThisBuild = true

    override suspend fun accountLabel(): String? = signIn.currentAccountName()

    override suspend fun signIn(activity: Activity): SignInResult = signIn.signIn(activity)

    override suspend fun signOut() {
        // A stored OneDrive listing is one account's library. It must not greet the next one.
        if (signIn.signOut()) driveListingStore.clear()
    }
}

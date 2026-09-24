package com.gallery.sync.data.remote.auth

import android.app.Activity
import com.gallery.sync.data.remote.cloud.CloudConnection
import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.domain.backup.BackupLocation
import javax.inject.Inject

/** Google Photos as a [CloudConnection], over the AppAuth sign-in that already exists. */
class GooglePhotosCloudConnection @Inject constructor(
    private val signIn: GooglePhotosSignIn
) : CloudConnection {

    override val location = BackupLocation.GOOGLE_PHOTOS

    override val kind = ConnectionKind.OAUTH

    override val isOfferedInThisBuild = true

    override suspend fun accountLabel(): String? = signIn.currentAccountName()

    override suspend fun signIn(activity: Activity): SignInResult = signIn.signIn(activity)

    override suspend fun signOut() {
        signIn.signOut()
    }
}

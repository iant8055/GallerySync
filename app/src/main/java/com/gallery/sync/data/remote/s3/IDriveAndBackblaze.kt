package com.gallery.sync.data.remote.s3

import com.gallery.sync.R
import com.gallery.sync.data.remote.cloud.EncryptedCloudSecretsStore
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.BackupLocation
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/** IDrive e2. The endpoint is per account (shown in the e2 console next to the bucket). */
@Singleton
class IDriveE2Cloud @Inject constructor(
    secrets: EncryptedCloudSecretsStore,
    s3: S3Client,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : S3CompatibleCloud(
    BackupLocation.IDRIVE_E2, R.string.cloud_hint_idrive_endpoint, R.string.cloud_hint_idrive_region, secrets, s3, dispatcher
)

/** Backblaze B2 through its S3-compatible API: endpoint `s3.<region>.backblazeb2.com`. */
@Singleton
class BackblazeB2Cloud @Inject constructor(
    secrets: EncryptedCloudSecretsStore,
    s3: S3Client,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : S3CompatibleCloud(
    BackupLocation.BACKBLAZE_B2, R.string.cloud_hint_b2_endpoint, R.string.cloud_hint_b2_region, secrets, s3, dispatcher
)

package com.gallery.sync.di

import com.gallery.sync.data.remote.auth.GooglePhotosCloudConnection
import com.gallery.sync.data.remote.auth.OneDriveCloudConnection
import com.gallery.sync.data.remote.cloud.CloudConnection
import com.gallery.sync.data.remote.cloud.CloudConnections
import com.gallery.sync.data.remote.cloud.CloudUploadClient
import com.gallery.sync.data.remote.cloud.DropboxCloud
import com.gallery.sync.data.remote.cloud.GoogleDriveCloud
import com.gallery.sync.data.remote.cloud.PCloudCloud
import com.gallery.sync.data.remote.s3.BackblazeB2Cloud
import com.gallery.sync.data.remote.s3.IDriveE2Cloud
import com.gallery.sync.data.repository.GooglePhotosCloudUploader
import com.gallery.sync.domain.repository.CloudDownloader
import com.gallery.sync.domain.repository.CloudDownloaders
import com.gallery.sync.domain.repository.CloudUploader
import com.gallery.sync.domain.repository.CloudUploaders
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The optional clouds. Adding a provider is one `@Binds @IntoSet` line per role (connection, uploader)
 * plus its adapter; nothing in the engine or the screens names a provider.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {

    @Binds @IntoSet
    abstract fun bindGooglePhotosUploader(impl: GooglePhotosCloudUploader): CloudUploader

    @Binds @IntoSet
    abstract fun bindOneDriveConnection(impl: OneDriveCloudConnection): CloudConnection

    @Binds @IntoSet
    abstract fun bindGooglePhotosConnection(impl: GooglePhotosCloudConnection): CloudConnection

    @Binds @IntoSet
    abstract fun bindGoogleDriveUploader(impl: GoogleDriveCloud): CloudUploader

    @Binds @IntoSet
    abstract fun bindGoogleDriveConnection(impl: GoogleDriveCloud): CloudConnection

    @Binds @IntoSet
    abstract fun bindGoogleDriveDownloader(impl: GoogleDriveCloud): CloudDownloader

    @Binds @IntoSet
    abstract fun bindDropboxUploader(impl: DropboxCloud): CloudUploader

    @Binds @IntoSet
    abstract fun bindDropboxConnection(impl: DropboxCloud): CloudConnection

    @Binds @IntoSet
    abstract fun bindDropboxDownloader(impl: DropboxCloud): CloudDownloader

    @Binds @IntoSet
    abstract fun bindPCloudUploader(impl: PCloudCloud): CloudUploader

    @Binds @IntoSet
    abstract fun bindPCloudConnection(impl: PCloudCloud): CloudConnection

    @Binds @IntoSet
    abstract fun bindIDriveUploader(impl: IDriveE2Cloud): CloudUploader

    @Binds @IntoSet
    abstract fun bindBackblazeUploader(impl: BackblazeB2Cloud): CloudUploader

    @Binds @IntoSet
    abstract fun bindIDriveConnection(impl: IDriveE2Cloud): CloudConnection

    @Binds @IntoSet
    abstract fun bindIDriveDownloader(impl: IDriveE2Cloud): CloudDownloader

    @Binds @IntoSet
    abstract fun bindBackblazeDownloader(impl: BackblazeB2Cloud): CloudDownloader

    @Binds @IntoSet
    abstract fun bindBackblazeConnection(impl: BackblazeB2Cloud): CloudConnection

    companion object {
        @Provides
        @Singleton
        fun provideCloudUploaders(all: @JvmSuppressWildcards Set<CloudUploader>): CloudUploaders =
            CloudUploaders(all)

        @Provides
        @Singleton
        fun provideCloudDownloaders(all: @JvmSuppressWildcards Set<CloudDownloader>): CloudDownloaders =
            CloudDownloaders(all)

        @Provides
        @Singleton
        fun provideCloudConnections(all: @JvmSuppressWildcards Set<CloudConnection>): CloudConnections =
            CloudConnections(all)

        /**
         * No logging interceptor, on purpose: see [CloudUploadClient]. No call timeout: a video takes
         * as long as it takes.
         */
        @Provides
        @Singleton
        @CloudUploadClient
        fun provideCloudUploadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

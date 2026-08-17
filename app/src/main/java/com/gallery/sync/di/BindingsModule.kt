package com.gallery.sync.di

import com.gallery.sync.data.remote.auth.MsalOneDriveSignIn
import com.gallery.sync.data.remote.auth.MsalOneDriveTokenProvider
import com.gallery.sync.data.remote.auth.OneDriveSignIn
import com.gallery.sync.data.remote.auth.OneDriveTokenProvider
import com.gallery.sync.data.remote.auth.StoredOneDriveTokenProvider
import com.gallery.sync.data.repository.OneDriveRepositoryImpl
import com.gallery.sync.domain.repository.OneDriveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds data-layer implementations to the interfaces the rest of the app depends on.
 *
 * Kept separate from [NetworkModule] because `@Binds` requires an abstract module while `@Provides`
 * for constructed types lives in an `object`.
 */
@Module
@InstallIn(SingletonComponent::class)
interface BindingsModule {

    @Binds
    @Singleton
    fun bindOneDriveRepository(impl: OneDriveRepositoryImpl): OneDriveRepository

    /**
     * The Azure app registration has landed, so this now binds the MSAL-backed provider.
     *
     * [StoredOneDriveTokenProvider] is deliberately kept rather than deleted: it is the only
     * implementation that unit tests can construct without an Android context, and it documents
     * the null-token contract that [MsalOneDriveTokenProvider] still honours.
     */
    @Binds
    @Singleton
    fun bindOneDriveTokenProvider(impl: MsalOneDriveTokenProvider): OneDriveTokenProvider

    @Binds
    @Singleton
    fun bindOneDriveSignIn(impl: MsalOneDriveSignIn): OneDriveSignIn
}

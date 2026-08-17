package com.gallery.sync.di

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
     * The single line that changes when the Azure app registration lands: swap
     * [StoredOneDriveTokenProvider] for a provider that can actually acquire a token.
     */
    @Binds
    @Singleton
    fun bindOneDriveTokenProvider(impl: StoredOneDriveTokenProvider): OneDriveTokenProvider
}

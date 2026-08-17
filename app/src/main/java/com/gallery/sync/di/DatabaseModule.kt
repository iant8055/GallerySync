package com.gallery.sync.di

import android.content.Context
import androidx.room.Room
import com.gallery.sync.data.local.GallerySyncDatabase
import com.gallery.sync.data.local.dao.MediaFolderDao
import com.gallery.sync.data.local.dao.MediaItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Note the deliberate absence of `fallbackToDestructiveMigration()`: it would silently drop the
     * user's index on a schema change. A schema change must ship a real migration instead.
     */
    @Provides
    @Singleton
    fun provideGallerySyncDatabase(
        @ApplicationContext context: Context
    ): GallerySyncDatabase = Room.databaseBuilder(
        context,
        GallerySyncDatabase::class.java,
        GallerySyncDatabase.DATABASE_NAME
    ).build()

    @Provides
    @Singleton
    fun provideMediaItemDao(database: GallerySyncDatabase): MediaItemDao = database.mediaItemDao()

    @Provides
    @Singleton
    fun provideMediaFolderDao(database: GallerySyncDatabase): MediaFolderDao =
        database.mediaFolderDao()
}

package com.gallery.sync.data.remote.cloud

import javax.inject.Qualifier

/**
 * Marks the OkHttp client the optional clouds upload through. Deliberately has **no logging
 * interceptor**: these calls carry credentials and whole file bodies, and neither belongs in a log.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudUploadClient

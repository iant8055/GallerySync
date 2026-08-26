package com.gallery.sync.di

import com.gallery.sync.BuildConfig
import com.gallery.sync.data.remote.onedrive.GraphApiService
import com.gallery.sync.data.remote.onedrive.GraphAuthInterceptor
import com.gallery.sync.data.remote.onedrive.GraphDownloadService
import com.gallery.sync.data.remote.onedrive.GraphUploadService
import com.gallery.sync.data.remote.onedrive.UploadChunkService
import javax.inject.Qualifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0/"
    private const val JSON_MEDIA_TYPE = "application/json"
    private const val TIMEOUT_SECONDS = 30L
    private const val UPLOAD_TIMEOUT_SECONDS = 120L

    /**
     * `ignoreUnknownKeys` is not optional: Microsoft adds fields to `driveItem` continuously, and
     * strict parsing would turn any such addition into a crash on the user's device.
     *
     * `encodeDefaults` is a **safety** setting, not a stylistic one. kotlinx.serialization omits
     * properties equal to their default, so `conflictBehavior = "rename"` — a default — would be
     * dropped from the upload-session body and Graph would fall back to its own conflict handling.
     * That risks overwriting a photo already in the user's drive, which CLAUDE.md forbids. Any
     * default that must actually reach the wire depends on this.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Full request/response logging in debug builds only.
     *
     * `BODY` in a release build would print bearer tokens into logcat, where any app holding
     * `READ_LOGS` — or anyone with an adb cable — could harvest them. `NONE` in release is a
     * security control, not a performance tweak.
     */
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Belt and braces: even in debug, never render the token.
            redactHeader("Authorization")
        }

    /**
     * Interceptor order is deliberate and security-relevant.
     *
     * Application interceptors run in the order they are added, so registering the logger *before*
     * [GraphAuthInterceptor] means the request the logger sees has no `Authorization` header on it
     * yet. Combined with `redactHeader` and the debug-only `BODY` level, the token cannot reach
     * logcat by any of the three paths.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: GraphAuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(GRAPH_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGraphApiService(retrofit: Retrofit): GraphApiService = retrofit.create()

    @Provides
    @Singleton
    fun provideGraphUploadService(retrofit: Retrofit): GraphUploadService = retrofit.create()

    /**
     * Client for resumable-upload chunk PUTs — **without** [GraphAuthInterceptor].
     *
     * The session URL Graph hands back is already authorised, and attaching a bearer token to it
     * is a documented cause of 401s on a perfectly valid session. That cannot be expressed as a
     * per-request option, so it needs a client of its own.
     *
     * The write timeout is far longer than the shared client's: a 5 MiB chunk on a weak mobile
     * connection legitimately takes longer than 30 seconds, and timing it out would restart work
     * that was progressing fine.
     */
    @Provides
    @Singleton
    @UploadChunkClient
    fun provideUploadChunkClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @UploadChunkClient
    fun provideUploadChunkRetrofit(
        @UploadChunkClient client: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        // Never used — every call supplies an absolute @Url — but Retrofit requires a base URL.
        .baseUrl(GRAPH_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideUploadChunkService(@UploadChunkClient retrofit: Retrofit): UploadChunkService =
        retrofit.create()

    /**
     * Logging for the download client, capped at `HEADERS`.
     *
     * `BODY` buffers the whole response into memory to print it, and `@Streaming` cannot stop it —
     * that annotation is Retrofit's, and this interceptor is OkHttp, one layer below. On a 2 GB
     * video the buffer reaches the 512 MiB heap ceiling and takes the process down; observed twice
     * on the Fold 4, 26 Aug 2026. `HEADERS` keeps the status, the URL and the timing, which is
     * everything a download is ever debugged from, and reads none of the body.
     */
    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

    /**
     * Client for file downloads. Authenticated like the shared client, but never body-logged.
     *
     * Interceptor order matters here for the same reason it does above: the logger is registered
     * before [GraphAuthInterceptor], so the request it sees carries no `Authorization` header yet.
     */
    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadClient(
        @DownloadClient loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: GraphAuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadRetrofit(
        @DownloadClient client: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(GRAPH_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGraphDownloadService(@DownloadClient retrofit: Retrofit): GraphDownloadService =
        retrofit.create()
}

/** Marks the unauthenticated client and Retrofit used for pre-authorised upload-session URLs. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadChunkClient

/** Marks the client and Retrofit used for file downloads, which are never body-logged. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadClient

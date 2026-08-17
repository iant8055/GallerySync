package com.gallery.sync.di

import com.gallery.sync.BuildConfig
import com.gallery.sync.data.remote.onedrive.GraphApiService
import com.gallery.sync.data.remote.onedrive.GraphAuthInterceptor
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

    /**
     * `ignoreUnknownKeys` is not optional: Microsoft adds fields to `driveItem` continuously, and
     * strict parsing would turn any such addition into a crash on the user's device.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
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
}

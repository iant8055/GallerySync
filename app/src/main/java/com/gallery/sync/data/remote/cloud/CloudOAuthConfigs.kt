package com.gallery.sync.data.remote.cloud

import android.content.Context
import com.gallery.sync.R
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The app registration one OAuth provider needs: an id from that provider's developer console. */
data class OAuthClient(val clientId: String, val redirectUri: String)

@Serializable
private data class Entry(
    val client_id: String = "",
    val redirect_uri: String = ""
)

/**
 * The client ids for Google Drive, Dropbox and pCloud, read from `res/raw/cloud_oauth_config.json`.
 *
 * A **public client id, never a secret** — none of these flows uses a client secret, which is why the
 * file can be committed. Each is blank until the app is registered with that provider (TASK-026);
 * a provider whose id is blank is simply not offered, because a Connect button that cannot succeed is
 * worse than no button. Filling an id in is the whole of turning a provider on.
 */
@Singleton
class CloudOAuthConfigs @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val entries: Map<String, Entry> by lazy {
        try {
            val text = context.resources.openRawResource(R.raw.cloud_oauth_config)
                .bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<Map<String, Entry>>(text)
        } catch (e: Exception) {
            Logger.w(TAG, "cloud_oauth_config.json could not be read; no OAuth cloud will be offered")
            emptyMap()
        }
    }

    /** The registration for [providerId], or null when it has not been filled in. */
    fun clientFor(providerId: String): OAuthClient? {
        val entry = entries[providerId] ?: return null
        if (entry.client_id.isBlank() || entry.redirect_uri.isBlank()) return null
        return OAuthClient(entry.client_id.trim(), entry.redirect_uri.trim())
    }

    private companion object {
        const val TAG = "CloudOAuthConfigs"
    }
}

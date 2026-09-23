package com.gallery.sync.data.remote.auth

import android.content.Context
import com.gallery.sync.R
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed contents of `res/raw/google_photos_config.json`.
 *
 * Unlike MSAL, which reads `msal_config.json` itself, AppAuth has no loader for this shape — it is
 * Google Cloud Console's own client-config layout, not AppAuth's. Parsed once and cached; every
 * field here is what Ian registered in Google Cloud Console, see TASK-026.
 */
@Singleton
class GoogleAuthConfig @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    val value: Parsed by lazy { parse() }

    data class Parsed(
        val clientId: String,
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val redirectUri: String,
        val scopes: List<String>
    )

    private fun parse(): Parsed {
        val json = context.resources.openRawResource(R.raw.google_photos_config)
            .bufferedReader()
            .use { it.readText() }
        val obj = JSONObject(json)
        val scopesArray = obj.getJSONArray("scopes")
        val scopes = (0 until scopesArray.length()).map { scopesArray.getString(it) }
        return Parsed(
            clientId = obj.getString("client_id"),
            authorizationEndpoint = obj.getString("auth_uri"),
            tokenEndpoint = obj.getString("token_uri"),
            redirectUri = obj.getString("redirect_uri"),
            scopes = scopes
        )
    }
}

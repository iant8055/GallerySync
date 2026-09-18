package com.gallery.sync.ui.settings

import androidx.annotation.StringRes
import com.gallery.sync.R
import java.net.URI

/**
 * The two Play-listing documents in `docs/`, shown inside the app from the cards at the bottom of
 * Settings.
 *
 * They are **read live from GitHub Pages**, served from the `main` branch. What a user sees is
 * whatever is published there, not what is in the working tree, so a change to `docs/` reaches the
 * app only once it is pushed.
 */
enum class SupportPage(val url: String, @param:StringRes val title: Int) {
    PRIVACY_POLICY(
        url = "${SupportLinks.SITE}/privacy-policy.html",
        title = R.string.settings_privacy_policy
    ),
    DELETE_ACCOUNT(
        url = "${SupportLinks.SITE}/delete-account.html",
        title = R.string.settings_delete_account
    )
}

object SupportLinks {
    const val HOST = "iant8055.github.io"
    const val SITE = "https://$HOST/GallerySync"
    const val CONTACT_EMAIL = "IanDev@Currently.com"

    /**
     * Whether [url] may load inside the in-app viewer.
     *
     * Only our own pages, over HTTPS. Everything else — the Microsoft consent page, the privacy
     * statement, GitHub — hands off to the phone's browser, so the viewer is never a general
     * browser and a link on a page cannot walk somebody to a site of its choosing inside the app.
     * The host is compared exactly, so `iant8055.github.io.example.com` does not pass.
     */
    fun staysInApp(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == HOST && uri.path?.startsWith("/GallerySync/") == true
    }.getOrDefault(false)
}

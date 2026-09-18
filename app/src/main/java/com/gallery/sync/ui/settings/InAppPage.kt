package com.gallery.sync.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.gallery.sync.R
import com.gallery.sync.util.Logger

/**
 * The frame every in-app page shares: full screen, a title on the left, Close on the right.
 *
 * [onDismissRequest] is what Back does, [onClose] what the Close button does — they differ for the
 * web viewer, where Back steps back through pages before it closes anything.
 */
@Composable
private fun FullScreenPageDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.systemBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.settings_page_close))
                    }
                }
                content()
            }
        }
    }
}

/**
 * Shows one of the published support pages full-screen, read from GitHub Pages.
 *
 * A WebView is the right tool because the pages are real HTML — tables, links, a dark palette —
 * and turning them into Compose text would lose the tables and drift from what is published.
 * It is locked down accordingly: JavaScript off (the pages need none), no file or content access,
 * no mixed content, and navigation confined to our own pages by [SupportLinks.staysInApp].
 * Anything else opens in the phone's browser.
 *
 * Back steps back through pages the viewer has visited (the two pages link to each other) and
 * closes it from the first one. A failed load says so and offers the browser instead of leaving a
 * blank sheet.
 */
@Composable
fun InAppPageDialog(page: SupportPage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // The theme's own surface colour, so the moment before the page paints is not a white flash.
    val background = MaterialTheme.colorScheme.background.toArgb()

    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    FullScreenPageDialog(
        title = stringResource(page.title),
        onDismissRequest = {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else onDismiss()
        },
        onClose = onDismiss
    ) {
        if (loading && !failed) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        setBackgroundColor(background)
                        settings.apply {
                            javaScriptEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                if (SupportLinks.staysInApp(url)) return false
                                openLink(context, Intent(Intent.ACTION_VIEW, url.toUri()))
                                return true
                            }

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                loading = true
                                failed = false
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) failed = true
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse
                            ) {
                                if (request.isForMainFrame) failed = true
                            }
                        }
                        loadUrl(page.url)
                        webView = this
                    }
                }
            )

            if (failed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_page_load_failed),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(
                        onClick = {
                            openLink(context, Intent(Intent.ACTION_VIEW, page.url.toUri()))
                        }
                    ) {
                        Text(stringResource(R.string.settings_page_open_browser))
                    }
                }
            }
        }
    }
}

/**
 * The Contact Info page: the address, selectable, and a button that copies it.
 *
 * Built in the app rather than read from the web, because it is one address and needs no publishing.
 * It deliberately opens no mail app — Ian's ruling — so copying is the one thing it offers.
 */
@Composable
fun ContactDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    FullScreenPageDialog(
        title = stringResource(R.string.settings_contact),
        onDismissRequest = onDismiss,
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.contact_page_intro),
                style = MaterialTheme.typography.bodyLarge
            )
            SelectionContainer {
                Text(
                    text = SupportLinks.CONTACT_EMAIL,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            OutlinedButton(
                onClick = {
                    context.getSystemService<ClipboardManager>()?.setPrimaryClip(
                        ClipData.newPlainText(
                            context.getString(R.string.contact_page_clip_label),
                            SupportLinks.CONTACT_EMAIL
                        )
                    )
                    copied = true
                }
            ) {
                Text(
                    stringResource(
                        if (copied) R.string.contact_page_copied else R.string.contact_page_copy
                    )
                )
            }
        }
    }
}

/** Starts [intent], and does nothing visible if the phone has no app to handle it. */
internal fun openLink(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure { Logger.w("InAppPage", "no app could open ${intent.action}") }
}

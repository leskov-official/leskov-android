package com.example.web

import android.net.Uri
import android.webkit.WebView
import com.example.model.LeskovConstants
import org.json.JSONObject

/** Keeps the website's live player and its DOM in the same Chromium document. */
object WebNavigation {
    fun isTrustedUri(uri: Uri?): Boolean = uri != null &&
        uri.scheme.equals("https", true) && uri.host.equals(LeskovConstants.HOST, true) &&
        uri.userInfo == null && uri.port in listOf(-1, 443)

    fun navigate(page: WebView, url: String, onCallActive: () -> Unit = {}) {
        if (!isTrustedUri(runCatching { Uri.parse(url) }.getOrNull())) return
        if (!isTrustedUri(runCatching { Uri.parse(page.url.orEmpty()) }.getOrNull())) {
            page.loadUrl(url)
            return
        }
        page.evaluateJavascript(
            "window.__leskovAppShell ? window.__leskovAppShell.navigate(${JSONObject.quote(url)}) : 'hard';"
        ) { result ->
            when (result) {
                "\"soft\"" -> Unit // The site publishes history and completion itself.
                "\"call_active\"" -> onCallActive()
                else -> page.loadUrl(url)
            }
        }
    }
}

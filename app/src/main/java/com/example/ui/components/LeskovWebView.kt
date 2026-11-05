package com.example.ui.components

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.media.LeskovMediaService
import com.example.media.LeskovAudioHandoff
import com.example.media.LeskovNativeAudioService
import com.example.push.FcmTokenManager
import com.example.model.LeskovConstants
import com.example.viewmodel.PageFailure
import com.example.viewmodel.LeskovViewModel

// Audio state messages are received via androidx.webkit WebMessageListener below.
// Unlike addJavascriptInterface, it restricts the bridge to the trusted first-party origin
// and checks isMainFrame, so third-party frames cannot forge playback events.

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeskovWebView(
    viewModel: LeskovViewModel,
    onFileChooserRequested: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    onPermissionRequested: (PermissionRequest) -> Unit,
    onNativePushRequested: () -> Unit,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {},
    fullscreenVideoHost: FullscreenVideoHost? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val audioBridgeScript = remember { context.assets.open("leskov-player-bridge.js").bufferedReader().use { it.readText() } }
    val mobileStyle = remember { context.assets.open("leskov-mobile.css").bufferedReader().use { it.readText() } }
    val layoutScript = remember(mobileStyle) {
        """
        (function(){
          if(location.origin!=='https://lesv.online' || window.top!==window) return;
          function install(){
            if(!document.documentElement || !document.head) return false;
            document.documentElement.setAttribute('data-leskov-app','');
            var viewport=document.querySelector('meta[name="viewport"]');
            if(!viewport){viewport=document.createElement('meta');viewport.name='viewport';document.head.appendChild(viewport);}
            viewport.content='width=device-width, initial-scale=1, viewport-fit=cover';
            if(!document.getElementById('leskov-app-design')){
              var style=document.createElement('style');style.id='leskov-app-design';
              style.textContent=${JSONObject.quote(mobileStyle)};document.head.appendChild(style);
            }
            return true;
          }
          if(!install()){var observer=new MutationObserver(function(){if(install()) observer.disconnect();});observer.observe(document,{childList:true,subtree:true});}
        })();
        """.trimIndent()
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF090B17.toInt())
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = false
                textZoom = (configuration.fontScale * 100).toInt().coerceIn(75, 200)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = userAgentString + LeskovConstants.USER_AGENT_SUFFIX
            }

            // Install before site scripts so an early Premium autoplay cannot be missed.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, audioBridgeScript + "\n" + layoutScript,
                    setOf("https://${LeskovConstants.HOST}"))
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this@apply, false)

            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            // A secure, origin-scoped web message bridge; never expose a raw
            // @JavascriptInterface to third-party iframes on this social site.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    this, "LeskovAudioBridge", setOf("https://${LeskovConstants.HOST}")
                ) { _, message, origin, isMainFrame, _ ->
                    if (!isMainFrame || origin.scheme != "https" || origin.host != LeskovConstants.HOST) return@addWebMessageListener
                    try {
                        val payload = JSONObject(message.data ?: return@addWebMessageListener)
                        if (payload.optString("type") == "web_start") {
                            // A NEW track was started in the web UI. Stop prior Media3 playback
                            // before Chromium obtains audio focus to prevent the Play→Pause race.
                            LeskovNativeAudioService.stop(context)
                            return@addWebMessageListener
                        }
                        if (LeskovNativeAudioService.isActive() && !payload.optBoolean("playing", false)) return@addWebMessageListener
                        val playing = payload.optBoolean("playing", false)
                        val title = payload.optString("title", "LESKOV").take(160)
                        val artist = payload.optString("artist", "Igor Leskov").take(160)
                        if (playing && LeskovNativeAudioService.isActive()) {
                            // A new web track supersedes the previous native track.
                            LeskovNativeAudioService.stop(context)
                        }
                        val artwork = payload.optString("artwork", "").take(1500)
                        LeskovMediaService.updatePlayback(
                            context, playing, title, artist, artwork,
                            payload.optLong("positionMs", 0L),
                            payload.optLong("durationMs", 0L),
                            payload.optBoolean("hasPrevious", false),
                            payload.optBoolean("hasNext", false)
                        )
                    } catch (e: Exception) {
                        Log.w("LeskovWebView", "Invalid audio state message", e)
                    }
                }
            } else {
                Log.w("LeskovWebView", "WebView too old for secure audio bridge; native media notification disabled")
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    this, "LeskovFcmBridge", setOf("https://${LeskovConstants.HOST}")
                ) { _, message, origin, isMainFrame, _ ->
                    if (isMainFrame && origin.scheme == "https" && origin.host == LeskovConstants.HOST) {
                        message.data?.let { FcmTokenManager.handleResponse(it) }
                    }
                }
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    this, "LeskovNativePush", setOf("https://${LeskovConstants.HOST}")
                ) { _, message, origin, isMainFrame, _ ->
                    if (isMainFrame && origin.scheme == "https" && origin.host == LeskovConstants.HOST &&
                        message.data == "request") {
                        this@apply.post { onNativePushRequested() }
                    }
                }
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    this, "LeskovNativePlayback", setOf("https://${LeskovConstants.HOST}")
                ) { _, message, origin, isMainFrame, _ ->
                    if (!isMainFrame || origin.scheme != "https" || origin.host != LeskovConstants.HOST) return@addWebMessageListener
                    when (message.data) {
                        "toggle" -> this@apply.post { LeskovNativeAudioService.toggle(context) }
                        "stop" -> this@apply.post { LeskovNativeAudioService.stop(context) }
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    url?.let { viewModel.onPageStarted(it) }
                    // Catch the server logout even if it immediately redirects to auth.php.
                    if (url?.contains("auth.php?logout=") == true) viewModel.onUrlChanged(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (isInternalUri(uri)) {
                        // A user-clicked GET link creates a new PHP document: transfer the
                        // outgoing first-party audio stream to Media3 before it disappears.
                        if (request.isForMainFrame && request.method == "GET"
                            && uri.toString() != view?.url) {
                            view?.let { current ->
                                LeskovAudioHandoff.beforeLeaving(current) { current.loadUrl(uri.toString()) }
                                return true
                            }
                        }
                        return false
                    }

                    // External URLs open safely via system intent
                    if (request.isForMainFrame) {
                        openExternalUri(context, uri)
                    }
                    return true
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    if (view != null && isInternalUri(Uri.parse(url.orEmpty()))) {
                        view.evaluateJavascript(layoutScript, null)
                        view.evaluateJavascript(audioBridgeScript, null)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()

                    url?.let {
                        Log.d("LeskovNavigation", "Loaded PHP route: ${Uri.parse(it).path}")
                        viewModel.onUrlChanged(it)
                    }
                    view?.takeIf { isInternalUri(Uri.parse(url.orEmpty())) && !viewModel.uiState.value.isOffline }?.let {
                        viewModel.setCanGoBack(it.canGoBack())
                        applyMobileAppAdaptations(it, viewModel)
                        publishNativeSessionToPage(it)
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        viewModel.onPageFailed(PageFailure.CONNECTION)
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                    if (request?.isForMainFrame == true && (response?.statusCode ?: 0) >= 400) {
                        viewModel.onPageFailed(PageFailure.SERVER)
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.e("LeskovWebView", "Chromium renderer lost; crashed=${detail?.didCrash()}; restarting WebView host")
                    // Android requires handling this explicitly. Otherwise a renderer death
                    // terminates the entire application and Samsung recommends clearing cache.
                    val activity = context as? Activity
                    // Remove the dead renderer from the hierarchy as Android requires.
                    runCatching { (view?.parent as? ViewGroup)?.removeView(view) }
                    runCatching { view?.destroy() }
                    Handler(Looper.getMainLooper()).post {
                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            activity.recreate()
                        }
                    }
                    return true
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    // Strictly cancel untrusted SSL certificates to safeguard credentials
                    handler?.cancel()
                    viewModel.onPageFailed(PageFailure.SECURITY)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null && fullscreenVideoHost != null) {
                        fullscreenVideoHost.show(view, callback)
                    } else callback?.onCustomViewHidden()
                }

                override fun onHideCustomView() {
                    fullscreenVideoHost?.hide()
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    viewModel.onProgressChanged(newProgress)
                    view?.let {
                        viewModel.setCanGoBack(it.canGoBack())
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null || fileChooserParams == null) return false
                    return onFileChooserRequested(filePathCallback, fileChooserParams)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val origin = request.origin
                    if (origin.scheme != "https" || origin.host != LeskovConstants.HOST) {
                        request.deny()
                        return
                    }
                    onPermissionRequested(request)
                }


            }

            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                handleDownload(context, url, userAgent, contentDisposition, mimetype)
            })

            onWebViewCreated(this)
        }
    }

    // Publish the current Media3 handoff state to whichever PHP page is visible.
    // The website can listen for "leskov:native-playback" to keep its own controls
    // in sync without spawning a second player or adding a bottom mini-player.
    val nativeHandoff by LeskovNativeAudioService.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(nativeHandoff.active, nativeHandoff.playing, nativeHandoff.title) {
        webView.post {
            val current = webView.url.orEmpty()
            if (isInternalUri(Uri.parse(current))) {
                publishNativeSessionToPage(webView)
            }
        }
    }

    DisposableEffect(webView) {
        // Forward media controls (play/pause from notification) to web player
        LeskovMediaService.onMediaActionRequested = { action ->
            webView.post {
                if (isInternalUri(Uri.parse(webView.url.orEmpty()))) {
                    webView.evaluateJavascript(
                        "window.__leskovAndroidAudio && window.__leskovAndroidAudio.command(${JSONObject.quote(action)});", null
                    )
                }
            }
        }

        onDispose {
            fullscreenVideoHost?.hide()
            LeskovMediaService.onMediaActionRequested = null
            CookieManager.getInstance().flush()
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
        update = { it.settings.textZoom = (configuration.fontScale * 100).toInt().coerceIn(75, 200) }
    )
}

/**
 * Injects scoped CSS/JS to detect language, session status, handle audio bridge, fix profile, and adapt calls UI
 */
private fun applyMobileAppAdaptations(webView: WebView, viewModel: LeskovViewModel) {
    // 1. Language Detection from HTML attributes and cookies
    val jsLangCheck = """
        (function() {
            try {
                var lang = document.documentElement.getAttribute('lang') || (document.body ? document.body.getAttribute('data-lang') : null);
                if (!lang) {
                    var m = document.cookie.match(/(?:^|;\s*)leskov_lang=([a-z]{2})/);
                    if (m) lang = m[1];
                }
                return lang || 'ru';
            } catch(e) { return 'ru'; }
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsLangCheck) { result ->
        val lang = result?.replace("\"", "")?.trim()?.lowercase()
        if (!lang.isNullOrBlank()) {
            viewModel.onLanguageDetected(lang)
        }
    }

    // 2. Session Detection from data-current-user-id
    val jsSessionCheck = """
        (function() {
            try {
                var uidAttr = document.body ? document.body.getAttribute('data-current-user-id') : null;
                if (uidAttr !== null && uidAttr !== undefined) {
                    var parsed = parseInt(uidAttr, 10);
                    if (!isNaN(parsed)) return parsed;
                }
            } catch(e) {}
            return -1;
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsSessionCheck) { result ->
        val userId = result?.replace("\"", "")?.toIntOrNull() ?: -1
        if (userId >= 0) {
            viewModel.onUserSessionDetected(userId)
        }
    }

    // One bridge for the site's Premium manager, DOM audio and detached audio instances.
    val jsAudioBridgeHook = webView.context.assets.open("leskov-player-bridge.js").bufferedReader().use { it.readText() }
    webView.evaluateJavascript(jsAudioBridgeHook, null)

    // 4. Calls: do not patch getUserMedia or stop all tracks from an Android timer.
    // The previous injected monitor repeatedly stopped ALL WebRTC tracks while the
    // site's [data-call-active] panel was hidden, including during ringing/connecting.
    // Server scripts/calls.js owns acceptance, rejection and media stream lifecycle.

    // 5. Scoped Layout & Profile CSS (Full profile visibility, responsive sections, 84px bottom spacing)
    val jsScopedLayout = """
        (function() {
            try {
                var path = window.location.pathname;
                var styleId = 'leskov-mobile-scoped-css';
                var existing = document.getElementById(styleId);
                var css = '';
                var mobileViewport = document.querySelector('meta[name="viewport"]');
                if (!mobileViewport) {
                    mobileViewport = document.createElement('meta');
                    mobileViewport.name = 'viewport';
                    document.head.appendChild(mobileViewport);
                }
                mobileViewport.content = 'width=device-width, initial-scale=1, viewport-fit=cover';


                // CSS pixels stay aligned with touch coordinates and the site's seek slider.
                css += '@media (max-width: 850px) { ';
                // Never zoom the root: it breaks viewport coordinates and fixed modals.
                css += 'html { -webkit-text-size-adjust: 100% !important; } ';
                css += 'html, body { width: 100% !important; max-width: 100% !important; min-width: 0 !important; overflow-x: hidden !important; } ';
                css += 'h1 { font-size: clamp(25px, 7vw, 34px) !important; line-height: 1.16 !important; overflow-wrap: break-word !important; } ';
                css += 'h2 { font-size: clamp(21px, 5.5vw, 28px) !important; line-height: 1.2 !important; overflow-wrap: break-word !important; } ';
                css += 'h3 { font-size: clamp(18px, 4.7vw, 23px) !important; } ';
                css += 'input, select, textarea, button { max-width: 100% !important; } ';
                css += 'input[type=file] { display: block; width: 100% !important; min-width: 0 !important; font-size: 14px !important; } ';
                css += 'input[type=file]::file-selector-button { font-size: 14px !important; padding: 8px 10px !important; } ';
                css += 'img, video, canvas { max-width: 100% !important; } ';
                css += '.feed-container, .profile-container, .profile-main, .surface-card { min-width: 0 !important; max-width: 100% !important; } ';
                css += '[role=\"dialog\"], dialog[open], .notification-modal, .notifications-modal, .notifications-dialog, .notification-settings-modal { max-width: min(94vw, 460px) !important; max-height: min(88dvh, 760px) !important; overflow-y: auto !important; overscroll-behavior: contain !important; box-sizing: border-box !important; } ';
                css += '.notification-modal button, .notifications-modal button, .notification-settings-modal button { font-size: 14px !important; max-width: 100% !important; white-space: normal !important; } ';
                css += '.notification-modal .modal-content, .notifications-modal .modal-content, .notification-settings-modal .modal-content { max-width: 100% !important; padding: 12px !important; } ';
                css += 'input, textarea, select { font-size: max(16px, 1em) !important; box-sizing: border-box !important; } ';
                css += 'img, iframe, video, table, pre { max-width: 100% !important; } ';
                css += '.card, .surface-card, .profile-layout, .profile-main, .feed-container { box-sizing: border-box !important; } ';
                css += '} ';
                css += 'body { padding-bottom: max(16px, env(safe-area-inset-bottom)) !important; } ';

                // Scoped Feed adjustments
                if (path.indexOf('feed.php') !== -1) {
                    css += '.global-feed-page .profile-sidebar { display: none !important; } ';
                    css += '.global-feed-page .profile-layout { display: block !important; } ';
                    css += '#feed, .feed-container { width: 100% !important; max-width: 100% !important; margin: 0 auto !important; } ';
                }

                // Scoped Profile adjustments: RESTORE sidebar and topbar, make section navigation responsive
                if (path.indexOf('profile.php') !== -1) {
                    css += '.profile-sidebar, .profile-topbar { display: block !important; visibility: visible !important; opacity: 1 !important; } ';
                    css += '.profile-nav, .profile-tabs, .tabs, .nav-tabs { display: flex !important; flex-wrap: wrap !important; gap: 8px !important; overflow-x: auto !important; -webkit-overflow-scrolling: touch !important; padding: 6px 0 !important; } ';
                    css += '.profile-nav a, .profile-tabs a, .nav-tabs a { white-space: nowrap !important; } ';
                    css += '@media (max-width: 768px) { ';
                    css += '  .profile-layout, .profile-container { display: flex !important; flex-direction: column !important; width: 100% !important; } ';
                    css += '  .profile-sidebar { width: 100% !important; max-width: 100% !important; margin-bottom: 16px !important; } ';
                    css += '  .profile-main { width: 100% !important; max-width: 100% !important; } ';
                    css += '} ';
                }

                // Calls overlay styling
                css += '[data-call-incoming]:not([hidden]) { position: fixed !important; z-index: 2147483000 !important; } ';
                css += '[data-call-active].is-open:not([hidden]) { position: fixed !important; z-index: 2147482999 !important; } ';

                if (!existing) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.textContent = css;
                    document.head.appendChild(style);
                } else {
                    existing.textContent = css;
                }

                // The website's browser-Push API cannot work inside Android WebView.
                // Keep the site's other notification preferences, but send its native
                // "enable Push" button through an origin-checked Android bridge.
                if (!window.__leskovNativePushControls) {
                    window.__leskovNativePushControls = true;
                    document.addEventListener('click', function(ev) {
                        var button = ev.target && ev.target.closest && ev.target.closest('button');
                        if (!button || !/включить\s*push|enable\s*push|lülita\s*push/i.test((button.textContent||'').trim())) return;
                        if (window.LeskovNativePush) {
                            ev.preventDefault(); ev.stopImmediatePropagation();
                            window.LeskovNativePush.postMessage('request');
                        }
                    }, true);
                    var notice = function() {
                        var nodes = document.querySelectorAll('p, span, small, div');
                        for (var i = 0; i < nodes.length; i++) {
                            var n = nodes[i];
                            if (n.childElementCount === 0 && /этот браузер не поддерживает push|this browser does not support push/i.test(n.textContent || '')) {
                                n.textContent = 'В Android-приложении Push включаются через «Ещё» → «Уведомления». Для доставки требуется подключённый Firebase.';
                            }
                        }
                    };
                    notice();
                    // Feed and notifications can mutate hundreds of DOM nodes per second.
                    // Debounce this compatibility check instead of scanning the whole page
                    // for every mutation; the old observer could stall Chromium.
                    var noticePending = false;
                    var observer = new MutationObserver(function(){
                        if (noticePending) return;
                        noticePending = true;
                        setTimeout(function(){ noticePending = false; notice(); }, 300);
                    });
                    if (document.body) observer.observe(document.body, {childList:true, subtree:true});
                }

                // If on auth.php and hash is #register, ensure registration tab is active
                if (path.indexOf('auth.php') !== -1 && window.location.hash === '#register') {
                    var regTabBtn = document.querySelector('[data-auth-tab="register"]');
                    if (regTabBtn && regTabBtn.getAttribute('aria-selected') !== 'true') {
                        regTabBtn.click();
                    }
                }

            } catch(e) {}
        })();
    """.trimIndent()
    webView.evaluateJavascript(jsScopedLayout, null)
}


/** Refreshes the website's own player state after every PHP navigation. No second native
 * mini-player is mounted in Compose. The small status is inserted ONLY inside an existing
 * website music player, so the user always knows Media3 owns the active stream. */
private fun publishNativeSessionToPage(webView: WebView) {
    if (!isInternalUri(Uri.parse(webView.url.orEmpty()))) return
    val native = LeskovNativeAudioService.state.value
    val json = JSONObject()
        .put("active", native.active)
        .put("playing", native.playing)
        .put("title", native.title)
        .put("artist", native.artist)
        .toString()
    val js = """
        (function(){
          try {
            var state = $json;
            window.__leskovNativePlayback = state;
            window.dispatchEvent(new CustomEvent('leskov:native-playback', {detail:state}));
            if (window.__leskovAndroidAudio) window.__leskovAndroidAudio.renderNative();
          }catch(e){}
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

private fun isInternalUri(uri: Uri?): Boolean {
    if (uri == null) return false
    val scheme = uri.scheme ?: return false
    val host = uri.host ?: return false
    return "https".equals(scheme, ignoreCase = true) &&
            host.equals(LeskovConstants.HOST, ignoreCase = true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)
}

private fun openExternalUri(context: Context, uri: Uri) {
    val scheme = uri.scheme?.lowercase() ?: return
    if (scheme !in setOf("http", "https", "mailto", "tel", "sms", "geo", "market")) return

    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не удалось открыть внешнюю ссылку", Toast.LENGTH_SHORT).show()
    }
}

private fun handleDownload(
    context: Context,
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimetype: String
) {
    val uri = try {
        Uri.parse(url)
    } catch (_: Exception) {
        null
    } ?: return

    if (!isInternalUri(uri)) {
        openExternalUri(context, uri)
        return
    }

    try {
        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val cookie = CookieManager.getInstance().getCookie(url)
        val request = DownloadManager.Request(uri).apply {
            setMimeType(mimetype)
            cookie?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            setTitle(filename)
            setDescription("LESKOV: Загрузка файла")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                filename.substringAfterLast('/').substringAfterLast('\\').take(120))
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        downloadManager?.enqueue(request)
        Toast.makeText(context, "Загрузка началась: $filename", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось загрузить файл", Toast.LENGTH_SHORT).show()
    }
}

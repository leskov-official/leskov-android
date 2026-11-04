package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.media.LeskovAudioHandoff
import com.example.media.LeskovNativeAudioService
import com.example.model.AppLanguage
import com.example.model.LeskovStrings
import com.example.model.LeskovConstants
import com.example.ui.components.LeskovAuthGate
import com.example.ui.components.LeskovBottomBar
import com.example.ui.components.LeskovDeleteCookiesDialog
import com.example.ui.components.LeskovMoreBottomSheet
import com.example.ui.components.LeskovOfflineView
import com.example.ui.components.LeskovWebView
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovBackground
import com.example.ui.theme.LeskovPrimary
import com.example.ui.theme.LeskovSurface
import com.example.ui.theme.LeskovSurfaceVariant
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LeskovViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LeskovViewModel by viewModels()
    private var webViewRef: WebView? = null
    private var navigationSerial = 0

    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    private fun navigatePreservingMusic(url: String) {
        val page = webViewRef ?: return
        val requestId = ++navigationSerial
        LeskovAudioHandoff.beforeLeaving(page) {
            if (requestId == navigationSerial && webViewRef === page) page.loadUrl(url)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home / app switcher: move an eligible playing HTTPS stream into the
        // independent native Media3 session so screen lock does not kill it.
        webViewRef?.let { LeskovAudioHandoff.beforeLeaving(it) { } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install backward-compatible Android 12+ Splash Screen with #FFFFFF background & original logo
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialUrl = extractInternalUrl(intent) ?: LeskovConstants.buildUrl(LeskovConstants.HOME_PATH, AppLanguage.RU)

        setContent {
            MyApplicationTheme {
                LeskovApp(
                    viewModel = viewModel,
                    initialUrl = initialUrl,
                    onSetWebView = { webView ->
                        webViewRef = webView
                        webView.loadUrl(initialUrl)
                    },
                    getWebView = { webViewRef },
                    onOpenFileChooser = { callback, params ->
                        pendingFilePathCallback?.onReceiveValue(null)
                        pendingFilePathCallback = callback
                        try {
                            val intent = params.createIntent()
                            fileChooserLauncher.launch(intent)
                            true
                        } catch (_: Exception) {
                            pendingFilePathCallback?.onReceiveValue(null)
                            pendingFilePathCallback = null
                            Toast.makeText(this@MainActivity, "Выбор файлов недоступен", Toast.LENGTH_SHORT).show()
                            false
                        }
                    },
                    onNavigate = { url -> navigatePreservingMusic(url) },
                    onRequestMediaPermission = { request ->
                        // Only one origin-owned WebRTC request may be active at a time.
                        pendingPermissionRequest?.deny()
                        pendingPermissionRequest = request
                        val resources = request.resources
                        var needCamera = false
                        var needMic = false
                        for (r in resources) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE == r) needCamera = true
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE == r) needMic = true
                        }

                        // Never grant RESOURCE_PROTECTED_MEDIA_ID or unknown resources blindly.
                        if (resources.isEmpty() || resources.any {
                                it != PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                                it != PermissionRequest.RESOURCE_AUDIO_CAPTURE
                            }) {
                            request.deny()
                            pendingPermissionRequest = null
                            return@LeskovApp
                        }

                        val permissionsToAsk = mutableListOf<String>()
                        if (needCamera && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToAsk.add(Manifest.permission.CAMERA)
                        }
                        if (needMic && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToAsk.add(Manifest.permission.RECORD_AUDIO)
                        }

                        if (permissionsToAsk.isEmpty()) {
                            if (isTrustedCurrentPage()) request.grant(resources) else request.deny()
                            pendingPermissionRequest = null
                        } else {
                            mediaPermissionsLauncher.launch(permissionsToAsk.toTypedArray())
                        }
                    }
                )
            }
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (pendingFilePathCallback != null) {
            val uriResult = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            pendingFilePathCallback?.onReceiveValue(uriResult)
            pendingFilePathCallback = null
        }
    }

    private val mediaPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val request = pendingPermissionRequest
        pendingPermissionRequest = null
        if (request != null) {
            val allGranted = grants.isNotEmpty() && grants.values.all { it }
            if (allGranted && isTrustedCurrentPage()) {
                request.grant(request.resources)
            } else {
                request.deny()
            }
        }
    }

    private fun isTrustedCurrentPage(): Boolean {
        val url = webViewRef?.url ?: return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host.equals(LeskovConstants.HOST, true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingUrl = extractInternalUrl(intent)
        if (incomingUrl != null) {
            navigatePreservingMusic(incomingUrl)
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        pendingFilePathCallback?.onReceiveValue(null)
        pendingFilePathCallback = null
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        webViewRef = null
        super.onDestroy()
    }

    private fun extractInternalUrl(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        return if ("https".equals(scheme, ignoreCase = true) &&
            host.equals(LeskovConstants.HOST, ignoreCase = true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)
        ) {
            uri.toString()
        } else {
            null
        }
    }
}

@Composable
fun LeskovApp(
    viewModel: LeskovViewModel,
    initialUrl: String,
    onSetWebView: (WebView) -> Unit,
    getWebView: () -> WebView?,
    onOpenFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    onRequestMediaPermission: (PermissionRequest) -> Unit,
    onNavigate: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val nativeAudio by LeskovNativeAudioService.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var showDeleteCookiesDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.showMessage(LeskovStrings.notificationStatus(
            uiState.language,
            if (!granted) "permission_denied" else if (!com.example.push.FcmTokenManager.isFirebaseConfigured(context)) "firebase_missing" else "ready"
        ))
    }

    // Channel creation is harmless; permission is requested later from a user-initiated
    // settings action, not as an unexplained popup at the first launch.
    LaunchedEffect(uiState.language) {
        com.example.push.FcmTokenManager.createNotificationChannels(context, uiState.language)
    }

    // Request the real device token once, if a real Firebase project is configured.
    LaunchedEffect(Unit) {
        com.example.push.FcmTokenManager.refreshDeviceToken(context)
    }

    // Pending integration: requires authenticated server endpoint (see docs).
    LaunchedEffect(uiState.currentUserId, uiState.language) {
        if (uiState.currentUserId > 0) {
            com.example.push.FcmTokenManager.syncTokenWithServer(context, getWebView(), uiState.currentUserId, uiState.language)
        }
    }

    // Smart Back Handler
    BackHandler(enabled = true) {
        if (uiState.showMoreSheet) {
            viewModel.toggleMoreSheet(false)
        } else if (uiState.showAuthGate) {
            val homeUrl = viewModel.dismissAuthGateToHome()
            onNavigate(homeUrl)
        } else {
            val webView = getWebView()
            if (webView != null && webView.canGoBack()) {
                LeskovAudioHandoff.beforeLeaving(webView) { webView.goBack() }
            } else {
                (context as? ComponentActivity)?.finish()
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(nativeAudio.error) {
        if (nativeAudio.error != null) {
            snackbarHostState.showSnackbar(LeskovStrings.playbackError(uiState.language))
        }
    }
    val progressFraction by animateFloatAsState(
        targetValue = uiState.loadingProgress.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 170), label = "pageLoadProgress"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(LeskovBackground),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = { /* Clean layout - no heavy header bar */ },
        bottomBar = {
            // Keep form fields visible when the Android keyboard is open.
            AnimatedVisibility(visible = !keyboardVisible,
                enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(150)) { it / 2 }
            ) {
            // Exactly one on-screen player: the website's own player. Media3 remains
            // available for background audio and system lock-screen controls only.
            LeskovBottomBar(
                    selectedIndex = if (uiState.showMoreSheet) 5 else uiState.selectedTabIndex,
                    language = uiState.language,
                    onTabSelected = { index ->
                        val urlToLoad = viewModel.onTabSelected(index)
                        if (urlToLoad != null) {
                            viewModel.setOffline(false)
                            onNavigate(urlToLoad)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(LeskovBackground)
        ) {
            // Main WebView remains persistent in the hierarchy to retain state, cookies & playback
            LeskovWebView(
                viewModel = viewModel,
                onFileChooserRequested = onOpenFileChooser,
                onPermissionRequested = onRequestMediaPermission,
                onNativePushRequested = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val configured = com.example.push.FcmTokenManager.isFirebaseConfigured(context)
                        viewModel.showMessage(LeskovStrings.notificationStatus(
                            uiState.language, if (configured) "ready" else "firebase_missing"))
                        if (configured) com.example.push.FcmTokenManager.refreshDeviceToken(context)
                    }
                },
                onWebViewCreated = onSetWebView,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("leskov_webview")
            )

            // Auth Gate Overlay for guests on protected sections
            AnimatedVisibility(
                visible = uiState.showAuthGate,
                enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 12 },
                exit = fadeOut(tween(140))
            ) {
                LeskovAuthGate(
                    selectedTabIndex = uiState.selectedTabIndex,
                    language = uiState.language,
                    onLoginClick = {
                        val authUrl = viewModel.startAuth(isRegister = false)
                        viewModel.setOffline(false)
                        onNavigate(authUrl)
                    },
                    onRegisterClick = {
                        val registerUrl = viewModel.startAuth(isRegister = true)
                        viewModel.setOffline(false)
                        onNavigate(registerUrl)
                    },
                    onOpenTerms = {
                        val docUrl = viewModel.openDocument(LeskovConstants.TERMS_URL)
                        viewModel.setOffline(false)
                        onNavigate(docUrl)
                    },
                    onOpenPrivacy = {
                        val docUrl = viewModel.openDocument(LeskovConstants.PRIVACY_URL)
                        viewModel.setOffline(false)
                        onNavigate(docUrl)
                    },
                    onBackToHome = {
                        val homeUrl = viewModel.dismissAuthGateToHome()
                        onNavigate(homeUrl)
                    },
                    modifier = Modifier.testTag("auth_gate_view")
                )
            }

            // Offline Overlay View
            AnimatedVisibility(
                visible = uiState.isOffline,
                enter = fadeIn(tween(190)) + slideInVertically(tween(240)) { it / 10 },
                exit = fadeOut(tween(170))
            ) {
                LeskovOfflineView(
                    language = uiState.language,
                    onRetry = {
                        viewModel.setOffline(false)
                        getWebView()?.reload()
                    },
                    modifier = Modifier.testTag("offline_view")
                )
            }

            // Minimalist 2.5dp Progress Bar right below status bar
            AnimatedVisibility(
                visible = uiState.isLoading && uiState.loadingProgress < 100,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = LeskovPrimary,
                    trackColor = LeskovSurfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // More Bottom Sheet for Settings, Terms, Privacy, Support, Clear Cache & Delete Cookies
        if (uiState.showMoreSheet) {
            LeskovMoreBottomSheet(
                isLoggedIn = uiState.isLoggedIn,
                language = uiState.language,
                onDismiss = { viewModel.toggleMoreSheet(false) },
                onNavigateUrl = { targetUrl ->
                    viewModel.setOffline(false)
                    onNavigate(targetUrl)
                },
                onRequireLoginForSettings = {
                    val authUrl = viewModel.startAuth(isRegister = false)
                    viewModel.setOffline(false)
                    onNavigate(authUrl)
                },
                onClearAppCache = {
                    viewModel.clearAppCache(getWebView())
                },
                onEnableNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val state = if (!com.example.push.FcmTokenManager.isFirebaseConfigured(context)) "firebase_missing" else "ready"
                        viewModel.showMessage(LeskovStrings.notificationStatus(uiState.language, state))
                        if (state == "ready") com.example.push.FcmTokenManager.refreshDeviceToken(context)
                    }
                },
                onOpenDeleteCookiesConfirm = {
                    showDeleteCookiesDialog = true
                }
            )
        }

        // Delete Cookies Confirmation Dialog
        if (showDeleteCookiesDialog) {
            LeskovDeleteCookiesDialog(
                language = uiState.language,
                onDismiss = { showDeleteCookiesDialog = false },
                webView = getWebView(),
                viewModel = viewModel
            )
        }
    }
}

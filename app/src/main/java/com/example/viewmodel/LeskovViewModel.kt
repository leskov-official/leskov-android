package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LeskovPreferences
import com.example.model.AppLanguage
import com.example.model.LESKOV_NAV_TABS
import com.example.model.LeskovConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PageFailure { CONNECTION, SERVER, SECURITY }

data class LeskovUiState(
    val currentUrl: String = LeskovConstants.buildUrl(LeskovConstants.HOME_PATH, AppLanguage.RU),
    val selectedTabIndex: Int = 0,
    val language: AppLanguage = AppLanguage.RU,
    val isLoggedIn: Boolean = false,
    val currentUserId: Int = 0,
    val authChecked: Boolean = false,
    val showAuthGate: Boolean = false,
    val pendingReturnPath: String = LeskovConstants.FEED_PATH,
    val loadingProgress: Int = 0,
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val pageFailure: PageFailure? = null,
    val retryUrl: String = LeskovConstants.buildUrl(LeskovConstants.HOME_PATH, AppLanguage.RU),
    val canGoBack: Boolean = false,
    val showMoreSheet: Boolean = false,
    val isCallActive: Boolean = false,
    val statusMessage: String? = null
)

class LeskovViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = LeskovPreferences(application)
    private val _uiState = MutableStateFlow(LeskovUiState())
    val uiState: StateFlow<LeskovUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.languageFlow.collect { savedLang ->
                _uiState.update { current ->
                    current.copy(
                        language = savedLang,
                        currentUrl = if (current.currentUrl.startsWith(LeskovConstants.BASE_URL)) {
                            updateUrlLang(current.currentUrl, savedLang)
                        } else {
                            current.currentUrl
                        }
                    )
                }
            }
        }
    }

    fun onCallVisibilityChanged(active: Boolean) {
        _uiState.update { it.copy(isCallActive = active) }
    }

    fun onLanguageDetected(langCode: String?) {
        val newLang = AppLanguage.fromCode(langCode)
        if (newLang != _uiState.value.language) {
            _uiState.update { it.copy(language = newLang) }
            viewModelScope.launch {
                preferences.setLanguage(newLang)
            }
        }
    }

    fun onUrlChanged(url: String) {
        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        val path = uri?.path?.trimStart('/') ?: ""

        if (uri == null || !isTrustedPage(uri)) return

        // Check language from URL query parameter
        val langParam = uri?.getQueryParameter("lang")
        if (!langParam.isNullOrBlank()) {
            onLanguageDetected(langParam)
        }

        // Check if page is an auth or legal screen
        val isAuthScreen = path.contains("auth.php")
        val isLegalScreen = path.contains("terms.php") || path.contains("privacy_policy.php")

        // Map URL path to active tab index
        val matchingIndex = when {
            path.isEmpty() || path == "index.php" || path == "/" -> 0
            path.startsWith("feed") -> 1
            path.startsWith("discover") || path.startsWith("search") -> 2
            path.startsWith("messages") || path.startsWith("chat") -> 3
            path.startsWith("profile") || path.startsWith("user") -> 4
            else -> _uiState.value.selectedTabIndex
        }

        // The real LESKOV PHP logout endpoint is auth.php?logout=1, not logout.php.
        val isLogout = path.contains("logout.php") ||
            (path == "auth.php" && uri?.getQueryParameter("logout") != null)

        _uiState.update { current ->
            current.copy(
                currentUrl = url,
                selectedTabIndex = if (isAuthScreen || isLegalScreen) current.selectedTabIndex else matchingIndex,
                showAuthGate = if (isAuthScreen || isLegalScreen) false else current.showAuthGate,
                isLoggedIn = if (isLogout) false else current.isLoggedIn,
                currentUserId = if (isLogout) 0 else current.currentUserId,
                isOffline = current.pageFailure != null
            )
        }
    }

    fun onUserSessionDetected(userId: Int) {
        val loggedIn = userId > 0
        _uiState.update { current ->
            current.copy(
                isLoggedIn = loggedIn,
                currentUserId = userId,
                authChecked = true,
                showAuthGate = if (loggedIn) false else current.showAuthGate
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(
                loadingProgress = progress,
                isLoading = progress < 100 && it.pageFailure == null,
                isOffline = it.pageFailure != null
            )
        }
    }

    fun setOffline(offline: Boolean) {
        if (offline) onPageFailed(PageFailure.CONNECTION)
        else _uiState.update { it.copy(isOffline = false, pageFailure = null) }
    }

    fun onPageStarted(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (!isTrustedPage(uri)) return
        _uiState.update { it.copy(retryUrl = url, pageFailure = null,
            isOffline = false, isLoading = true, loadingProgress = 0) }
    }

    fun onPageFailed(failure: PageFailure) {
        _uiState.update { it.copy(pageFailure = failure, isOffline = true, isLoading = false) }
    }

    fun retryPage(): String {
        val target = _uiState.value.retryUrl
        onPageStarted(target)
        return target
    }

    private fun isTrustedPage(uri: Uri): Boolean =
        uri.scheme.equals("https", true) && uri.host.equals(LeskovConstants.HOST, true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443)


    fun setCanGoBack(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    /**
     * User tapped bottom navigation tab.
     * Returns the target URL to load into the WebView, or null if AuthGate is shown.
     */
    fun onTabSelected(index: Int): String? {
        val tab = LESKOV_NAV_TABS.getOrNull(index) ?: LESKOV_NAV_TABS[0]
        if (tab.route == "more") {
            toggleMoreSheet(true)
            return null
        }
        val requiresAuth = tab.requiresAuth
        val isGuest = !_uiState.value.isLoggedIn

        if (requiresAuth && isGuest) {
            _uiState.update {
                it.copy(
                    selectedTabIndex = index,
                    showAuthGate = true,
                    pendingReturnPath = tab.path
                )
            }
            return null
        } else {
            val targetUrl = LeskovConstants.buildUrl(tab.path, _uiState.value.language)
            val samePage = _uiState.value.currentUrl == targetUrl &&
                !_uiState.value.showAuthGate && !_uiState.value.isOffline
            _uiState.update {
                it.copy(
                    selectedTabIndex = index,
                    showAuthGate = false
                    // currentUrl only changes when the server actually loads the page.
                    // Pre-setting it caused stale URL/tab state after redirects.
                )
            }
            // Tapping the already-open tab must not reload PHP and interrupt music.
            return if (samePage) null else targetUrl
        }
    }

    fun clearAppCache(webView: android.webkit.WebView?) {
        try {
            webView?.clearCache(true)
            showMessage(com.example.model.LeskovStrings.cacheClearedToast(_uiState.value.language))
        } catch (_: Exception) {}
    }

    /**
     * User clicks "Войти" or "Создать аккаунт" on the AuthGate.
     * Dismisses the auth gate overlay and returns the server auth URL.
     */
    fun startAuth(isRegister: Boolean): String {
        val returnPath = _uiState.value.pendingReturnPath
        val lang = _uiState.value.language
        val hash = if (isRegister) "#register" else ""
        val safeReturnPath = Uri.encode(returnPath.trimStart('/'))
        val authUrl = "${LeskovConstants.BASE_URL}${LeskovConstants.AUTH_PATH}?return=${safeReturnPath}&lang=${lang.code}${hash}"
        _uiState.update {
            it.copy(
                showAuthGate = false,
                currentUrl = authUrl
            )
        }
        return authUrl
    }

    /**
     * Opens Terms or Privacy Policy document.
     * Dismisses the auth gate overlay and returns document URL.
     */
    fun openDocument(docUrl: String): String {
        val lang = _uiState.value.language
        val separator = if (docUrl.contains("?")) "&" else "?"
        val targetUrl = "$docUrl${separator}lang=${lang.code}"
        _uiState.update {
            it.copy(
                showAuthGate = false,
                currentUrl = targetUrl
            )
        }
        return targetUrl
    }

    /**
     * Returns user to Home tab from AuthGate.
     */
    fun dismissAuthGateToHome(): String {
        val homeUrl = LeskovConstants.buildUrl(LeskovConstants.HOME_PATH, _uiState.value.language)
        _uiState.update {
            it.copy(
                selectedTabIndex = 0,
                showAuthGate = false,
                currentUrl = homeUrl
            )
        }
        return homeUrl
    }

    fun toggleMoreSheet(show: Boolean) {
        _uiState.update { it.copy(showMoreSheet = show) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun updateUrlLang(url: String, lang: AppLanguage): String {
        return try {
            val uri = Uri.parse(url)
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames.forEach { key ->
                if (key != "lang") {
                    builder.appendQueryParameter(key, uri.getQueryParameter(key))
                }
            }
            builder.appendQueryParameter("lang", lang.code)
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }
}

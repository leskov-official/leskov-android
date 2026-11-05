package com.example

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppLanguage
import com.example.model.LESKOV_NAV_TABS
import com.example.model.LeskovConstants
import com.example.model.LeskovStrings
import com.example.push.FcmTokenManager
import com.example.viewmodel.LeskovViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private val app: Application
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LESKOV", appName)
  }

  @Test
  fun `guest tab access control and search route`() {
    val viewModel = LeskovViewModel(app)
    // Initial: Guest, on Home
    assertEquals(0, viewModel.uiState.value.selectedTabIndex)
    assertFalse(viewModel.uiState.value.isLoggedIn)
    assertFalse(viewModel.uiState.value.showAuthGate)

    // Tab 0: current Home tab should NOT reload its PHP document or interrupt audio.
    val homeUrl = viewModel.onTabSelected(0)
    assertNull(homeUrl)
    assertEquals(0, viewModel.uiState.value.selectedTabIndex)
    assertFalse(viewModel.uiState.value.showAuthGate)

    // Tab 2: Search is public -> returns URL with discover.php and DOES NOT bounce to home!
    val searchUrl = viewModel.onTabSelected(2)
    assertNotNull(searchUrl)
    assertTrue(searchUrl!!.contains("discover.php"))
    assertEquals(2, viewModel.uiState.value.selectedTabIndex)
    assertFalse(viewModel.uiState.value.showAuthGate)

    // Tab 1: Feed is protected -> guest gets null and AuthGate is shown!
    val feedGuestUrl = viewModel.onTabSelected(1)
    assertNull(feedGuestUrl)
    assertTrue(viewModel.uiState.value.showAuthGate)
    assertEquals("feed.php", viewModel.uiState.value.pendingReturnPath)

    // Tab 3: Messages is protected -> guest gets null and AuthGate is shown!
    val messagesGuestUrl = viewModel.onTabSelected(3)
    assertNull(messagesGuestUrl)
    assertTrue(viewModel.uiState.value.showAuthGate)
    assertEquals("messages.php", viewModel.uiState.value.pendingReturnPath)

    // Tab 4: Profile is protected -> guest gets null and AuthGate is shown!
    val profileGuestUrl = viewModel.onTabSelected(4)
    assertNull(profileGuestUrl)
    assertTrue(viewModel.uiState.value.showAuthGate)
    assertEquals("profile.php", viewModel.uiState.value.pendingReturnPath)
  }

  @Test
  fun `authenticated feed navigation points to feed and not profile`() {
    val viewModel = LeskovViewModel(app)
    viewModel.onUserSessionDetected(42)
    val feedUrl = viewModel.onTabSelected(1)
    assertEquals("https://lesv.online/feed.php?lang=ru", feedUrl)
    assertFalse(viewModel.uiState.value.showAuthGate)
    assertEquals(1, viewModel.uiState.value.selectedTabIndex)
    // The view model must follow the actual completed route, not assume the PHP page loaded.
    assertEquals("https://lesv.online/index.php?lang=ru", viewModel.uiState.value.currentUrl)
    viewModel.onUrlChanged(feedUrl!!)
    assertEquals(1, viewModel.uiState.value.selectedTabIndex)
    assertTrue(viewModel.uiState.value.currentUrl.endsWith("feed.php?lang=ru"))
  }

  @Test
  fun `login and register actions dismiss auth gate and load real auth urls`() {
    val viewModel = LeskovViewModel(app)
    viewModel.onTabSelected(1) // trigger auth gate for feed.php
    assertTrue(viewModel.uiState.value.showAuthGate)

    // Clicking "Войти" dismisses auth gate and returns real auth URL
    val loginUrl = viewModel.startAuth(isRegister = false)
    assertFalse(viewModel.uiState.value.showAuthGate)
    assertEquals("https://lesv.online/auth.php?return=feed.php&lang=ru", loginUrl)

    // Trigger auth gate again
    viewModel.onTabSelected(3) // trigger for messages.php
    assertTrue(viewModel.uiState.value.showAuthGate)

    // Clicking "Создать аккаунт" dismisses auth gate and returns real register URL with #register
    val registerUrl = viewModel.startAuth(isRegister = true)
    assertFalse(viewModel.uiState.value.showAuthGate)
    assertEquals("https://lesv.online/auth.php?return=messages.php&lang=ru#register", registerUrl)

    // Trigger auth gate again and check Terms / Privacy opening
    viewModel.onTabSelected(4)
    assertTrue(viewModel.uiState.value.showAuthGate)
    val termsUrl = viewModel.openDocument(LeskovConstants.TERMS_URL)
    assertFalse(viewModel.uiState.value.showAuthGate)
    assertEquals("https://lesv.online/terms.php?lang=ru", termsUrl)

    viewModel.onTabSelected(1)
    val privacyUrl = viewModel.openDocument(LeskovConstants.PRIVACY_URL)
    assertFalse(viewModel.uiState.value.showAuthGate)
    assertEquals("https://lesv.online/privacy_policy.php?lang=ru", privacyUrl)
  }

  @Test
  fun `trilingual navigation labels verification`() {
    // RU
    assertEquals("Главная", LeskovStrings.navLabel("home", AppLanguage.RU))
    assertEquals("Лента", LeskovStrings.navLabel("feed", AppLanguage.RU))
    assertEquals("Поиск", LeskovStrings.navLabel("search", AppLanguage.RU))
    assertEquals("Чаты", LeskovStrings.navLabel("messages", AppLanguage.RU))
    assertEquals("Профиль", LeskovStrings.navLabel("profile", AppLanguage.RU))

    // ET
    assertEquals("Avaleht", LeskovStrings.navLabel("home", AppLanguage.ET))
    assertEquals("Voog", LeskovStrings.navLabel("feed", AppLanguage.ET))
    assertEquals("Otsing", LeskovStrings.navLabel("search", AppLanguage.ET))
    assertEquals("Vestlused", LeskovStrings.navLabel("messages", AppLanguage.ET))
    assertEquals("Profiil", LeskovStrings.navLabel("profile", AppLanguage.ET))

    // EN
    assertEquals("Home", LeskovStrings.navLabel("home", AppLanguage.EN))
    assertEquals("Feed", LeskovStrings.navLabel("feed", AppLanguage.EN))
    assertEquals("Search", LeskovStrings.navLabel("search", AppLanguage.EN))
    assertEquals("Chats", LeskovStrings.navLabel("messages", AppLanguage.EN))
    assertEquals("Profile", LeskovStrings.navLabel("profile", AppLanguage.EN))
  }

  @Test
  fun `feed and guest screens accurate descriptions without duplicate artist subtitle`() {
    // Feed gate: distinct title and subtitle
    assertEquals("Войдите, чтобы открыть ленту и участвовать в обсуждениях", LeskovStrings.authGateTitle(1, AppLanguage.RU))
    assertEquals("Общая лента новостей и комментариев", LeskovStrings.authGateSubtitle(1, AppLanguage.RU))
    assertFalse("Feed subtitle must not duplicate artist subtitle",
      LeskovStrings.authGateSubtitle(1, AppLanguage.RU) == LeskovStrings.artistSubtitle(AppLanguage.RU))

    // Estonian translations
    assertEquals("Logige sisse, et avada voog ja osaleda aruteludes", LeskovStrings.authGateTitle(1, AppLanguage.ET))
    assertEquals("Uudiste ja kommentaaride üldine voog", LeskovStrings.authGateSubtitle(1, AppLanguage.ET))

    // English translations
    assertEquals("Sign in to open the feed and join discussions", LeskovStrings.authGateTitle(1, AppLanguage.EN))
    assertEquals("General feed of news and comments", LeskovStrings.authGateSubtitle(1, AppLanguage.EN))

    // Chats and Profile subtitles
    assertEquals("Личные переписки и общение с друзьями", LeskovStrings.authGateTitle(3, AppLanguage.RU))
    assertEquals("Ваш профиль, публикации и настройки", LeskovStrings.authGateTitle(4, AppLanguage.RU))
  }

  @Test
  fun `more sheet friendly subtitles without raw technical urls`() {
    // Russian
    assertEquals("Управление вашим профилем", LeskovStrings.sheetSettingsSubtitle(AppLanguage.RU))
    assertEquals("Правила использования сервиса", LeskovStrings.sheetTermsSubtitle(AppLanguage.RU))
    assertEquals("Как мы защищаем данные", LeskovStrings.sheetPrivacySubtitle(AppLanguage.RU))
    assertTrue(LeskovStrings.sheetSupportSubtitle(AppLanguage.RU).contains("info@lesv.info"))
    assertEquals("Выйти с аккаунта", LeskovStrings.sheetSignOut(AppLanguage.RU))
    assertEquals("Войти в аккаунт", LeskovStrings.sheetSignIn(AppLanguage.RU))

    // Estonian
    assertEquals("Oma profiili haldamine", LeskovStrings.sheetSettingsSubtitle(AppLanguage.ET))
    assertEquals("Teenuse kasutamise reeglid", LeskovStrings.sheetTermsSubtitle(AppLanguage.ET))
    assertEquals("Kuidas me andmeid kaitseme", LeskovStrings.sheetPrivacySubtitle(AppLanguage.ET))
    assertEquals("Logi välja", LeskovStrings.sheetSignOut(AppLanguage.ET))
    assertEquals("Logi sisse", LeskovStrings.sheetSignIn(AppLanguage.ET))

    // English
    assertEquals("Manage your profile", LeskovStrings.sheetSettingsSubtitle(AppLanguage.EN))
    assertEquals("Service terms and rules", LeskovStrings.sheetTermsSubtitle(AppLanguage.EN))
    assertEquals("How we protect your data", LeskovStrings.sheetPrivacySubtitle(AppLanguage.EN))
    assertEquals("Sign out", LeskovStrings.sheetSignOut(AppLanguage.EN))
    assertEquals("Sign in", LeskovStrings.sheetSignIn(AppLanguage.EN))
  }

  @Test
  fun `fcm notification channels creation verification`() {
    FcmTokenManager.createNotificationChannels(app, AppLanguage.RU)
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val msgChannel = nm.getNotificationChannel(FcmTokenManager.CHANNEL_MESSAGES)
    assertNotNull(msgChannel)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, msgChannel.importance)

    val callsChannel = nm.getNotificationChannel(FcmTokenManager.CHANNEL_CALLS)
    assertNotNull(callsChannel)
    assertEquals(NotificationManager.IMPORTANCE_HIGH, callsChannel.importance)

    val socialChannel = nm.getNotificationChannel(FcmTokenManager.CHANNEL_SOCIAL)
    assertNotNull(socialChannel)

    val postsChannel = nm.getNotificationChannel(FcmTokenManager.CHANNEL_POSTS)
    assertNotNull(postsChannel)

    val sysChannel = nm.getNotificationChannel(FcmTokenManager.CHANNEL_SYSTEM)
    assertNotNull(sysChannel)
  }

  @Test
  fun `language detection and persistence logic`() {
    val viewModel = LeskovViewModel(app)
    assertEquals(AppLanguage.RU, viewModel.uiState.value.language)

    // Detect ET
    viewModel.onLanguageDetected("et")
    assertEquals(AppLanguage.ET, viewModel.uiState.value.language)

    // Detect EN
    viewModel.onLanguageDetected("en")
    assertEquals(AppLanguage.EN, viewModel.uiState.value.language)

    // Detect RU
    viewModel.onLanguageDetected("ru")
    assertEquals(AppLanguage.RU, viewModel.uiState.value.language)
  }

  @Test
  fun `url mapping preserves search discover tab and detects query lang`() {
    val viewModel = LeskovViewModel(app)

    // discover.php maps to tab 2
    viewModel.onUrlChanged("https://lesv.online/discover.php?lang=et")
    assertEquals(2, viewModel.uiState.value.selectedTabIndex)
    assertEquals(AppLanguage.ET, viewModel.uiState.value.language)

    // search.php query maps to tab 2
    viewModel.onUrlChanged("https://lesv.online/search.php?q=music&lang=en")
    assertEquals(2, viewModel.uiState.value.selectedTabIndex)
    assertEquals(AppLanguage.EN, viewModel.uiState.value.language)
  }

  @Test
  fun `six navigation tabs and more sheet trigger`() {
    val viewModel = LeskovViewModel(app)
    assertEquals(6, LESKOV_NAV_TABS.size)

    val moreTab = LESKOV_NAV_TABS[5]
    assertEquals("more", moreTab.route)
    assertFalse(moreTab.requiresAuth)

    assertEquals("Ещё", LeskovStrings.navLabel("more", AppLanguage.RU))
    assertEquals("Veel", LeskovStrings.navLabel("more", AppLanguage.ET))
    assertEquals("More", LeskovStrings.navLabel("more", AppLanguage.EN))

    // Tapping 6th tab triggers more sheet directly without navigating webview
    assertFalse(viewModel.uiState.value.showMoreSheet)
    val resultUrl = viewModel.onTabSelected(5)
    assertNull(resultUrl)
    assertTrue(viewModel.uiState.value.showMoreSheet)
  }

  @Test
  fun `support email verification`() {
    assertEquals("info@lesv.info", LeskovConstants.SUPPORT_EMAIL)
  }

  @Test
  fun `cache clearing keeps user session and shows feedback`() {
    val viewModel = LeskovViewModel(app)
    viewModel.onUserSessionDetected(42)
    assertTrue(viewModel.uiState.value.isLoggedIn)

    viewModel.clearAppCache(null)
    assertTrue(viewModel.uiState.value.isLoggedIn)
    assertEquals("Кэш приложения очищен", viewModel.uiState.value.statusMessage)
  }

  @Test
  fun `profile sections routing`() {
    val viewModel = LeskovViewModel(app)
    val profileSections = listOf("overview", "music", "gallery", "friends", "bookmarks", "drafts", "wallet", "settings", "edit")
    for (sec in profileSections) {
      viewModel.onUrlChanged("https://lesv.online/profile.php?section=$sec&lang=ru")
      assertEquals("Section $sec should map to tab index 4", 4, viewModel.uiState.value.selectedTabIndex)
    }
  }

  @Test
  fun `native handoff validates HTTPS and exact first-party domain`() {
    assertTrue(com.example.media.LeskovNativeAudioService.isAllowedAudioUri(android.net.Uri.parse("https://lesv.online/tracks/a.mp3")))
    assertFalse(com.example.media.LeskovNativeAudioService.isAllowedAudioUri(android.net.Uri.parse("http://lesv.online/tracks/a.mp3")))
    assertFalse(com.example.media.LeskovNativeAudioService.isAllowedAudioUri(android.net.Uri.parse("https://lesv.online.evil.test/tracks/a.mp3")))
    assertFalse(com.example.media.LeskovNativeAudioService.isAllowedAudioUri(android.net.Uri.parse("https://user:pass@lesv.online/tracks/a.mp3")))
    assertFalse(com.example.media.LeskovNativeAudioService.isAllowedAudioUri(android.net.Uri.parse("blob:https://lesv.online/a.mp3")))
  }

  @Test
  fun `failed navigation survives progress and page finished and retries the intended route`() {
    val viewModel = LeskovViewModel(app)
    val target = "https://lesv.online/search.php?q=ambient&lang=en"
    viewModel.onPageStarted(target)
    viewModel.onPageFailed(com.example.viewmodel.PageFailure.CONNECTION)
    viewModel.onProgressChanged(100)
    viewModel.onUrlChanged(target)
    assertTrue(viewModel.uiState.value.isOffline)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(target, viewModel.retryPage())
    assertFalse(viewModel.uiState.value.isOffline)
    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.pageFailure)
  }

  @Test
  fun `server and certificate errors do not masquerade as lost internet`() {
    val viewModel = LeskovViewModel(app)
    for (failure in listOf(com.example.viewmodel.PageFailure.SERVER, com.example.viewmodel.PageFailure.SECURITY)) {
      viewModel.onPageStarted("https://lesv.online/feed.php")
      viewModel.onPageFailed(failure)
      viewModel.onProgressChanged(100)
      assertEquals(failure, viewModel.uiState.value.pageFailure)
      assertTrue(viewModel.uiState.value.isOffline)
      for (language in AppLanguage.entries) {
        assertFalse(LeskovStrings.pageErrorTitle(language, failure) == LeskovStrings.offlineTitle(language))
      }
    }
  }

  @Test
  fun `untrusted navigation cannot replace retry target or user state`() {
    val viewModel = LeskovViewModel(app)
    viewModel.onUserSessionDetected(42)
    val original = viewModel.uiState.value.currentUrl
    for (url in listOf("https://lesv.online.evil.test/auth.php?logout=1", "http://lesv.online/auth.php?logout=1", "https://lesv.online:8443/auth.php?logout=1")) {
      viewModel.onUrlChanged(url)
      viewModel.onPageStarted(url)
      assertEquals(original, viewModel.uiState.value.currentUrl)
      assertEquals(original, viewModel.uiState.value.retryUrl)
      assertTrue(viewModel.uiState.value.isLoggedIn)
    }
  }
}

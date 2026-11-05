package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

data class NavigationTab(
    val route: String,
    val path: String,
    val requiresAuth: Boolean,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

object LeskovConstants {
    const val BASE_URL = "https://lesv.online/"
    const val HOST = "lesv.online"
    val USER_AGENT_SUFFIX = " LESKOV-Android/${com.example.BuildConfig.VERSION_NAME}"

    // Real routes from the PHP backend
    const val HOME_PATH = "index.php"
    const val FEED_PATH = "feed.php"
    const val DISCOVER_PATH = "discover.php"
    const val SEARCH_PATH = "search.php"
    const val MESSAGES_PATH = "messages.php"
    const val PROFILE_PATH = "profile.php"
    const val AUTH_PATH = "auth.php"
    const val SETTINGS_PATH = "profile.php?section=settings"
    const val TERMS_PATH = "terms.php"
    const val PRIVACY_PATH = "privacy_policy.php"

    const val TERMS_URL = "https://lesv.online/terms.php"
    const val PRIVACY_URL = "https://lesv.online/privacy_policy.php"
    const val SETTINGS_URL = "https://lesv.online/profile.php?section=settings"
    const val SUPPORT_EMAIL = "info@lesv.info"

    fun buildUrl(path: String, lang: AppLanguage): String {
        val cleanPath = path.trimStart('/')
        val separator = if (cleanPath.contains("?")) "&" else "?"
        return "${BASE_URL}$cleanPath${separator}lang=${lang.code}"
    }
}

val LESKOV_NAV_TABS = listOf(
    NavigationTab(
        route = "home",
        path = "index.php",
        requiresAuth = false,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    NavigationTab(
        route = "feed",
        path = "feed.php",
        requiresAuth = true,
        selectedIcon = Icons.Filled.DynamicFeed,
        unselectedIcon = Icons.Outlined.DynamicFeed
    ),
    NavigationTab(
        route = "search",
        path = "discover.php",
        requiresAuth = false,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search
    ),
    NavigationTab(
        route = "messages",
        path = "messages.php",
        requiresAuth = true,
        selectedIcon = Icons.AutoMirrored.Filled.Chat,
        unselectedIcon = Icons.AutoMirrored.Outlined.Chat
    ),
    NavigationTab(
        route = "profile",
        path = "profile.php",
        requiresAuth = true,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    ),
    NavigationTab(
        route = "more",
        path = "more",
        requiresAuth = false,
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz
    )
)

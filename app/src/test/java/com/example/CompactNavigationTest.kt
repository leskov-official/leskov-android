package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.LESKOV_NAV_TABS
import com.example.ui.components.LeskovBottomBar
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class CompactNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun verify(language: AppLanguage, loggedIn: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MyApplicationTheme {
                    Box(Modifier.width(320.dp)) {
                        LeskovBottomBar(selectedIndex = 5, language = language, onTabSelected = {}, isLoggedIn = loggedIn)
                    }
                }
            }
        }
        LESKOV_NAV_TABS.forEach { tab ->
            val node = compose.onNodeWithTag("nav_tab_${tab.route}")
            if (loggedIn || !tab.requiresAuth) node.assertIsDisplayed().assertHeightIsEqualTo(50.dp)
            else node.assertDoesNotExist()
        }
    }

    @Test fun `guest sees only three tabs`() = verify(AppLanguage.RU, false)

    @Test fun `all Russian tabs visible on narrow screen with large system font`() = verify(AppLanguage.RU)
    @Test fun `all Estonian tabs visible on narrow screen with large system font`() = verify(AppLanguage.ET)
}

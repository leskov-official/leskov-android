package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.ui.components.LeskovAuthGate
import com.example.ui.components.LeskovMoreBottomSheet
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h700dp-mdpi")
class CompactChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `guest card keeps compact buttons with large system font`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MyApplicationTheme {
                    Box(Modifier.width(320.dp).height(620.dp)) {
                        LeskovAuthGate(4, AppLanguage.RU, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        assertTrue(compose.onNodeWithTag("auth_gate_login_button").assertIsDisplayed().getUnclippedBoundsInRoot().let { it.bottom - it.top <= 56.dp })
        assertTrue(compose.onNodeWithTag("auth_gate_register_button").assertIsDisplayed().getUnclippedBoundsInRoot().let { it.bottom - it.top <= 56.dp })
    }

    @Test fun `more menu settings remain compact with large system font`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MyApplicationTheme {
                    LeskovMoreBottomSheet(true, AppLanguage.RU, {}, {}, {}, {}, {}, {})
                }
            }
        }
        assertTrue(compose.onNodeWithTag("sheet_settings_item").assertIsDisplayed().getUnclippedBoundsInRoot().let { it.bottom - it.top <= 84.dp })
    }
}

package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.LESKOV_NAV_TABS
import com.example.model.LeskovStrings
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovSurface
import com.example.ui.theme.LeskovSurfaceSelected
import com.example.ui.theme.LeskovTextNavInactive

/** Six equal tabs fit the screen; selected tabs never move out of view. */
@Composable
fun LeskovBottomBar(selectedIndex: Int, language: AppLanguage,
                    onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // Keep this small navigation strip stable at large system fonts. Full labels remain
    // available to TalkBack; page content can still be magnified with WebView pinch zoom.
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceIn(0.85f, 1.15f))) {
        Surface(modifier = modifier.fillMaxWidth(), color = LeskovSurface, tonalElevation = 2.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().navigationBarsPadding()
                    .selectableGroup().padding(horizontal = 4.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    LESKOV_NAV_TABS.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        val label = LeskovStrings.navLabel(tab.route, language)
                        val tint by animateColorAsState(if (selected) LeskovAccentGlow else LeskovTextNavInactive,
                            label = "navTint")
                        Column(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(12.dp))
                            .semantics { contentDescription = label }
                            .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(index) })
                            .testTag("nav_tab_${tab.route}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Box(Modifier.size(width = 36.dp, height = 24.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) LeskovSurfaceSelected else Color.Transparent),
                                contentAlignment = Alignment.Center) {
                                Icon(if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(label, color = tint, fontSize = 9.5.sp, lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

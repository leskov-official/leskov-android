package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.LESKOV_NAV_TABS
import com.example.model.LeskovStrings
import com.example.model.NavigationTab
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovSurface
import com.example.ui.theme.LeskovSurfaceSelected
import com.example.ui.theme.LeskovTextNavInactive

/** A single native navigation surface; no additional floating menu or WebView top bar. */
@Composable
fun LeskovBottomBar(
    selectedIndex: Int,
    language: AppLanguage,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LeskovSurface,
        shadowElevation = 18.dp,
        tonalElevation = 3.dp
    ) {
        Column {
            HorizontalDivider(color = Color.White.copy(alpha = 0.075f), thickness = 1.dp)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val itemWidth = maxOf(maxWidth / LESKOV_NAV_TABS.size, 64.dp)
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(top = 7.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LESKOV_NAV_TABS.forEachIndexed { index, tab ->
                        LeskovNavItem(
                            tab = tab,
                            label = LeskovStrings.navLabel(tab.route, language),
                            isSelected = selectedIndex == index,
                            onClick = { onTabSelected(index) },
                            modifier = Modifier.width(itemWidth).testTag("nav_tab_${tab.route}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeskovNavItem(
    tab: NavigationTab,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(
        if (isSelected) LeskovSurfaceSelected else Color.Transparent,
        label = "navBackground"
    )
    val content by animateColorAsState(
        if (isSelected) Color(0xFFF6F3FF) else LeskovTextNavInactive,
        label = "navText"
    )
    val iconTint by animateColorAsState(
        if (isSelected) LeskovAccentGlow else LeskovTextNavInactive,
        label = "navIcon"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.09f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium), label = "navScale"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium), label = "navIndicator"
    )
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .semantics { selected = isSelected }
            .clickable(
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = LeskovAccentGlow),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp).scale(scale)
            )
            Spacer(Modifier.height(3.dp))
            Crossfade(targetState = label, label = "navTranslation") { currentLabel ->
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.sp,
                        color = content
                    ),
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 3.dp),
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(indicatorWidth)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) LeskovAccentGlow else Color.Transparent)
            )
        }
    }
}

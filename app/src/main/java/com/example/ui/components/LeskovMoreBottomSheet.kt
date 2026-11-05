package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.BuildConfig
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.LeskovConstants
import com.example.model.LeskovStrings
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovBackground
import com.example.ui.theme.LeskovError
import com.example.ui.theme.LeskovOnBackground
import com.example.ui.theme.LeskovSurfaceVariant
import com.example.ui.theme.LeskovTextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeskovMoreBottomSheet(
    isLoggedIn: Boolean,
    language: AppLanguage,
    onDismiss: () -> Unit,
    onNavigateUrl: (String) -> Unit,
    onRequireLoginForSettings: () -> Unit,
    onClearAppCache: () -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenDeleteCookiesConfirm: () -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LeskovCompactContent {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LeskovBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        // A modal has its own Android window and density provider.
        LeskovCompactContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF30284F), Color(0xFF161D32))))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("LESKOV", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
                        color = LeskovOnBackground)
                    Text(LeskovStrings.sheetTitle(language),
                        style = MaterialTheme.typography.bodyMedium, color = LeskovTextMuted)
                }
                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium,
                    color = LeskovAccentGlow,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 12.dp, vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = LeskovSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Account Settings (Friendly subtitle, no raw technical URL)
            MoreSheetItem(
                icon = Icons.Default.Settings,
                title = LeskovStrings.sheetSettings(language),
                subtitle = LeskovStrings.sheetSettingsSubtitle(language),
                tag = "sheet_settings_item",
                onClick = {
                    onDismiss()
                    if (isLoggedIn) {
                        onNavigateUrl("${LeskovConstants.SETTINGS_URL}&lang=${language.code}")
                    } else {
                        onRequireLoginForSettings()
                    }
                }
            )

            // 2. Terms of Service
            MoreSheetItem(
                icon = Icons.Default.Gavel,
                title = LeskovStrings.sheetTerms(language),
                subtitle = LeskovStrings.sheetTermsSubtitle(language),
                tag = "sheet_terms_item",
                onClick = {
                    onDismiss()
                    onNavigateUrl("${LeskovConstants.TERMS_URL}?lang=${language.code}")
                }
            )

            // 3. Privacy Policy
            MoreSheetItem(
                icon = Icons.Default.Policy,
                title = LeskovStrings.sheetPrivacy(language),
                subtitle = LeskovStrings.sheetPrivacySubtitle(language),
                tag = "sheet_privacy_item",
                onClick = {
                    onDismiss()
                    onNavigateUrl("${LeskovConstants.PRIVACY_URL}?lang=${language.code}")
                }
            )

            // 4. Support Email info@lesv.info
            MoreSheetItem(
                icon = Icons.Default.Email,
                title = LeskovStrings.sheetSupport(language),
                subtitle = LeskovStrings.sheetSupportSubtitle(language),
                tag = "sheet_support_item",
                onClick = {
                    onDismiss()
                    sendSupportEmail(context, onShowMessage)
                }
            )

            // 5. Device notification permission and configuration feedback.
            MoreSheetItem(
                icon = Icons.Default.NotificationsActive,
                title = LeskovStrings.sheetNotifications(language),
                subtitle = LeskovStrings.sheetNotificationsSubtitle(language),
                tag = "sheet_notifications_item",
                onClick = { onDismiss(); onEnableNotifications() }
            )

            // 6. Clear App Cache (Keeps Cookies and User Logged In)
            MoreSheetItem(
                icon = Icons.Default.CleaningServices,
                title = LeskovStrings.sheetClearCache(language),
                subtitle = LeskovStrings.sheetClearCacheSubtitle(language),
                tag = "sheet_clear_cache_item",
                onClick = {
                    onDismiss()
                    onClearAppCache()
                }
            )

            // 6. Sign Out (Logged-in) or Sign In (Guest)
            if (isLoggedIn) {
                MoreSheetItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = LeskovStrings.sheetSignOut(language),
                    subtitle = LeskovStrings.sheetSignOutSubtitle(language),
                    tag = "sheet_delete_cookies_item",
                    iconTint = LeskovError,
                    onClick = {
                        onDismiss()
                        onOpenDeleteCookiesConfirm()
                    }
                )
            } else {
                MoreSheetItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = LeskovStrings.sheetSignIn(language),
                    subtitle = LeskovStrings.sheetSignInSubtitle(language),
                    tag = "sheet_sign_in_item",
                    iconTint = LeskovAccentGlow,
                    onClick = {
                        onDismiss()
                        onRequireLoginForSettings()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }
    }
}

@Composable
private fun MoreSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    iconTint: androidx.compose.ui.graphics.Color = LeskovAccentGlow,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy), label = "sheetItemPress"
    )
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(210)) + slideInVertically(tween(230)) { it / 5 }
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction,
                indication = androidx.compose.material3.ripple(), onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                })
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(14.dp))
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint,
                modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = LeskovOnBackground
                )
            )
            Text(
                text = subtitle,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = LeskovTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = LeskovTextMuted, modifier = Modifier.size(20.dp))
    }
    }
}

private fun sendSupportEmail(context: Context, onShowMessage: (String) -> Unit) {
    val email = LeskovConstants.SUPPORT_EMAIL
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, "Запрос в поддержку LESKOV Android")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback: copy email to clipboard if no email client application is configured
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("LESKOV Support Email", email)
        clipboard?.setPrimaryClip(clip)
        onShowMessage("Email скопирован в буфер: $email")
    }
}

package com.example.ui.components

import android.webkit.WebView
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import com.example.web.WebNavigation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.model.AppLanguage
import com.example.model.LeskovStrings
import com.example.push.FcmTokenManager
import com.example.media.LeskovMediaService
import com.example.viewmodel.LeskovViewModel

@Composable
fun LeskovDeleteCookiesDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    webView: WebView?,
    viewModel: LeskovViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var submitted by remember { mutableStateOf(false) }
    val busy = state.logoutRequestId != null
    LaunchedEffect(busy, submitted) {
        if (submitted && !busy) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = LeskovStrings.signOutConfirmTitle(language),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        text = {
            Text(
                text = LeskovStrings.signOutConfirmDesc(language),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val page = webView
                    if (page == null || !WebNavigation.isTrustedUri(Uri.parse(page.url.orEmpty()))) {
                        viewModel.showMessage(LeskovStrings.logoutUnconfirmed(language))
                        onDismiss()
                        return@TextButton
                    }
                    val id = viewModel.beginLogout() ?: return@TextButton
                    submitted = true
                    // Only the server-confirmed result can clear the local session.
                    page.postDelayed({ viewModel.finishLogout(id, false) }, 20000)
                    try {
                        LeskovMediaService.stop(context)
                        FcmTokenManager.unregisterTokenOnServer(context, page) {
                            if (viewModel.uiState.value.logoutRequestId == id) {
                                if (!WebNavigation.isTrustedUri(Uri.parse(page.url.orEmpty()))) {
                                    viewModel.finishLogout(id, false)
                                } else page.evaluateJavascript(
                                    "window.__leskovAppShell ? window.__leskovAppShell.logout(${JSONObject.quote(id)}) : false;"
                                ) { result -> if (result != "true") viewModel.finishLogout(id, false) }
                            }
                        }
                    } catch (_: Exception) {
                        viewModel.finishLogout(id, false)
                    }
                },
                modifier = Modifier.testTag("confirm_delete_cookies_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = LeskovStrings.signOutBtn(language),
                        modifier = Modifier.alpha(if (busy) 0f else 1f),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_cookies_button")
            ) {
                Text(
                    text = LeskovStrings.cancelBtn(language),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

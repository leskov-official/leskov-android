package com.example.ui.components

import android.net.Uri
import android.webkit.WebView
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
import com.example.web.WebNavigation
import com.example.model.LeskovStrings
import com.example.push.FcmTokenManager
import com.example.ui.theme.LeskovBackground
import com.example.ui.theme.LeskovError
import com.example.ui.theme.LeskovOnBackground
import com.example.ui.theme.LeskovPrimary
import com.example.ui.theme.LeskovTextMuted
import com.example.viewmodel.LeskovViewModel

@Composable
fun LeskovDeleteCookiesDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    webView: WebView?,
    viewModel: LeskovViewModel
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LeskovBackground,
        title = {
            Text(
                text = LeskovStrings.signOutConfirmTitle(language),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = LeskovOnBackground
                )
            )
        },
        text = {
            Text(
                text = LeskovStrings.signOutConfirmDesc(language),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = LeskovTextMuted
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val page = webView
                    if (page != null && WebNavigation.isTrustedUri(Uri.parse(page.url.orEmpty()))) {
                        FcmTokenManager.unregisterTokenOnServer(context, page) {
                            page.evaluateJavascript(
                                "location.origin === 'https://lesv.online' && window.__leskovAppShell?.signOut ? (window.__leskovAppShell.signOut(), true) : false"
                            ) { started ->
                                if (started != "true") viewModel.showMessage(LeskovStrings.signOutFailed(language))
                            }
                        }
                    } else viewModel.showMessage(LeskovStrings.signOutFailed(language))
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_delete_cookies_button")
            ) {
                Text(
                    text = LeskovStrings.signOutBtn(language),
                    color = LeskovError,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_cookies_button")
            ) {
                Text(
                    text = LeskovStrings.cancelBtn(language),
                    color = LeskovPrimary
                )
            }
        }
    )
}

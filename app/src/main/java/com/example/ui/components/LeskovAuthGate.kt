package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.LeskovStrings
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovAccentLight
import com.example.ui.theme.LeskovBackground
import com.example.ui.theme.LeskovOnBackground
import com.example.ui.theme.LeskovPrimary
import com.example.ui.theme.LeskovPrimaryVariant
import com.example.ui.theme.LeskovSurface
import com.example.ui.theme.LeskovSurfaceVariant
import com.example.ui.theme.LeskovTextMuted

@Composable
fun LeskovAuthGate(
    selectedTabIndex: Int,
    language: AppLanguage,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionTitle = LeskovStrings.authGateTitle(selectedTabIndex, language)
    val sectionSubtitle = LeskovStrings.authGateSubtitle(selectedTabIndex, language)
    val loginInteraction = remember { MutableInteractionSource() }
    val registerInteraction = remember { MutableInteractionSource() }
    val loginPressed by loginInteraction.collectIsPressedAsState()
    val registerPressed by registerInteraction.collectIsPressedAsState()
    val loginScale by animateFloatAsState(if (loginPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "loginPress")
    val registerScale by animateFloatAsState(if (registerPressed) 0.975f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "registerPress")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF131630), LeskovBackground)))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 370.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(LeskovSurface.copy(alpha = 0.96f))
                .border(1.dp, LeskovPrimary.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Original circular artwork logo
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .border(2.dp, LeskovPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.leskov_logo),
                    contentDescription = "LESKOV",
                    modifier = Modifier.size(66.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle brand badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(LeskovSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = LeskovAccentGlow,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LESKOV",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = LeskovAccentLight
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section headline
            Crossfade(targetState = sectionTitle, label = "authSectionTitle") { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = LeskovOnBackground,
                        lineHeight = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Crossfade(targetState = sectionSubtitle, label = "authSectionSubtitle") { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = LeskovTextMuted,
                        lineHeight = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Login button
            Button(
                onClick = onLoginClick,
                interactionSource = loginInteraction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LeskovPrimaryVariant,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .scale(loginScale)
                    .testTag("auth_gate_login_button")
            ) {
                Text(
                    text = LeskovStrings.loginButton(language),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Outlined Register button
            OutlinedButton(
                onClick = onRegisterClick,
                interactionSource = registerInteraction,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = LeskovAccentLight
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, LeskovPrimary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .scale(registerScale)
                    .testTag("auth_gate_register_button")
            ) {
                Text(
                    text = LeskovStrings.registerButton(language),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guest clarification
            Text(
                text = LeskovStrings.publicNote(language),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = LeskovTextMuted.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Both links wrap within their own half at large accessibility font sizes.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenTerms,
                    modifier = Modifier.weight(1f).testTag("auth_gate_terms_link")
                ) {
                    Text(
                        text = LeskovStrings.terms(language),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LeskovAccentGlow
                        )
                    )
                }
                Text(text = "·", color = LeskovTextMuted)
                TextButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.weight(1f).testTag("auth_gate_privacy_link")
                ) {
                    Text(
                        text = LeskovStrings.privacy(language),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LeskovAccentGlow
                        )
                    )
                }
            }

            TextButton(
                onClick = onBackToHome,
                modifier = Modifier.testTag("auth_gate_back_to_home")
            ) {
                Text(
                    text = LeskovStrings.backToHome(language),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = LeskovTextMuted
                    )
                )
            }
        }
    }
}

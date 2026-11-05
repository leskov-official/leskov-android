package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import com.example.viewmodel.PageFailure
import com.example.model.AppLanguage
import com.example.model.LeskovStrings
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovBackground
import com.example.ui.theme.LeskovPrimary
import com.example.ui.theme.LeskovPrimaryVariant
import com.example.ui.theme.LeskovSurfaceVariant
import com.example.ui.theme.LeskovTextMuted

@Composable
fun LeskovOfflineView(
    language: AppLanguage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    failure: PageFailure? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LeskovBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(LeskovSurfaceVariant.copy(alpha = 0.6f))
                .border(1.dp, LeskovPrimary.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)

                    .clip(CircleShape)
                    .background(LeskovPrimaryVariant.copy(alpha = 0.2f))
                    .border(1.dp, LeskovPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (failure) {
                        PageFailure.SERVER -> Icons.Default.CloudOff
                        PageFailure.SECURITY -> Icons.Default.Lock
                        else -> Icons.Default.WifiOff
                    },
                    contentDescription = null,
                    tint = LeskovAccentGlow,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = LeskovStrings.pageErrorTitle(language, failure),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = LeskovStrings.pageErrorDescription(language, failure),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = LeskovTextMuted,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LeskovPrimaryVariant,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("retry_button")
            ) {
                Text(
                    text = LeskovStrings.retryBtn(language),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.NativeAudioState
import com.example.model.AppLanguage
import com.example.ui.theme.LeskovAccentGlow
import com.example.ui.theme.LeskovOnBackground
import com.example.ui.theme.LeskovTextMuted

/** Persistent, original-brand now-playing bar, shown only while Media3 owns the stream. */
@Composable
fun LeskovMiniPlayer(
    state: NativeAudioState,
    language: AppLanguage,
    onToggle: () -> Unit,
    onClose: () -> Unit
) {
    val outline = Color(0xFF403F69)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        color = Color(0xFF181C34),
        shape = RoundedCornerShape(17.dp),
        shadowElevation = 9.dp,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.background(
            Brush.horizontalGradient(listOf(Color(0xFF262047), Color(0xFF111A30)))
        )) {
            Row(
                Modifier.fillMaxWidth().height(59.dp).padding(start = 9.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.leskov_logo),
                    contentDescription = null,
                    modifier = Modifier.size(41.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Crossfade(targetState = state.title, animationSpec = tween(180),
                        label = "trackTitle") { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = LeskovOnBackground,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = LeskovTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(45.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(LeskovAccentGlow),
                        contentAlignment = Alignment.Center) {
                        Crossfade(targetState = state.playing, animationSpec = tween(160),
                            label = "playPauseIcon") { playing ->
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = when (language) {
                                    AppLanguage.RU -> if (playing) "Пауза" else "Воспроизвести"
                                    AppLanguage.ET -> if (playing) "Paus" else "Esita"
                                    AppLanguage.EN -> if (playing) "Pause" else "Play"
                                },
                                modifier = Modifier.size(23.dp), tint = Color(0xFF0D0B22)
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = when(language) {
                        AppLanguage.RU -> "Закрыть плеер"
                        AppLanguage.ET -> "Sulge mängija"
                        AppLanguage.EN -> "Close player"
                    },
                        tint = Color(0xFFC1BCDB), modifier = Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(visible = state.buffering) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = LeskovAccentGlow,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

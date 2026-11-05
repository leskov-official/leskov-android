package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun LeskovSnackbar(data: SnackbarData) = LeskovCompactContent {
    Surface(Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp), shadowElevation = 8.dp, color = Color(0xFF16223A)) {
        Row(Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF192641), Color(0xFF12323A))))
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.leskov_logo), contentDescription = null,
                modifier = Modifier.size(30.dp).clip(CircleShape))
            Text(data.visuals.message, modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                color = Color(0xFFF2F6FF), fontSize = 13.sp, lineHeight = 18.sp)
            IconButton(onClick = { data.dismiss() }) {
                Icon(Icons.Default.Close, contentDescription = "OK", tint = Color(0xFF8FD5E2), modifier = Modifier.size(18.dp))
            }
        }
    }
}

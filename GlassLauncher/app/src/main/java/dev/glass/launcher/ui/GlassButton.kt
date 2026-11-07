package dev.glass.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A pill of real glass (refraction, rim light, press bulge and spring) instead of a flat Material
 * text button. Pass `Modifier.weight(1f)` to share a row; otherwise it wraps its label.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassPanel(
        modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        cornerRadius = 22.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

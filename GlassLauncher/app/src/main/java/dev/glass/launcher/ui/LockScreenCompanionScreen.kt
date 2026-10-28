package dev.glass.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import dev.glass.launcher.settings.LauncherSettings
import dev.glass.launcher.ui.glass.LocalGlassMaterial
import dev.glass.launcher.ui.glass.LocalGlassQuality
import dev.glass.launcher.ui.glass.rememberGlassQuality
import dev.glass.launcher.wallpaper.WallpaperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun LockScreenCompanionScreen(
    onContinueToDeviceUnlock: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context) { LauncherSettings(context) }
    var wallpaper by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val material = remember(settings.preset, settings.glassIntensity) { settings.material() }

    LaunchedEffect(context) {
        val metrics = context.resources.displayMetrics
        val bitmap = withContext(Dispatchers.IO) {
            val store = WallpaperStore(context)
            store.current()?.let { store.decode(it, metrics.widthPixels, metrics.heightPixels) }
        }
        wallpaper = bitmap?.asImageBitmap()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            nowMillis = System.currentTimeMillis()
        }
    }

    CompositionLocalProvider(
        LocalGlassMaterial provides material,
        LocalGlassQuality provides rememberGlassQuality(),
    ) {
        BackdropHost(wallpaper = wallpaper) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "GLASS COMPANION",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onClose) {
                        Text("Close", color = Color.White)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DateFormat.getTimeFormat(context).format(Date(nowMillis)),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        fontSize = 82.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 3f), 12f),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    GlassPanel(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        cornerRadius = 24.dp,
                    ) {
                        Text(
                            text = DateFormat.format("EEEE, d MMMM", Date(nowMillis)).toString(),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = Color.White,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                    ) {
                        Text(
                            text = "The device unlock flow remains handled by Android. This companion layers over the lock screen with the same glass wallpaper and motion.",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onContinueToDeviceUnlock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text("Continue to device unlock")
                    }
                }
            }
        }
    }
}
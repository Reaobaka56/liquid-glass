package dev.glass.launcher.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.glass.launcher.ui.glass.LocalReduceMotion
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live device state for the status bar. Every field comes from a real Android source.
 *
 * @property batteryPercent 0..100, or -1 if Android did not report it.
 * @property wifiLevel -1 when Wi-Fi is not the active network, otherwise 1..3.
 * @property cellularLevel -1 when there is no ready SIM, otherwise 0..4 bars.
 * @property silent true when the ringer is on silent or vibrate.
 */
@Immutable
data class StatusBarState(
    val timeText: String = "",
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val wifiLevel: Int = -1,
    val cellularLevel: Int = -1,
    val silent: Boolean = false,
)

/** Height of the system status bar / display cut-out, whether or not the bar is currently shown. */
@Composable
fun rememberStatusBarHeight(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    return remember(context, density, cutoutTop) {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        val barPx = if (id > 0) res.getDimensionPixelSize(id) else with(density) { 24.dp.roundToPx() }
        with(density) { maxOf(barPx, cutoutTop).toDp() }
    }
}

/**
 * Hides the system status bar while this is in composition (a swipe from the top edge still
 * reveals it, so the notification shade stays reachable) and restores it afterwards.
 * Only affects this window; other apps keep their own status bar.
 */
@Composable
fun HideSystemStatusBar(hide: Boolean) {
    val view = LocalView.current
    DisposableEffect(hide) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (hide) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun StatusBarState.withBattery(intent: Intent): StatusBarState {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
    return copy(batteryPercent = percent, charging = plugged != 0)
}

private fun wifiLevelOf(caps: NetworkCapabilities): Int {
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return -1
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 3
    val rssi = caps.signalStrength
    return when {
        rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED -> 3
        rssi >= -60 -> 3
        rssi >= -72 -> 2
        else -> 1
    }
}

/** Collects real battery, charging, network, SIM-signal, ringer and clock state. */
@Composable
fun rememberStatusBarState(): StatusBarState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(StatusBarState()) }

    // Clock, re-read at the top of every minute.
    LaunchedEffect(context) {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        while (true) {
            val now = System.currentTimeMillis()
            state = state.copy(timeText = format.format(Date(now)))
            delay(60_000L - (now % 60_000L))
        }
    }

    // Battery + ringer mode (both are system broadcasts; the battery one is sticky).
    DisposableEffect(context) {
        val audio = context.getSystemService(AudioManager::class.java)
        fun silentNow() = audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL

        val battery = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state = state.withBattery(intent)
            }
        }
        val ringer = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state = state.copy(silent = silentNow())
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context, ringer, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) state = state.withBattery(sticky)
        state = state.copy(silent = silentNow())

        onDispose {
            context.unregisterReceiver(battery)
            context.unregisterReceiver(ringer)
        }
    }

    // Wi-Fi: follows the default network (needs ACCESS_NETWORK_STATE, a normal permission).
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state = state.copy(wifiLevel = wifiLevelOf(caps))
            }

            override fun onLost(network: Network) {
                state = state.copy(wifiLevel = -1)
            }
        }
        val registered = cm != null && runCatching {
            cm.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        }.isSuccess
        onDispose {
            if (registered) runCatching { cm?.unregisterNetworkCallback(callback) }
        }
    }

    // Cellular bars: polled, because there is no permission-free push API. No ready SIM -> hidden.
    LaunchedEffect(context) {
        val tm = context.getSystemService(TelephonyManager::class.java)
        while (true) {
            val level = runCatching {
                when {
                    tm == null || tm.simState != TelephonyManager.SIM_STATE_READY -> -1
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> tm.signalStrength?.level ?: -1
                    else -> -1
                }
            }.getOrDefault(-1)
            if (level != state.cellularLevel) state = state.copy(cellularLevel = level)
            delay(5_000L)
        }
    }

    return state
}

/**
 * iOS-style status bar: time on the left, cellular, Wi-Fi and battery on the right, a silent-mode
 * bell when relevant, and an island pill that pops out when the charger is plugged in.
 * Give it the status bar height (see [rememberStatusBarHeight]).
 */
@Composable
fun IosStatusBar(modifier: Modifier = Modifier) {
    val s = rememberStatusBarState()
    val shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 4f)
    val description = buildString {
        append("Status bar. ")
        if (s.batteryPercent >= 0) {
            append("Battery ${s.batteryPercent} percent")
            if (s.charging) append(", charging")
            append(". ")
        }
        if (s.wifiLevel >= 0) append("Wi-Fi connected. ")
        if (s.silent) append("Silent mode.")
    }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = s.timeText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = shadow),
            )
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (s.silent) SilentBell()
                if (s.cellularLevel >= 0) SignalBars(s.cellularLevel)
                if (s.wifiLevel >= 0) WifiGlyph(s.wifiLevel)
                BatteryGlyph(s.batteryPercent, s.charging, shadow)
            }
        }
        ChargingIsland(s.charging, s.batteryPercent, Modifier.align(Alignment.Center))
    }
}

/** Pops out for a couple of seconds when charging starts, then tucks away. */
@Composable
private fun ChargingIsland(charging: Boolean, percent: Int, modifier: Modifier) {
    val reduceMotion = LocalReduceMotion.current
    var show by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }

    LaunchedEffect(charging) {
        if (!seeded) {
            seeded = true // The first reading is the starting state, not a plug-in event.
            return@LaunchedEffect
        }
        if (charging) {
            show = true
            delay(2_600L)
            show = false
        } else {
            show = false
        }
    }

    val widthSpec: AnimationSpec<Dp> =
        if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 380f)
    val islandWidth by animateDpAsState(if (show) 150.dp else 34.dp, widthSpec, label = "islandWidth")
    val islandAlpha by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 160),
        label = "islandAlpha",
    )

    if (islandAlpha > 0.01f) {
        Box(
            modifier
                .width(islandWidth)
                .height(34.dp)
                .graphicsLayer { this.alpha = islandAlpha }
                .clip(RoundedCornerShape(17.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Canvas(Modifier.size(10.dp, 16.dp)) { drawBolt(Color(0xFF34C759), size) }
                if (percent >= 0) {
                    Text("$percent%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBolt(color: Color, box: Size) {
    val w = box.width
    val h = box.height
    val bolt = Path().apply {
        moveTo(w * 0.62f, 0f)
        lineTo(w * 0.05f, h * 0.58f)
        lineTo(w * 0.46f, h * 0.58f)
        lineTo(w * 0.38f, h)
        lineTo(w * 0.95f, h * 0.38f)
        lineTo(w * 0.54f, h * 0.38f)
        close()
    }
    drawPath(bolt, color)
}

@Composable
private fun SignalBars(level: Int) {
    Canvas(Modifier.size(17.dp, 12.dp)) {
        val barW = 3.dp.toPx()
        val gap = 1.5.dp.toPx()
        val heights = floatArrayOf(4f, 6.5f, 9f, 12f)
        for (i in 0 until 4) {
            val h = heights[i].dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = if (i < level) 1f else 0.35f),
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun WifiGlyph(level: Int) {
    Canvas(Modifier.size(17.dp, 12.dp)) {
        val stroke = 1.6.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height - 1.2.dp.toPx()
        val radii = floatArrayOf(3.2f, 6.5f, 9.8f)
        for (i in 0 until 3) {
            val r = radii[i].dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = if (i < level) 1f else 0.35f),
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(Color.White, radius = 1.3.dp.toPx(), center = Offset(cx, cy))
    }
}

@Composable
private fun BatteryGlyph(percent: Int, charging: Boolean, shadow: Shadow) {
    val fillColor = when {
        charging -> Color(0xFF34C759)
        percent in 0..20 -> Color(0xFFFF3B30)
        else -> Color.White
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (percent >= 0) {
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(shadow = shadow),
            )
        }
        Canvas(Modifier.size(27.dp, 13.dp)) {
            val bodyW = 24.dp.toPx()
            val outline = 1.dp.toPx()
            val dim = Color.White.copy(alpha = 0.45f)
            drawRoundRect(
                color = dim,
                topLeft = Offset(outline / 2f, outline / 2f),
                size = Size(bodyW - outline, size.height - outline),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = outline),
            )
            drawRoundRect(
                color = dim,
                topLeft = Offset(bodyW + 1.dp.toPx(), size.height / 2f - 2.dp.toPx()),
                size = Size(1.6.dp.toPx(), 4.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
            if (percent >= 0) {
                val inset = 2.dp.toPx()
                val maxFill = bodyW - inset * 2f
                val fraction = percent.coerceIn(0, 100) / 100f
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(inset, inset),
                    size = Size(maxOf(maxFill * fraction, 2.dp.toPx()), size.height - inset * 2f),
                    cornerRadius = CornerRadius(2.5.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun SilentBell() {
    Canvas(Modifier.size(12.dp)) {
        val w = size.width
        val h = size.height
        val bell = Path().apply {
            moveTo(w * 0.2f, h * 0.72f)
            quadraticBezierTo(w * 0.3f, h * 0.62f, w * 0.3f, h * 0.42f)
            cubicTo(w * 0.3f, h * 0.2f, w * 0.7f, h * 0.2f, w * 0.7f, h * 0.42f)
            quadraticBezierTo(w * 0.7f, h * 0.62f, w * 0.8f, h * 0.72f)
            close()
        }
        drawPath(bell, Color.White)
        drawCircle(Color.White, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.84f))
        // Dark underlay so the slash stays visible on top of the white bell.
        drawLine(
            Color.Black.copy(alpha = 0.55f), Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.9f),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
        )
        drawLine(
            Color.White, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.9f),
            strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round,
        )
    }
}

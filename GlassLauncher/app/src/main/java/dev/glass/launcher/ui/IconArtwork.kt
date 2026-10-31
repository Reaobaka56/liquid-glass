package dev.glass.launcher.ui

import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.glass.launcher.AppIconTheme
import dev.glass.launcher.settings.AppIconShape

val LocalAppIconShape = compositionLocalOf { AppIconShape.SQUIRCLE }
val LocalAppIconCornerRadius = compositionLocalOf { 22f }
val LocalShowAppIconLabels = compositionLocalOf { true }

@Composable
fun AppIconArtwork(
    original: ImageBitmap,
    label: String,
    assignment: String?,
    customIcon: ImageBitmap? = null,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Crossfade(
        targetState = assignment to customIcon,
        modifier = modifier.size(size),
        label = "appIconArtwork",
    ) { (currentAssignment, currentCustomIcon) ->
        AppIconArtworkFrame(
            original = original,
            label = label,
            assignment = currentAssignment,
            customIcon = currentCustomIcon,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AppIconArtworkFrame(
    original: ImageBitmap,
    label: String,
    assignment: String?,
    customIcon: ImageBitmap?,
    modifier: Modifier,
) {
    val clipShape = LocalAppIconShape.current.toComposeShape(LocalAppIconCornerRadius.current)
    val iconShape = LocalAppIconShape.current
    val cornerRadius = LocalAppIconCornerRadius.current
    val parts = assignment?.split("|", limit = 2)
    if (parts == null || parts.size != 2 || (parts[0] == "custom" && customIcon == null)) {
        Image(bitmap = original, contentDescription = label, modifier = modifier.clip(clipShape))
        return
    }
    if (parts[0] == "custom") {
        val selected = customIcon ?: original
        Image(bitmap = selected, contentDescription = label, modifier = modifier.clip(clipShape))
        return
    }
    if (parts[0] == "pack") {
        Image(
            bitmap = customIcon ?: original,
            contentDescription = label,
            modifier = modifier.clip(clipShape),
        )
        return
    }
    val theme = AppIconTheme.entries.firstOrNull { it.name == parts[1] } ?: AppIconTheme.LIQUID_GLASS
    Canvas(modifier) {
        drawIntoCanvas { composeCanvas ->
            drawIcon(composeCanvas.nativeCanvas, parts[0], theme, iconShape, cornerRadius)
        }
    }
}

private fun AppIconShape.toComposeShape(radius: Float): Shape = when (this) {
    AppIconShape.SQUIRCLE -> RoundedCornerShape(percent = (radius + 7f).coerceIn(0f, 50f).toInt() * 2)
    AppIconShape.ROUNDED -> RoundedCornerShape(percent = radius.coerceIn(0f, 50f).toInt() * 2)
    AppIconShape.CIRCLE -> CircleShape
    AppIconShape.SQUARE -> RectangleShape
}

private fun drawIcon(canvas: AndroidCanvas, glyph: String, theme: AppIconTheme, shape: AppIconShape, cornerRadius: Float) {
    val save = canvas.save()
    canvas.scale(canvas.width / 100f, canvas.height / 100f)
    val corner = when (shape) {
        AppIconShape.SQUIRCLE -> (cornerRadius + 7f).coerceIn(0f, 50f)
        AppIconShape.ROUNDED -> cornerRadius.coerceIn(0f, 50f)
        AppIconShape.CIRCLE -> 50f
        AppIconShape.SQUARE -> 0f
    }
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 100f, 100f,
            palette(glyph, theme),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(RectF(1f, 1f, 99f, 99f), corner, corner, backgroundPaint)

    val symbol = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (theme == AppIconTheme.MONOCHROME) 0xFF353741.toInt() else android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (theme == AppIconTheme.MONOCHROME) 0xFF353741.toInt() else android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    when (glyph) {
        "phone" -> drawPhone(canvas, stroke)
        "messages", "whatsapp", "discord" -> drawBubble(canvas, symbol, stroke, glyph == "whatsapp")
        "safari", "maps", "google" -> drawCompass(canvas, symbol, stroke)
        "camera" -> drawCamera(canvas, symbol, stroke)
        "photos" -> drawPhotos(canvas, symbol)
        "settings" -> drawGear(canvas, symbol)
        "store", "files", "wallet" -> drawBag(canvas, symbol, stroke, glyph)
        "music", "spotify", "podcasts" -> drawMusic(canvas, symbol, glyph)
        "weather" -> drawWeather(canvas, symbol, stroke)
        "calendar" -> drawCalendar(canvas, symbol, stroke)
        "clock" -> drawClock(canvas, symbol, stroke)
        "mail", "gmail" -> drawMail(canvas, stroke)
        "notes", "reminders" -> drawNotes(canvas, symbol, stroke)
        "facetime" -> drawVideo(canvas, symbol)
        "calculator" -> drawCalculator(canvas, symbol)
        "health" -> drawHeart(canvas, symbol)
        "translate" -> drawTranslate(canvas, symbol)
        "shortcuts" -> drawShortcuts(canvas, symbol)
        "contacts" -> drawContacts(canvas, symbol, stroke)
        "home" -> drawHome(canvas, symbol, stroke)
        "voice" -> drawVoice(canvas, symbol)
        "tiktok", "youtube" -> drawPlay(canvas, symbol, glyph == "youtube")
        "chrome" -> drawChrome(canvas, symbol, stroke)
        "generic" -> drawInitial(canvas, symbol)
        else -> drawInitial(canvas, symbol)
    }

    if (theme == AppIconTheme.LIQUID_GLASS) {
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, 60f,
                intArrayOf(0x55FFFFFF, 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(RectF(2f, 2f, 98f, 54f), corner, corner, shine)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(RectF(1.5f, 1.5f, 98.5f, 98.5f), corner, corner, rim)
    }
    canvas.restoreToCount(save)
}

private fun palette(glyph: String, theme: AppIconTheme): IntArray {
    if (theme == AppIconTheme.MONOCHROME) return intArrayOf(0xFFF9F9FB.toInt(), 0xFFD8D9DF.toInt())
    if (theme == AppIconTheme.DARK) {
        val color = when (glyph) {
            "phone" -> 0xFF145B45.toInt()
            "messages", "whatsapp" -> 0xFF155944.toInt()
            "music", "spotify" -> 0xFF55434F.toInt()
            "camera", "photos" -> 0xFF514A63.toInt()
            "weather" -> 0xFF344C69.toInt()
            "calendar", "clock" -> 0xFF4E3D59.toInt()
            "youtube", "tiktok" -> 0xFF5C3943.toInt()
            else -> 0xFF3B4355.toInt()
        }
        return intArrayOf(color, 0xFF171A24.toInt())
    }
    return when (glyph) {
        "phone", "facetime" -> intArrayOf(0xFF75E58D.toInt(), 0xFF22B967.toInt())
        "messages", "whatsapp" -> intArrayOf(0xFF7DE88B.toInt(), 0xFF1EAF61.toInt())
        "safari", "maps", "google" -> intArrayOf(0xFF73D7FF.toInt(), 0xFF3786F4.toInt())
        "camera", "photos" -> intArrayOf(0xFFFFAFD8.toInt(), 0xFF9A82FF.toInt())
        "settings", "calculator" -> intArrayOf(0xFF8F9BAD.toInt(), 0xFF4D596D.toInt())
        "store", "files", "wallet" -> intArrayOf(0xFF74D9F7.toInt(), 0xFF3887DD.toInt())
        "music", "spotify", "podcasts" -> intArrayOf(0xFFFF6B82.toInt(), 0xFFCA3E72.toInt())
        "weather" -> intArrayOf(0xFF69C9FF.toInt(), 0xFF4364D9.toInt())
        "calendar", "clock" -> intArrayOf(0xFFFF8175.toInt(), 0xFFE44861.toInt())
        "mail", "gmail" -> intArrayOf(0xFF71B8FF.toInt(), 0xFF4666D8.toInt())
        "notes", "reminders" -> intArrayOf(0xFFFFDD76.toInt(), 0xFFF3A442.toInt())
        "health" -> intArrayOf(0xFFFF7288.toInt(), 0xFFE34066.toInt())
        "translate", "shortcuts" -> intArrayOf(0xFFBA91FF.toInt(), 0xFF7056D9.toInt())
        "contacts", "home" -> intArrayOf(0xFFFFC46F.toInt(), 0xFFEF8157.toInt())
        "voice" -> intArrayOf(0xFFBB96FF.toInt(), 0xFF6D56CF.toInt())
        "tiktok", "youtube" -> intArrayOf(0xFFFF7078.toInt(), 0xFFEB344E.toInt())
        "chrome" -> intArrayOf(0xFFFFCF61.toInt(), 0xFF5DBD81.toInt())
        else -> intArrayOf(0xFF62DCCB.toInt(), 0xFF5369DB.toInt())
    }
}

private fun drawPhone(c: AndroidCanvas, p: Paint) {
    val path = Path().apply {
        moveTo(29f, 20f); cubicTo(24f, 25f, 32f, 47f, 45f, 60f)
        cubicTo(58f, 73f, 76f, 80f, 81f, 72f); lineTo(70f, 58f)
        cubicTo(68f, 55f, 64f, 57f, 60f, 61f); cubicTo(53f, 59f, 41f, 47f, 39f, 40f)
        cubicTo(43f, 36f, 45f, 32f, 42f, 29f); close()
    }
    c.drawPath(path, p)
}

private fun drawBubble(c: AndroidCanvas, fill: Paint, stroke: Paint, whatsapp: Boolean) {
    val rect = RectF(20f, 22f, 80f, 70f)
    c.drawRoundRect(rect, 18f, 18f, fill)
    val tail = Path().apply { moveTo(34f, 66f); lineTo(28f, 82f); lineTo(49f, 69f); close() }
    c.drawPath(tail, fill)
    if (whatsapp) {
        c.drawArc(RectF(34f, 33f, 66f, 64f), 120f, 220f, false, stroke)
    } else {
        c.drawCircle(38f, 46f, 2f, stroke)
        c.drawCircle(50f, 46f, 2f, stroke)
        c.drawCircle(62f, 46f, 2f, stroke)
    }
}

private fun drawCompass(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    c.drawCircle(50f, 50f, 29f, stroke)
    c.drawCircle(50f, 50f, 3f, fill)
    val path = Path().apply { moveTo(61f, 36f); lineTo(54f, 54f); lineTo(37f, 63f); lineTo(44f, 45f); close() }
    c.drawPath(path, fill)
}

private fun drawCamera(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    c.drawRoundRect(RectF(18f, 31f, 82f, 72f), 10f, 10f, fill)
    c.drawRoundRect(RectF(35f, 24f, 51f, 34f), 4f, 4f, fill)
    c.drawCircle(51f, 51f, 14f, stroke)
    c.drawCircle(51f, 51f, 8f, fill)
    c.drawCircle(71f, 40f, 3f, fill)
}

private fun drawPhotos(c: AndroidCanvas, p: Paint) {
    val colors = intArrayOf(0xFFFFCE5C.toInt(), 0xFFFF6B7E.toInt(), 0xFF6DAFFF.toInt(), 0xFF7DDD89.toInt(), 0xFFB387FF.toInt(), 0xFFFFA557.toInt())
    colors.forEachIndexed { index, color ->
        p.color = color
        val angle = index * 60f
        c.save()
        c.rotate(angle, 50f, 50f)
        c.drawOval(RectF(42f, 17f, 58f, 52f), p)
        c.restore()
    }
    p.color = android.graphics.Color.WHITE
    c.drawCircle(50f, 50f, 7f, p)
}

private fun drawGear(c: AndroidCanvas, p: Paint) {
    val teeth = Path()
    repeat(8) { i ->
        val a = Math.toRadians(i * 45.0)
        val r1 = 25.0
        val r2 = 36.0
        val cx = 50.0
        val cy = 50.0
        val x1 = cx + kotlin.math.cos(a) * r1
        val y1 = cy + kotlin.math.sin(a) * r1
        val x2 = cx + kotlin.math.cos(a) * r2
        val y2 = cy + kotlin.math.sin(a) * r2
        if (i == 0) teeth.moveTo(x1.toFloat(), y1.toFloat()) else teeth.lineTo(x1.toFloat(), y1.toFloat())
        teeth.lineTo(x2.toFloat(), y2.toFloat())
    }
    teeth.close()
    c.drawPath(teeth, p)
    p.color = 0xFF748194.toInt()
    c.drawCircle(50f, 50f, 12f, p)
}

private fun drawBag(c: AndroidCanvas, fill: Paint, stroke: Paint, glyph: String) {
    val rect = RectF(24f, 37f, 76f, 77f)
    c.drawRoundRect(rect, 7f, 7f, fill)
    c.drawArc(RectF(36f, 22f, 64f, 52f), 180f, 180f, false, stroke)
    if (glyph == "files") {
        c.drawRoundRect(RectF(36f, 48f, 64f, 67f), 3f, 3f, stroke)
    } else if (glyph == "wallet") {
        c.drawRoundRect(RectF(55f, 49f, 77f, 67f), 4f, 4f, stroke)
    }
}

private fun drawMusic(c: AndroidCanvas, fill: Paint, glyph: String) {
    if (glyph == "spotify") {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f }
        c.drawArc(RectF(24f, 27f, 76f, 74f), 205f, 130f, false, stroke)
        c.drawArc(RectF(31f, 39f, 70f, 72f), 205f, 125f, false, stroke)
        c.drawArc(RectF(38f, 51f, 65f, 70f), 205f, 120f, false, stroke)
    } else {
        val path = Path().apply { moveTo(56f, 25f); lineTo(56f, 62f); cubicTo(56f, 73f, 41f, 79f, 34f, 72f); cubicTo(27f, 65f, 37f, 58f, 49f, 59f); lineTo(49f, 36f); lineTo(71f, 31f); lineTo(71f, 24f); close() }
        c.drawPath(path, fill)
        if (glyph == "podcasts") {
            fill.color = 0x99FFFFFF.toInt()
            c.drawCircle(50f, 43f, 4f, fill)
        }
    }
}

private fun drawWeather(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    fill.color = 0xFFFFD35B.toInt()
    c.drawCircle(59f, 40f, 17f, fill)
    fill.color = android.graphics.Color.WHITE
    c.drawCircle(40f, 59f, 15f, fill)
    c.drawCircle(55f, 54f, 20f, fill)
    c.drawCircle(69f, 61f, 13f, fill)
    c.drawRoundRect(RectF(27f, 54f, 80f, 70f), 8f, 8f, fill)
}

private fun drawCalendar(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    fill.color = 0xFFF14E59.toInt()
    c.drawRoundRect(RectF(22f, 25f, 78f, 76f), 9f, 9f, fill)
    stroke.color = android.graphics.Color.WHITE
    c.drawLine(23f, 41f, 77f, 41f, stroke)
    c.drawLine(37f, 19f, 37f, 33f, stroke)
    c.drawLine(63f, 19f, 63f, 33f, stroke)
    fill.color = android.graphics.Color.WHITE
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 23f; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    c.drawText("31", 50f, 66f, text)
}

private fun drawClock(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    fill.color = android.graphics.Color.WHITE
    c.drawCircle(50f, 50f, 32f, fill)
    stroke.color = 0xFF525866.toInt()
    c.drawCircle(50f, 50f, 29f, stroke)
    c.drawLine(50f, 50f, 50f, 31f, stroke)
    c.drawLine(50f, 50f, 65f, 57f, stroke)
    fill.color = 0xFF525866.toInt()
    c.drawCircle(50f, 50f, 3f, fill)
}

private fun drawMail(c: AndroidCanvas, stroke: Paint) {
    c.drawRoundRect(RectF(18f, 29f, 82f, 72f), 8f, 8f, stroke)
    val path = Path().apply { moveTo(21f, 34f); lineTo(50f, 55f); lineTo(79f, 34f) }
    c.drawPath(path, stroke)
}

private fun drawNotes(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    fill.color = 0xFFFFFDF4.toInt()
    c.drawRoundRect(RectF(25f, 20f, 75f, 80f), 7f, 7f, fill)
    stroke.color = 0xFFFFC84F.toInt()
    stroke.strokeWidth = 4f
    for (y in 39..67 step 10) c.drawLine(34f, y.toFloat(), 66f, y.toFloat(), stroke)
}

private fun drawVideo(c: AndroidCanvas, fill: Paint) {
    c.drawRoundRect(RectF(22f, 32f, 67f, 68f), 9f, 9f, fill)
    val path = Path().apply { moveTo(67f, 42f); lineTo(82f, 33f); lineTo(82f, 67f); lineTo(67f, 58f); close() }
    c.drawPath(path, fill)
}

private fun drawCalculator(c: AndroidCanvas, fill: Paint) {
    fill.color = 0xFF383E4A.toInt()
    c.drawRoundRect(RectF(24f, 18f, 76f, 82f), 9f, 9f, fill)
    fill.color = 0xFFFFA640.toInt()
    c.drawRoundRect(RectF(31f, 26f, 69f, 40f), 3f, 3f, fill)
    fill.color = android.graphics.Color.WHITE
    for (y in listOf(50f, 62f, 74f)) for (x in listOf(36f, 50f, 64f)) c.drawCircle(x, y, 3f, fill)
}

private fun drawHeart(c: AndroidCanvas, fill: Paint) {
    val path = Path().apply {
        moveTo(50f, 75f); cubicTo(44f, 69f, 19f, 50f, 19f, 37f)
        cubicTo(19f, 19f, 42f, 16f, 50f, 34f)
        cubicTo(59f, 16f, 81f, 19f, 81f, 37f)
        cubicTo(81f, 50f, 56f, 69f, 50f, 75f); close()
    }
    c.drawPath(path, fill)
}

private fun drawTranslate(c: AndroidCanvas, fill: Paint) {
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    c.drawText("文", 39f, 61f, text)
    text.textSize = 29f
    c.drawText("A", 65f, 66f, text)
}

private fun drawShortcuts(c: AndroidCanvas, fill: Paint) {
    val path = Path().apply { moveTo(50f, 18f); lineTo(77f, 34f); lineTo(50f, 50f); lineTo(23f, 34f); close() }
    c.drawPath(path, fill)
    val lower = Path().apply { moveTo(23f, 48f); lineTo(50f, 64f); lineTo(77f, 48f); lineTo(77f, 63f); lineTo(50f, 80f); lineTo(23f, 63f); close() }
    fill.color = 0xAAFFFFFF.toInt()
    c.drawPath(lower, fill)
}

private fun drawContacts(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    c.drawCircle(50f, 37f, 14f, fill)
    c.drawRoundRect(RectF(25f, 52f, 75f, 79f), 16f, 16f, fill)
    stroke.color = 0x55FFFFFF
    c.drawLine(25f, 60f, 75f, 60f, stroke)
}

private fun drawHome(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    val roof = Path().apply { moveTo(17f, 48f); lineTo(50f, 20f); lineTo(83f, 48f) }
    c.drawPath(roof, stroke)
    val house = Path().apply { moveTo(27f, 43f); lineTo(50f, 25f); lineTo(73f, 43f); lineTo(73f, 78f); lineTo(27f, 78f); close() }
    c.drawPath(house, fill)
    fill.color = 0xFFB06D42.toInt()
    c.drawRoundRect(RectF(44f, 56f, 57f, 78f), 4f, 4f, fill)
}

private fun drawVoice(c: AndroidCanvas, fill: Paint) {
    c.drawRoundRect(RectF(43f, 21f, 57f, 60f), 8f, 8f, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 5f }
    c.drawArc(RectF(31f, 34f, 69f, 72f), 0f, 180f, false, stroke)
    c.drawLine(50f, 72f, 50f, 81f, stroke)
}

private fun drawPlay(c: AndroidCanvas, fill: Paint, youtube: Boolean) {
    if (youtube) {
        fill.color = android.graphics.Color.WHITE
        c.drawRoundRect(RectF(21f, 32f, 79f, 68f), 11f, 11f, fill)
        fill.color = 0xFFEC4054.toInt()
    }
    val path = Path().apply { moveTo(43f, 34f); lineTo(68f, 50f); lineTo(43f, 66f); close() }
    c.drawPath(path, fill)
}

private fun drawChrome(c: AndroidCanvas, fill: Paint, stroke: Paint) {
    fill.color = 0xFFEF6256.toInt()
    c.drawArc(RectF(18f, 18f, 82f, 82f), 210f, 120f, true, fill)
    fill.color = 0xFFFFD14E.toInt()
    c.drawArc(RectF(18f, 18f, 82f, 82f), 330f, 120f, true, fill)
    fill.color = 0xFF54B879.toInt()
    c.drawArc(RectF(18f, 18f, 82f, 82f), 90f, 120f, true, fill)
    fill.color = 0xFF4285F4.toInt()
    c.drawCircle(50f, 50f, 17f, fill)
    stroke.color = android.graphics.Color.WHITE
    c.drawCircle(50f, 50f, 17f, stroke)
}

private fun drawInitial(c: AndroidCanvas, fill: Paint) {
    fill.color = android.graphics.Color.WHITE
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    c.drawText("A", 50f, 66f, text)
}

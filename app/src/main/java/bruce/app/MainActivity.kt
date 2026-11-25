package bruce.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import bruce.app.ui.theme.Black
import bruce.app.ui.theme.DarkGray
import bruce.app.ui.theme.LightGray
import bruce.app.ui.theme.PurpleAccent
import bruce.app.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Render at 3× so text gets real pixels instead of 8-pixel blobs
private const val SCALE = 3

/** Display function ids from the firmware's serial display protocol. */
private object Fn {
    const val FILLSCREEN = 0
    const val DRAWRECT = 1
    const val FILLRECT = 2
    const val DRAWROUNDRECT = 3
    const val FILLROUNDRECT = 4
    const val DRAWCIRCLE = 5
    const val FILLCIRCLE = 6
    const val DRAWTRIANGLE = 7
    const val FILLTRIANGLE = 8
    const val DRAWELLIPSE = 9
    const val FILLELLIPSE = 10
    const val DRAWLINE = 11
    const val DRAWARC = 12
    const val DRAWWIDELINE = 13
    const val DRAWCENTRESTRING = 14
    const val DRAWRIGHTSTRING = 15
    const val DRAWSTRING = 16
    const val PRINT = 17
    const val DRAWIMAGE = 18
    const val DRAWPIXEL = 19
    const val DRAWFASTVLINE = 20
    const val DRAWFASTHLINE = 21
    const val SCREEN_INFO = 99
}

private fun u16(d: ByteArray, i: Int): Int {
    if (i + 1 >= d.size) return 0
    return ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
}

private fun rgb565(c: Int): Int = android.graphics.Color.rgb(
    (c shr 11 and 0x1F) * 255 / 31,
    (c shr 5 and 0x3F) * 255 / 63,
    (c and 0x1F) * 255 / 31
)

private fun draw(packet: ByteArray, c: AndroidCanvas, paint: Paint, sw: Int, sh: Int) {
    if (packet.size < 3) return
    val fn = packet[2].toInt() and 0xFF
    val p = 3

    fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = rgb565(color)
    }

    fun stroke(color: Int, width: Float = 1f) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = rgb565(color)
    }

    when (fn) {
        Fn.FILLSCREEN -> {
            fill(u16(packet, p))
            c.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        }
        Fn.DRAWRECT, Fn.FILLRECT -> {
            val x = u16(packet, p); val y = u16(packet, p + 2)
            val w = u16(packet, p + 4); val h = u16(packet, p + 6)
            if (fn == Fn.FILLRECT) fill(u16(packet, p + 8)) else stroke(u16(packet, p + 8))
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        Fn.DRAWROUNDRECT, Fn.FILLROUNDRECT -> {
            val x = u16(packet, p); val y = u16(packet, p + 2)
            val w = u16(packet, p + 4); val h = u16(packet, p + 6)
            val r = u16(packet, p + 8).toFloat()
            if (fn == Fn.FILLROUNDRECT) fill(u16(packet, p + 10)) else stroke(u16(packet, p + 10))
            c.drawRoundRect(
                RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), r, r, paint
            )
        }
        Fn.DRAWCIRCLE, Fn.FILLCIRCLE -> {
            val cx = u16(packet, p); val cy = u16(packet, p + 2); val r = u16(packet, p + 4)
            if (fn == Fn.FILLCIRCLE) fill(u16(packet, p + 6)) else stroke(u16(packet, p + 6))
            c.drawCircle(cx.toFloat(), cy.toFloat(), r.toFloat(), paint)
        }
        Fn.DRAWTRIANGLE, Fn.FILLTRIANGLE -> {
            val path = Path().apply {
                moveTo(u16(packet, p).toFloat(), u16(packet, p + 2).toFloat())
                lineTo(u16(packet, p + 4).toFloat(), u16(packet, p + 6).toFloat())
                lineTo(u16(packet, p + 8).toFloat(), u16(packet, p + 10).toFloat())
                close()
            }
            if (fn == Fn.FILLTRIANGLE) fill(u16(packet, p + 12)) else stroke(u16(packet, p + 12))
            c.drawPath(path, paint)
        }
        Fn.DRAWELLIPSE, Fn.FILLELLIPSE -> {
            val cx = u16(packet, p); val cy = u16(packet, p + 2)
            val rx = u16(packet, p + 4); val ry = u16(packet, p + 6)
            if (fn == Fn.FILLELLIPSE) fill(u16(packet, p + 8)) else stroke(u16(packet, p + 8))
            c.drawOval(
                RectF((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat()),
                paint
            )
        }
        Fn.DRAWLINE -> {
            stroke(u16(packet, p + 8))
            c.drawLine(
                u16(packet, p).toFloat(), u16(packet, p + 2).toFloat(),
                u16(packet, p + 4).toFloat(), u16(packet, p + 6).toFloat(), paint
            )
        }
        Fn.DRAWARC -> {
            val cx = u16(packet, p); val cy = u16(packet, p + 2)
            val r1 = u16(packet, p + 4); val r2 = u16(packet, p + 6)
            val startAngle = u16(packet, p + 8)
            val endAngle = u16(packet, p + 10)
            val fgColor = u16(packet, p + 12)
            // A ring segment, stroked — not a filled pie. Icons stack concentric arcs
            // (WiFi waves, RF/LoRa/IR/RFID/BLE), so filling the hole with bgColor would
            // wipe out the arcs drawn underneath. Matches the firmware's own renderer
            // (sd_files/esp32_serial_navigator.html, case 12).
            val outerR = maxOf(r1, r2)
            val innerR = minOf(r1, r2)
            val radius = (outerR + innerR) / 2f
            val band = (outerR - innerR).coerceAtLeast(1).toFloat()
            // TFT_eSPI: 0° = 6 o'clock, clockwise → Android: 0° = 3 o'clock, clockwise positive.
            val start = 90f + startAngle
            val sweep = (if (endAngle >= startAngle) endAngle - startAngle else endAngle + 360 - startAngle).toFloat()
            stroke(fgColor, band)
            c.drawArc(
                RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                start, sweep, false, paint
            )
            paint.strokeWidth = 1f
        }
        Fn.DRAWWIDELINE -> {
            val ax = u16(packet, p); val ay = u16(packet, p + 2)
            val bx = u16(packet, p + 4); val by = u16(packet, p + 6)
            val wd = u16(packet, p + 8)
            val savedCap = paint.strokeCap
            paint.strokeCap = Paint.Cap.ROUND
            stroke(u16(packet, p + 10), maxOf(1, wd).toFloat())
            c.drawLine(ax.toFloat(), ay.toFloat(), bx.toFloat(), by.toFloat(), paint)
            paint.strokeCap = savedCap
            paint.strokeWidth = 1f
        }
        Fn.DRAWPIXEL -> {
            val x = u16(packet, p); val y = u16(packet, p + 2)
            fill(u16(packet, p + 4))
            c.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), paint)
        }
        Fn.DRAWFASTVLINE -> {
            val x = u16(packet, p); val y = u16(packet, p + 2); val h = u16(packet, p + 4)
            stroke(u16(packet, p + 6))
            c.drawLine(x.toFloat(), y.toFloat(), x.toFloat(), (y + h).toFloat(), paint)
        }
        Fn.DRAWFASTHLINE -> {
            val x = u16(packet, p); val y = u16(packet, p + 2); val w = u16(packet, p + 4)
            stroke(u16(packet, p + 6))
            c.drawLine(x.toFloat(), y.toFloat(), (x + w).toFloat(), y.toFloat(), paint)
        }
        Fn.DRAWSTRING, Fn.DRAWCENTRESTRING, Fn.DRAWRIGHTSTRING, Fn.PRINT -> {
            val xTft = u16(packet, p)
            val yTft = u16(packet, p + 2)
            val tSize = u16(packet, p + 4).coerceAtLeast(1)
            val tColor = u16(packet, p + 6)
            val bgColor = u16(packet, p + 8)
            val strOff = p + 10
            if (strOff >= packet.size) return
            val str = String(packet, strOff, packet.size - strOff, Charsets.UTF_8)
                .trimEnd(' ', ' ', '\n', '\r')
            if (str.isEmpty()) return

            paint.textSize = (tSize * 8).toFloat()
            val fm = paint.fontMetrics
            val strW = paint.measureText(str)
            val drawX = when (fn) {
                Fn.DRAWCENTRESTRING -> xTft - strW / 2f
                Fn.DRAWRIGHTSTRING -> xTft - strW
                else -> xTft.toFloat()
            }
            // TFT_eSPI sets textbgcolor == textcolor to mean "transparent background"
            if (bgColor != tColor) {
                fill(bgColor)
                c.drawRect(drawX, yTft.toFloat(), drawX + strW, yTft + (fm.descent - fm.ascent), paint)
            }
            fill(tColor)
            c.drawText(str, drawX, yTft - fm.ascent, paint)
        }
        Fn.DRAWIMAGE -> {
            // Image files live on the device; render a placeholder
            val x = u16(packet, p).toFloat(); val y = u16(packet, p + 2).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.rgb(50, 50, 70)
            c.drawRect(x, y, x + 32, y + 32, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = android.graphics.Color.rgb(100, 100, 140)
            c.drawRect(x, y, x + 32, y + 32, paint)
        }
    }
}

/** Thread-safe canvas — packets applied from the transport thread, read on the UI thread. */
private class ScreenCanvas(typeface: Typeface) {
    var logW = 240; var logH = 135
    private var bmp = newBuf(logW, logH)
    private val lock = Any()
    @Volatile var dirty = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = true
        isSubpixelText = true
        this.typeface = typeface
    }

    private fun newBuf(w: Int, h: Int) =
        Bitmap.createBitmap(w * SCALE, h * SCALE, Bitmap.Config.RGB_565)

    fun apply(packet: ByteArray) {
        if (packet.size < 3) return
        val fn = packet[2].toInt() and 0xFF
        synchronized(lock) {
            if (fn == Fn.SCREEN_INFO && packet.size >= 8) {
                val w = u16(packet, 3); val h = u16(packet, 5)
                if (w > 0 && h > 0 && (w != logW || h != logH)) {
                    logW = w; logH = h; bmp = newBuf(w, h)
                }
                return
            }
            val c = AndroidCanvas(bmp)
            c.scale(SCALE.toFloat(), SCALE.toFloat())
            draw(packet, c, paint, logW, logH)
            dirty = true
        }
    }

    // ponytail: copies the frame so Compose sees a new ImageBitmap; ~0.5 MB/frame at 240×135×3.
    // If that ever shows up in a profile, draw the live Bitmap in a Canvas keyed on a frame counter.
    fun snapshot(): ImageBitmap = synchronized(lock) {
        dirty = false
        bmp.copy(Bitmap.Config.RGB_565, false).asImageBitmap()
    }
}

/**
 * Live device screen over a serial transport (USB OTG today, BLE once the firmware
 * NUS bridge lands) — same "display start"/"nav …" commands the desktop app uses.
 */
@Composable
fun ScreenMirrorPanel(
    serial: SerialCommunication?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val typeface = remember {
        runCatching { ResourcesCompat.getFont(context, R.font.vt323_regular) }.getOrNull()
            ?: Typeface.MONOSPACE
    }
    val canvas = remember { ScreenCanvas(typeface) }
    var isStreaming by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var packetCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var isConnected by remember { mutableStateOf(serial?.isConnected() == true) }

    LaunchedEffect(Unit) {
        if (!isConnected) withContext(Dispatchers.IO) { serial?.connect() }
        while (isActive && !isConnected) {
            delay(500)
            isConnected = serial?.isConnected() == true
        }
    }

    fun send(cmd: String) {
        scope.launch(Dispatchers.IO) { serial?.sendCommand(cmd) }
    }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            serial?.setScreenPacketListener { pkt ->
                canvas.apply(pkt)
                packetCount++
            }
            withContext(Dispatchers.IO) { serial?.sendCommand("display start") }
            while (isActive) {
                if (canvas.dirty) bitmap = withContext(Dispatchers.Default) { canvas.snapshot() }
                delay(33)
            }
        } else {
            withContext(Dispatchers.IO) { serial?.sendCommand("display stop") }
            serial?.setScreenPacketListener(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (serial?.isConnected() == true) serial.sendCommand("display stop")
            serial?.setScreenPacketListener(null)
            serial?.disconnect()
        }
    }

    // Orion-Store Color Palette
    val oledBlack = Color(0xFF000000)
    val orionSurfaceLow = Color(0xFF0D0E12)
    val orionSurfaceCard = Color(0xFF16181F)
    val orionIndigo = Color(0xFF4F46E5)
    val orionLime = Color(0xFFD4E743)
    val orionEmerald = Color(0xFF10B981)
    val orionTextMuted = Color(0xFF8E95A5)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(oledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // --- 1. Top Header (Orion Pill Bar) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(orionSurfaceLow)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConnected && isStreaming) orionEmerald else if (isConnected) orionLime else Color.Red)
                )
                Column {
                    Text(
                        text = "Bruce Live Mirror",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) "USB OTG Active" else "No Device Attached",
                        color = orionTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isStreaming) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(orionSurfaceCard)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$packetCount pkts",
                            color = orionLime,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                OrionSpringButton(
                    label = "✕",
                    containerColor = orionSurfaceCard,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    onClose()
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // --- 2. Live Screen Canvas (OLED Bezel Card) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(orionSurfaceLow)
                .border(
                    1.dp,
                    if (isStreaming) Brush.verticalGradient(listOf(orionIndigo.copy(alpha = 0.5f), Color.Transparent))
                    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)),
                    RoundedCornerShape(26.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Bruce device screen",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            !isConnected -> "Connect device via USB OTG"
                            isStreaming -> "Waiting for draw calls…\nPress a button to trigger repaint"
                            else -> "Screen mirror is idle"
                        },
                        color = orionTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- 3. Stream Toggle Button ---
        OrionSpringButton(
            label = if (isStreaming) "STOP MIRROR STREAM" else "START MIRROR STREAM",
            subLabel = if (isStreaming) "Tap to pause display transmission" else "Stream display frames over USB OTG",
            containerColor = if (isStreaming) orionSurfaceCard else orionIndigo,
            contentColor = Color.White,
            enabled = isConnected,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            isStreaming = !isStreaming
        }

        Spacer(Modifier.height(12.dp))

        // --- 4. Orion Segmented D-Pad & Action Controls ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(orionSurfaceLow)
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)), RoundedCornerShape(24.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Directional Pad (Left Side)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OrionSpringButton(
                    label = "▲",
                    enabled = isConnected,
                    containerColor = orionSurfaceCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(46.dp)
                ) { send("nav up") }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSpringButton(
                        label = "◀",
                        enabled = isConnected,
                        containerColor = orionSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(46.dp)
                    ) { send("nav prev") }

                    OrionSpringButton(
                        label = "▼",
                        enabled = isConnected,
                        containerColor = orionSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(46.dp)
                    ) { send("nav down") }

                    OrionSpringButton(
                        label = "▶",
                        enabled = isConnected,
                        containerColor = orionSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(46.dp)
                    ) { send("nav next") }
                }
            }

            // M5 Action Buttons (Right Side)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Next Button (Btn B)
                    OrionSpringButton(
                        label = "NEXT",
                        subLabel = "Btn B",
                        enabled = isConnected,
                        containerColor = orionSurfaceCard,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.width(82.dp).height(46.dp)
                    ) { send("nav next") }

                    // Back / ESC Button (Power)
                    OrionSpringButton(
                        label = "BACK",
                        subLabel = "Power",
                        enabled = isConnected,
                        containerColor = orionSurfaceCard,
                        contentColor = orionTextMuted,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.width(76.dp).height(46.dp)
                    ) { send("nav esc") }
                }

                // Primary Select Button (Btn A / M5)
                OrionSpringButton(
                    label = "SELECT (M5)",
                    subLabel = "Main Button",
                    enabled = isConnected,
                    containerColor = orionIndigo,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(166.dp).height(50.dp)
                ) { send("nav select") }
            }
        }
    }
}

/**
 * Orion-Store inspired spring-physics button with tactile haptic feedback
 */
@Composable
private fun OrionSpringButton(
    label: String,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    enabled: Boolean = true,
    containerColor: Color = Color(0xFF16181F),
    contentColor: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.91f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "OrionSpringAnim"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.4f))
            .border(
                1.dp,
                if (enabled) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent))
                else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.35f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = Color(0xFF8E95A5),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

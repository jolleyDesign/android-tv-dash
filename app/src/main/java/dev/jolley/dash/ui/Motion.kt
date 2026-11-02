package dev.jolley.dash.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jolley.dash.Controller
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One clock for all ambient motion. [t] only advances while Home is on screen (Compose pauses
 * the frame clock when the activity stops) and only when the Motion setting is on, so nothing
 * here costs anything while an app is in front.
 *
 * Everything reads [t] in the draw phase, so ambient motion redraws layers but never recomposes.
 */
class Motion(private val c: Controller) {
    val on: Boolean get() = c.prefs.motion
    var t by mutableDoubleStateOf(0.0); private set
    /** 0..1 strength of the wallpaper drift; eases in/out when the setting is toggled. */
    val drift = Animatable(if (c.prefs.motion) 1f else 0f)
    /** 0..1 envelope of the focus-ring breath; settles to 0 after [dev.jolley.dash.IDLE_MS] idle. */
    val env = Animatable(0f)

    fun advance(dt: Double) { t += dt }

    /** 0..1, one breath every 3.2 s. */
    fun breath(): Float {
        val e = env.value
        return if (e <= 0f) 0f else e * (.5f - .5f * cos(2 * PI * t / 3.2).toFloat())
    }

    /** Sine with the given period in seconds, -1..1. */
    fun wave(period: Double, phase: Double = 0.0): Float = sin(2 * PI * t / period + phase).toFloat()

    /** 0..1 sawtooth with the given period in seconds. */
    fun saw(period: Double, offset: Double = 0.0): Float = ((t / period + offset) % 1.0).toFloat()
}

val LocalMotion = staticCompositionLocalOf<Motion> { error("Motion not provided") }

@Composable
fun rememberMotion(c: Controller): Motion {
    val m = remember(c) { Motion(c) }
    val on = m.on
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        var last = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                // Clamp: the first frame after returning from an app would otherwise jump.
                if (last >= 0) m.advance(((n - last) / 1e9).coerceAtMost(.1))
                last = n
            }
        }
    }
    LaunchedEffect(on) { m.drift.animateTo(if (on) 1f else 0f, tween(1200, easing = Ease)) }
    val breathe = on && c.lively
    LaunchedEffect(breathe) { m.env.animateTo(if (breathe) 1f else 0f, tween(if (breathe) 600 else 2400)) }
    return m
}

/**
 * Slow Ken Burns pan/zoom. The scale always exceeds the pan so photo edges never show.
 * Applied identically to the wallpaper and the dock's blurred copy, so the glass stays aligned.
 */
fun GraphicsLayerScope.drift(m: Motion) {
    val a = m.drift.value
    if (a == 0f) return
    val s = 1f + a * (.09f + .02f * m.wave(120.0))
    scaleX = s
    scaleY = s
    translationX = a * .03f * size.width * m.wave(97.0)
    translationY = a * .025f * size.height * m.wave(73.0, 1.0)
}

// ---------- Focus ----------

/**
 * Focus ring outline at [r], 6dp outside the element. [b] (0..1) is the breath: the ring eases
 * out by 2dp and a soft halo fades in around it.
 */
fun DrawScope.drawRing(r: Rect, circle: Boolean, corner: Dp, a: Float, b: Float) {
    if (a <= 0f) return
    val stroke = 4.dp.toPx()
    val gap = (6f + 2f * b).dp.toPx()
    fun outline(e: Float, w: Float, alpha: Float) {
        if (circle) {
            drawCircle(C.Accent, radius = r.minDimension / 2 + e, center = r.center, alpha = alpha, style = Stroke(w))
        } else {
            val cr = corner.toPx() + e
            drawRoundRect(
                C.Accent, topLeft = Offset(r.left - e, r.top - e), size = Size(r.width + e * 2, r.height + e * 2),
                cornerRadius = CornerRadius(cr, cr), alpha = alpha, style = Stroke(w),
            )
        }
    }
    outline(gap, stroke, a)
    if (b > .01f) {
        val w = 2.dp.toPx()
        halo.forEachIndexed { k, h -> outline(gap + stroke / 2 + w * (k + .5f), w, a * b * h) }
    }
}

private val halo = floatArrayOf(.2f, .11f, .04f)

/** A soft diagonal glint that sweeps across an element once, shortly after it gains focus. */
@Composable
fun Modifier.sheen(focused: Boolean): Modifier {
    val m = LocalMotion.current
    val p = remember { Animatable(1f) }
    val on = m.on
    LaunchedEffect(focused, on) {
        if (!focused || !on) {
            p.snapTo(1f)
            return@LaunchedEffect
        }
        // Skipped when focus just passes through on the way somewhere else.
        kotlinx.coroutines.delay(140)
        p.snapTo(0f)
        p.animateTo(1f, tween(800, easing = Ease))
    }
    return drawWithContent {
        drawContent()
        val v = p.value
        if (v <= 0f || v >= 1f) return@drawWithContent
        val w = size.width
        val h = size.height
        val x = -.5f * w + v * 2f * w
        val band = .35f * w
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent, .5f to Color.White.copy(alpha = .24f * (1f - v * .4f)), 1f to Color.Transparent,
                start = Offset(x - band, h * .2f), end = Offset(x + band, h * .8f),
            ),
        )
    }
}

/**
 * A single ring that glides between items instead of each item fading its own. [target] is
 * in this composable's coordinates; reading it inside the flow keeps scrolling from recomposing.
 */
@Composable
fun GlideRing(visible: Boolean, corner: Dp, modifier: Modifier = Modifier, target: () -> Rect?) {
    val m = LocalMotion.current
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(220, easing = Ease), label = "glide")
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            placed = false
            return@LaunchedEffect
        }
        snapshotFlow { target() }.collectLatest { r ->
            if (r == null) return@collectLatest
            // Appear in place when the row gains focus; only glide between tiles.
            if (!placed) {
                rect.snapTo(r)
                placed = true
            } else {
                rect.animateTo(r, spring(dampingRatio = .82f, stiffness = 420f))
            }
        }
    }
    Spacer(modifier.fillMaxSize().drawBehind { if (placed) drawRing(rect.value, false, corner, a, m.breath()) })
}

// ---------- Clock ----------

/** Text whose changed characters roll up into place (the clock's minute tick). */
@Composable
fun RollingText(text: String, style: TextStyle) {
    if (!LocalMotion.current.on) {
        BasicText(text, style = style)
        return
    }
    Row {
        val n = text.length
        text.forEachIndexed { i, ch ->
            // Keyed from the right, so 9:59 → 10:00 rolls the minutes rather than shifting every slot.
            key(n - i) {
                AnimatedContent(
                    ch,
                    transitionSpec = {
                        (slideInVertically(tween(560, easing = Ease)) { it / 2 } + fadeIn(tween(360, 60))) togetherWith
                            (slideOutVertically(tween(460, easing = Ease)) { -it / 2 } + fadeOut(tween(220))) using
                            SizeTransform(clip = false)
                    },
                    label = "char",
                ) { BasicText(it.toString(), style = style) }
            }
        }
    }
}

// ---------- Weather ----------

private enum class Wx { Sun, Moon, CloudSun, CloudMoon, Cloud, Fog, Drizzle, Rain, Snow, Storm }

private fun wxOf(code: Int, isDay: Boolean): Wx = when (code) {
    0, 1 -> if (isDay) Wx.Sun else Wx.Moon
    2 -> if (isDay) Wx.CloudSun else Wx.CloudMoon
    3 -> Wx.Cloud
    45, 48 -> Wx.Fog
    in 51..57 -> Wx.Drizzle
    in 61..67, in 80..82 -> Wx.Rain
    in 71..77, 85, 86 -> Wx.Snow
    in 95..99 -> Wx.Storm
    else -> Wx.Cloud
}

private fun path(vararg d: String): Path = Path().apply { d.forEach { addPath(PathParser().parsePathString(it).toPath()) } }

/** The same Lucide paths as [Ic], split into the parts that move. */
private object WxPath {
    val sunCore by lazy { path("M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0") }
    val sunRays by lazy {
        path(
            "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2",
            "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41",
        )
    }
    val moon by lazy { path("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z") }
    val cloud by lazy { path("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z") }
    val partRays by lazy { path("M12 2v2", "m4.93 4.93 1.41 1.41", "M20 12h2", "m19.07 4.93-1.41 1.41") }
    val partArc by lazy { path("M15.947 12.65a4 4 0 0 0-5.925-4.128") }
    val partCloud by lazy { path("M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z") }
    val partMoon by lazy { path("M10.188 8.5A6 6 0 0 1 16 4a1 1 0 0 0 6 6 6 6 0 0 1-3 5.197") }
    val partMoonCloud by lazy { path("M13 16a3 3 0 1 1 0 6H7a5 5 0 1 1 4.9-6Z") }
    val base by lazy { path("M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242") }
    val fog1 by lazy { path("M16 17H7") }
    val fog2 by lazy { path("M17 21H9") }
    val stormCloud by lazy { path("M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973") }
    val bolt by lazy { path("m13 12-3 5h4l-3 5") }
}

private const val LINE = 1.8f
private val lineStyle = Stroke(LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Lucide weather glyph with gentle, looping motion (sun turns, clouds drift, rain falls…). */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, tint: Color) {
    val m = LocalMotion.current
    if (!m.on) {
        Glyph(Ic.weather(code, isDay), size, tint)
        return
    }
    val wx = wxOf(code, isDay)
    Spacer(
        Modifier.size(size).drawBehind {
            scale(this.size.width / 24f, pivot = Offset.Zero) { drawWx(wx, m, tint) }
        },
    )
}

private fun DrawScope.line(p: Path, c: Color, alpha: Float = 1f) = drawPath(p, c, alpha = alpha, style = lineStyle)

private fun DrawScope.drawWx(wx: Wx, m: Motion, c: Color) {
    val drift = m.wave(8.0)
    when (wx) {
        Wx.Sun -> {
            line(WxPath.sunCore, c)
            rotate(((m.t * 9) % 360).toFloat(), Offset(12f, 12f)) { line(WxPath.sunRays, c) }
        }
        Wx.Moon -> rotate(6f * m.wave(10.0), Offset(12f, 12f)) { line(WxPath.moon, c) }
        Wx.Cloud -> translate(1f * drift) { line(WxPath.cloud, c) }
        Wx.CloudSun -> {
            val pulse = .5f + .5f * m.wave(4.0)
            scale(1f + .07f * pulse, Offset(12f, 12f)) { line(WxPath.partRays, c, .55f + .45f * pulse) }
            line(WxPath.partArc, c)
            translate(.9f * drift) { line(WxPath.partCloud, c) }
        }
        Wx.CloudMoon -> {
            rotate(5f * m.wave(10.0), Offset(16f, 9f)) { line(WxPath.partMoon, c) }
            translate(.9f * drift) { line(WxPath.partMoonCloud, c) }
        }
        Wx.Fog -> {
            translate(.6f * drift) { line(WxPath.base, c) }
            translate(1.4f * m.wave(5.0)) { line(WxPath.fog1, c) }
            translate(-1.4f * m.wave(5.0, .8)) { line(WxPath.fog2, c) }
        }
        Wx.Drizzle -> {
            translate(.5f * drift) { line(WxPath.base, c) }
            fall(m, c, period = 1.6, top = 14f, bottom = 22.5f, length = 1f)
        }
        Wx.Rain -> {
            translate(.5f * drift) { line(WxPath.base, c) }
            fall(m, c, period = 1.0, top = 14f, bottom = 22f, length = 2.6f)
        }
        Wx.Snow -> {
            translate(.5f * drift) { line(WxPath.base, c) }
            fall(m, c, period = 3.4, top = 14f, bottom = 22.5f, length = 0f, sway = .7f)
        }
        Wx.Storm -> {
            translate(.5f * drift) { line(WxPath.stormCloud, c) }
            // Mostly steady, with a quick double flicker every few seconds.
            val s = ((m.t % 4.5)).toFloat()
            val flicker = if (s < .07f || s in .14f..0.2f) .2f else 1f
            line(WxPath.bolt, c, flicker)
        }
    }
}

/**
 * Drops falling in three columns (x = 8, 12, 16), two per column, fading in under the cloud and
 * out at the bottom. [length] 0 draws round flakes; [sway] drifts them side to side.
 */
private fun DrawScope.fall(m: Motion, c: Color, period: Double, top: Float, bottom: Float, length: Float, sway: Float = 0f) {
    val xs = floatArrayOf(8f, 12f, 16f)
    for (col in 0..2) for (k in 0..1) {
        val p = m.saw(period, col * .37 + k * .5)
        val alpha = (p / .15f).coerceAtMost(1f) * ((1f - p) / .3f).coerceAtMost(1f)
        val y = top + (bottom - top - length) * p
        val x = xs[col] + sway * sin(2 * PI * (p * 1.3 + col * .6)).toFloat()
        if (length == 0f) {
            drawCircle(c, radius = LINE / 2 + .1f, center = Offset(x, y), alpha = alpha)
        } else {
            drawLine(c, Offset(x, y), Offset(x, y + length), strokeWidth = LINE, cap = StrokeCap.Round, alpha = alpha)
        }
    }
}

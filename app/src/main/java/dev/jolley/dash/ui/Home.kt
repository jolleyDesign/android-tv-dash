@file:OptIn(ExperimentalFoundationApi::class)

package dev.jolley.dash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.jolley.dash.Controller
import dev.jolley.dash.DockEntry
import dev.jolley.dash.MenuTarget
import dev.jolley.dash.Overlay
import dev.jolley.dash.Util
import dev.jolley.dash.Zone
import dev.jolley.dash.data.PinnedShow
import dev.jolley.dash.data.WeatherApi
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun Modifier.track(c: Controller, key: String): Modifier {
    val d = LocalDensity.current.density
    return onGloballyPositioned {
        val r = it.boundsInRoot()
        c.bounds[key] = Rect(r.left / d, r.top / d, r.right / d, r.bottom / d)
    }
}

@Composable
fun focusAnim(focused: Boolean) = animateFloatAsState(if (focused) 1f else 0f, tween(220, easing = Ease), label = "focus")

@Composable
fun Wallpaper(c: Controller) {
    val img = c.wallpaper
    val m = LocalMotion.current
    if (img != null) {
        Image(img, null, Modifier.fillMaxSize().graphicsLayer { drift(m) }, contentScale = ContentScale.Crop)
        // Keeps the clock and dock legible over bright photos.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x99000000), .3f to Color(0x59000000), .5f to Color(0x26000000), .62f to Color(0x40000000), 1f to Color(0xA6000000)),
            ),
        )
    } else {
        Box(Modifier.fillMaxSize().background(C.Bg))
    }
}

@Composable
fun Home(c: Controller) {
    val dim by animateFloatAsState(if (c.overlay is Overlay.Settings) .5f else 1f, tween(300), label = "dim")
    val rise = rememberRise(c)
    Box(Modifier.fillMaxSize()) {
        Clock(c, Modifier.absoluteOffset(96.dp, 72.dp).graphicsLayer { rise(0f, 14.dp); alpha *= dim })
        if (c.showsVisible) ShowsRow(c, Modifier.absoluteOffset(y = 596.dp).graphicsLayer { rise(.1f, 28.dp) })
        Dock(c, Modifier.absoluteOffset(y = 864.dp).graphicsLayer { rise(.2f, 40.dp) })
        Row(
            Modifier.absoluteOffset(y = 1034.dp).fillMaxWidth().graphicsLayer { rise(.2f, 40.dp) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Ic.ChevronDown, 22.dp, C.Muted)
            Spacer(Modifier.width(6.dp))
            BasicText("All apps", style = wall(19, 500, C.Muted))
        }
        val move = c.overlay as? Overlay.Move
        if (move != null) {
            KeyHints(listOf("◀ ▶" to "reorder", "OK" to "done"), Modifier.absoluteOffset(y = 1034.dp))
        }
    }
}

/**
 * Coming back to Home, the clock, shows and dock rise into place, staggered. Purely visual: input
 * works from the first frame. Gated on [Controller.returns] (bumped in onStop) and read in the
 * draw phase, so the first frame after returning is already the start pose — no flash.
 */
private class Rise(private val c: Controller, private val m: Motion) {
    var seen by mutableIntStateOf(c.returns)
    val p = Animatable(1f)

    fun GraphicsLayerScope.invoke(delay: Float, dy: Dp) {
        val v = if (m.on && c.returns != seen) 0f else ((p.value - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (v >= 1f) return
        translationY = (1f - v) * dy.toPx()
        alpha = (v * 1.6f).coerceAtMost(1f)
    }
}

@Composable
private fun rememberRise(c: Controller): GraphicsLayerScope.(Float, Dp) -> Unit {
    val m = LocalMotion.current
    val r = remember(c) { Rise(c, m) }
    LaunchedEffect(c.returns) {
        if (c.returns == r.seen) return@LaunchedEffect
        val animate = m.on
        r.p.snapTo(if (animate) 0f else 1f)
        r.seen = c.returns
        if (animate) r.p.animateTo(1f, tween(480, easing = Ease))
    }
    return { delay, dy -> with(r) { invoke(delay, dy) } }
}

@Composable
fun KeyHints(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(end = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (k, label) ->
            Box(Modifier.border(1.dp, C.Bezel, RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                BasicText(k, style = type(15, 500))
            }
            BasicText(label, style = type(18, color = C.Muted))
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }
    return now
}

private val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val time12 = DateTimeFormatter.ofPattern("h:mm")
private val time24 = DateTimeFormatter.ofPattern("H:mm")

@Composable
fun Clock(c: Controller, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            RollingText(now.format(if (c.prefs.use24h) time24 else time12), wall(120, 300, tracking = -4.8f, lineHeight = 120))
            val w = c.weather
            if (w != null && c.place != null) {
                Column(
                    Modifier
                        .drawBehind { drawLine(C.Bezel, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1.dp.toPx()) }
                        .padding(start = 36.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeatherIcon(w.code, w.isDay, 40.dp, C.Text)
                        BasicText("${w.temp}°", style = wall(48, 300, tracking = -1.4f, lineHeight = 48))
                    }
                    BasicText("${WeatherApi.describe(w.code)} · H ${w.hi}° L ${w.lo}°", style = wall(20, color = C.Muted))
                }
            }
        }
        BasicText(now.format(dateFmt), style = wall(24, color = C.Muted, tracking = .5f))
    }
}

// ---------- Pinned shows ----------

private val tileGradients = listOf(
    Color(0xFF2B4C5E) to Color(0xFF0F1C24),
    Color(0xFF6B3A1F) to Color(0xFF1C0F08),
    Color(0xFF2F5A34) to Color(0xFF0C1A0E),
    Color(0xFF4B2E5A) to Color(0xFF160E1C),
)

@Composable
fun ShowsRow(c: Controller, modifier: Modifier = Modifier) {
    val o = c.overlay
    val active = c.zone == Zone.Shows && (
        o == Overlay.None || o is Overlay.Move && o.zone == Zone.Shows ||
            o is Overlay.Menu && o.target is MenuTarget.Show
        )
    val moving = o is Overlay.Move && o.zone == Zone.Shows
    val glide = LocalMotion.current.on
    // Tile bounds in root px (including focus scale and lift), for the gliding ring.
    val tiles = remember { mutableStateMapOf<Int, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val report: ((Int, Rect) -> Unit)? = if (glide) { i, r -> tiles[i] = r } else null
    Box(modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicText("Pinned shows", Modifier.padding(start = 96.dp), style = Eyebrow.copy(shadow = WallShadow))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(c.shows, key = { _, s -> s.id }) { i, s ->
                    ShowTile(c, s, i, focused = active && c.showIndex == i, lifted = moving && c.showIndex == i, report)
                }
                item(key = "add") {
                    AddTile(c, c.shows.size, focused = active && c.showIndex == c.shows.size, report)
                }
            }
        }
        if (glide) GlideRing(active, 16.dp, Modifier.matchParentSize()) { tiles[c.showIndex]?.translate(-origin) }
    }
}

/** Reports unclipped bounds (a tile half-scrolled out of view still gets a full-size ring). */
private fun Modifier.reportBounds(i: Int, report: ((Int, Rect) -> Unit)?): Modifier =
    if (report == null) this else onGloballyPositioned {
        report(i, it.findRootCoordinates().localBoundingBoxOf(it, clipBounds = false))
    }

@Composable
private fun ShowTile(c: Controller, s: PinnedShow, i: Int, focused: Boolean, lifted: Boolean, report: ((Int, Rect) -> Unit)?) {
    val f by focusAnim(focused)
    val lift by animateDpAsState(if (lifted) (-16).dp else 0.dp, tween(220, easing = Ease), label = "lift")
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bring.bringIntoView() }
    val shape = RoundedCornerShape(16.dp)
    val (g1, g2) = tileGradients[(s.title.hashCode() and 0x7fffffff) % tileGradients.size]
    Box(
        Modifier
            .bringIntoViewRequester(bring)
            .offset(y = lift)
            .graphicsLayer { scaleX = 1 + .06f * f; scaleY = 1 + .06f * f }
            .ring({ if (report != null) 0f else f }, shape, 16.dp)
            .track(c, "show:$i")
            .reportBounds(i, report)
            .size(320.dp, 180.dp)
            .clip(shape)
            .sheen(focused)
            .background(Brush.linearGradient(listOf(g1, g2))),
    ) {
        c.showImages[s.id]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.4f to Color.Transparent, 1f to Color(0xB8000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
            BasicText(s.title, style = type(24, 600, tracking = -.24f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(s.source, style = type(16, color = C.Text.copy(alpha = .75f), tracking = .6f), maxLines = 1)
        }
    }
}

@Composable
private fun AddTile(c: Controller, i: Int, focused: Boolean, report: ((Int, Rect) -> Unit)?) {
    val f by focusAnim(focused)
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bring.bringIntoView() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .bringIntoViewRequester(bring)
            .graphicsLayer { scaleX = 1 + .06f * f; scaleY = 1 + .06f * f }
            .ring({ if (report != null) 0f else f }, shape, 16.dp)
            .reportBounds(i, report)
            .size(320.dp, 180.dp)
            .clip(shape)
            .background(C.Panel.copy(alpha = .55f))
            .drawBehind {
                drawRoundRect(
                    C.Bezel, cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        val tint = if (focused) C.Text else C.Muted
        Glyph(Ic.Plus, 36.dp, tint)
        BasicText("Pin a show", style = type(20, color = tint))
    }
}

// ---------- Dock ----------

private val Pill = RoundedCornerShape(percent = 50)

@Composable
fun Dock(c: Controller, modifier: Modifier = Modifier) {
    val o = c.overlay
    val active = c.zone == Zone.Dock && (
        o == Overlay.None || o is Overlay.Move && o.zone == Zone.Dock ||
            o is Overlay.Menu && o.under == Overlay.None && o.target is MenuTarget.App
        )
    val moving = o is Overlay.Move && o.zone == Zone.Dock
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val wall = c.wallBlur

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.onGloballyPositioned { origin = it.positionInRoot().let { p -> IntOffset(p.x.toInt(), p.y.toInt()) } }) {
            Box(Modifier.matchParentSize().clip(Pill)) {
                if (wall != null) BlurredBackdrop(wall, origin)
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .12f), Color.White.copy(alpha = .03f))))
                        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .25f), Color.White.copy(alpha = .05f))), Pill),
                )
            }
            Row(
                Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var prev: DockEntry? = null
                c.dockEntries.forEachIndexed { i, e ->
                    if (prev != null && prev!!::class != e::class) Box(Modifier.size(1.dp, 56.dp).background(C.Bezel))
                    prev = e
                    val mag = if (!active) 0f else when (abs(i - c.dockIndex)) { 0 -> 1f; 1 -> .38f; 2 -> .1f; else -> 0f }
                    DockItem(c, e, i, focused = active && c.dockIndex == i, lifted = moving && c.dockIndex == i, mag = mag)
                }
            }
        }
    }
}

/**
 * Glass over a photo: re-draw the pre-blurred wallpaper copy behind the dock, aligned to the
 * screen and drifting with it. Pre-blurred so the drift never re-runs a blur per frame.
 */
@Composable
private fun BlurredBackdrop(blur: ImageBitmap, origin: IntOffset) {
    val motion = LocalMotion.current
    Image(
        blur, null,
        Modifier
            .layout { m, cons ->
                val p = m.measure(Constraints.fixed(1920.dp.roundToPx(), 1080.dp.roundToPx()))
                layout(cons.maxWidth, cons.maxHeight) { p.place(-origin.x, -origin.y) }
            }
            .graphicsLayer { drift(motion) },
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
    )
}

private const val MAGNIFY = .32f

@Composable
private fun DockItem(c: Controller, e: DockEntry, i: Int, focused: Boolean, lifted: Boolean, mag: Float) {
    val f by focusAnim(focused || lifted)
    val m by animateFloatAsState(mag, spring(dampingRatio = .72f, stiffness = 420f), label = "mag")
    val lift by animateDpAsState(if (lifted) (-28).dp else 0.dp, spring(dampingRatio = .75f, stiffness = 500f), label = "lift")
    val (wrap, disc) = when (e) {
        is DockEntry.Fav -> 104.dp to 88.dp
        is DockEntry.Recent -> 88.dp to 72.dp
        is DockEntry.Shortcut -> 80.dp to 64.dp
    }
    val label = when (e) {
        is DockEntry.Fav -> e.app.label
        is DockEntry.Recent -> e.app.label
        is DockEntry.Shortcut -> e.util.label
    }
    val rest = if (e is DockEntry.Recent) .78f else 1f
    // Width is read during layout so the magnify spring only re-measures the row, never recomposes it.
    Box(
        Modifier.layout { meas, _ ->
            val w = (wrap.toPx() * (1 + MAGNIFY * m)).roundToInt()
            val h = wrap.roundToPx()
            val p = meas.measure(Constraints.fixed(w, h))
            layout(w, h) { p.place(0, 0) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .offset(y = lift)
                .graphicsLayer {
                    val s = 1 + MAGNIFY * m
                    scaleX = s; scaleY = s
                    // Grow upward out of the dock, keeping the bottom edge put.
                    translationY = -disc.toPx() * MAGNIFY * m / 2
                    alpha = rest + (1 - rest) * f
                }
                .ring({ f }, CircleShape)
                .track(c, "dock:$i")
                .size(disc)
                .clip(CircleShape)
                .sheen(focused),
            contentAlignment = Alignment.Center,
        ) {
            when (e) {
                is DockEntry.Shortcut -> {
                    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = .08f)))
                    Glyph(
                        when (e.util) { Util.Inputs -> Ic.Tv; Util.WiFi -> Ic.Wifi; Util.Settings -> Ic.Gear },
                        28.dp, C.Text,
                    )
                }
                is DockEntry.Fav -> AppDisc(c, e.app.pkg, label, disc)
                is DockEntry.Recent -> AppDisc(c, e.app.pkg, label, disc)
            }
        }
        if (lifted) {
            Glyph(Ic.ChevronLeft, 28.dp, C.Accent, Modifier.align(Alignment.CenterStart).offset(x = (-34).dp, y = lift))
            Glyph(Ic.ChevronRight, 28.dp, C.Accent, Modifier.align(Alignment.CenterEnd).offset(x = 34.dp, y = lift))
        }
        BasicText(
            label,
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 48.dp)
                .requiredWidth(260.dp)
                .graphicsLayer { alpha = if (lifted) 0f else f; translationY = (1 - f) * -6.dp.toPx() },
            style = wall(22, 500).copy(textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

@Composable
fun AppDisc(c: Controller, pkg: String, label: String, size: androidx.compose.ui.unit.Dp) {
    val icon = c.icons[pkg]
    if (icon != null) {
        Image(icon, null, Modifier.size(size), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).background(C.Panel), contentAlignment = Alignment.Center) {
            BasicText(label.take(1).uppercase(), style = type((size.value * .38f).toInt(), 600))
        }
    }
}

package dev.jolley.dash.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.jolley.dash.Controller
import dev.jolley.dash.MenuTarget
import dev.jolley.dash.Overlay
import dev.jolley.dash.Zone
import dev.jolley.dash.data.BtKind
import dev.jolley.dash.data.BtState
import dev.jolley.dash.data.Cities
import dev.jolley.dash.data.NetKind
import dev.jolley.dash.data.Weather
import dev.jolley.dash.data.WeatherApi
import dev.jolley.dash.data.WidgetKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val WIDGETS_Y = 300.dp
/** Where the shows row's tiles sit; used when that row is turned off. */
val WIDGETS_LOW_Y = 564.dp
private val CARD_H = 252.dp
/** Room above and below the cards for the focus ring, inside the faded layer. */
private val PAD = 30.dp
private val VIEW_W = 1920.dp
private val FADE = 96.dp
private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun WidgetsRow(c: Controller, modifier: Modifier = Modifier) {
    val o = c.overlay
    val active = c.zone == Zone.Widgets && (
        o == Overlay.None || o is Overlay.Move && o.zone == Zone.Widgets ||
            o is Overlay.Menu && o.target is MenuTarget.Widget
        )
    val moving = o is Overlay.Move && o.zone == Zone.Widgets
    val list = rememberLazyListState()
    // Scrolled from here, not per card: a card that's off-screen isn't composed, so it can't scroll itself in.
    LaunchedEffect(list) {
        snapshotFlow { (c.widgetIndex to c.widgets).takeIf { c.zone == Zone.Widgets } }.collectLatest { key ->
            val i = key?.first ?: return@collectLatest
            // After a reorder, wait for the new order to be laid out before measuring.
            withFrameNanos { }
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == i }
            val end = info.viewportEndOffset - info.afterContentPadding
            when {
                item == null -> list.animateScrollToItem(i)
                item.offset < 0 || item.size > end -> list.animateScrollBy(item.offset.toFloat())
                item.offset + item.size > end -> list.animateScrollBy((item.offset + item.size - end).toFloat())
            }
        }
    }
    Box(
        modifier
            .offset(y = -PAD)
            .width(VIEW_W)
            // Offscreen so the gradient can mask the row: cards scrolling past the edge fade out.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val f = FADE.toPx() / size.width
                drawRect(Brush.horizontalGradient(0f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
    ) {
        LazyRow(
            Modifier.fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(start = 96.dp, end = FADE, top = PAD, bottom = PAD),
            // Centered only applies while everything fits; a longer row scrolls from the left as usual.
            horizontalArrangement = Arrangement.spacedBy(24.dp, if (c.prefs.centered) Alignment.CenterHorizontally else Alignment.Start),
        ) {
            itemsIndexed(c.widgets, key = { _, k -> k }) { i, k ->
                WidgetCard(c, k, i, focused = active && c.widgetIndex == i, lifted = moving && c.widgetIndex == i)
            }
        }
    }
}

@Composable
private fun WidgetCard(c: Controller, kind: WidgetKind, i: Int, focused: Boolean, lifted: Boolean) {
    val f by focusAnim(focused || lifted)
    val lift by animateDpAsState(if (lifted) (-14).dp else 0.dp, tween(220, easing = Ease), label = "lift")
    Box(Modifier.offset(y = lift)) {
        Box(
            Modifier
                .graphicsLayer { val s = 1 + .03f * f; scaleX = s; scaleY = s }
                .ring({ f }, CardShape, 28.dp)
                .track(c, "widget:$i"),
        ) {
            Card(c) {
                when (kind) {
                    WidgetKind.Weather -> WeatherCard(c)
                    WidgetKind.NowPlaying -> NowPlayingCard(c, focused)
                    WidgetKind.Sun -> SunCard(c)
                    WidgetKind.Clocks -> ClocksCard(c)
                    WidgetKind.Connections -> ConnectionsCard(c)
                }
            }
        }
        if (lifted) {
            Glyph(Ic.ChevronLeft, 28.dp, C.Accent, Modifier.align(Alignment.CenterStart).offset(x = (-26).dp))
            Glyph(Ic.ChevronRight, 28.dp, C.Accent, Modifier.align(Alignment.CenterEnd).offset(x = 26.dp))
        }
    }
}

/**
 * Frosted card: the dock's pre-blurred wallpaper copy under a dark tint — no sheen or gradient. The tint is
 * strong enough that white text holds up on bright photos.
 */
@Composable
private fun Card(c: Controller, content: @Composable BoxScope.() -> Unit) {
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val blur = c.wallBlur
    Box(
        Modifier
            .height(CARD_H)
            .clip(CardShape)
            .onGloballyPositioned { origin = it.positionInRoot().let { p -> IntOffset(p.x.toInt(), p.y.toInt()) } },
    ) {
        // matchParentSize: inside a LazyRow the card's own width constraint is unbounded.
        if (blur != null) Box(Modifier.matchParentSize()) { BlurredBackdrop(blur, origin) }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = if (blur != null) .58f else .7f))
                .border(1.dp, Color.White.copy(alpha = .08f), CardShape),
        )
        Box(Modifier.padding(24.dp), content = content)
    }
}

@Composable
private fun Header(icon: ImageVector, label: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, 20.dp, C.Muted)
        Spacer(Modifier.width(10.dp))
        BasicText(label, Modifier.weight(1f), style = type(17, 500, C.Muted))
        trailing()
    }
}

@Composable
private fun Placeholder(width: Int, icon: ImageVector, title: String, body: String) {
    Column(Modifier.width(width.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Glyph(icon, 40.dp, C.Muted)
        BasicText(title, style = type(26, 600, tracking = -.4f))
        BasicText(body, style = type(18, color = C.Muted, lineHeight = 26))
    }
}

@Composable
private fun VDivider() = Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = .1f)))

private val hour12 = DateTimeFormatter.ofPattern("h a")
private val hour24 = DateTimeFormatter.ofPattern("HH")
private val clock12 = DateTimeFormatter.ofPattern("h:mm a")
private val clock24 = DateTimeFormatter.ofPattern("H:mm")
private val dowFmt = DateTimeFormatter.ofPattern("EEE")

private fun local(epoch: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())

private fun Controller.timeOf(epoch: Long): String =
    if (epoch == 0L) "—" else local(epoch).format(if (prefs.use24h) clock24 else clock12)

// ---------- Weather ----------

@Composable
private fun WeatherCard(c: Controller) {
    val w = c.weather
    val place = c.place
    if (w == null || place == null) {
        Placeholder(
            380, Ic.CloudSun, "Weather",
            if (place == null) "Set your ZIP code to see the forecast.\nPress OK to set it." else "Loading the forecast…",
        )
        return
    }
    val now = rememberNow()
    val nowSec = now.atZone(ZoneId.systemDefault()).toEpochSecond()
    Row(Modifier.width(892.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(572.dp).fillMaxHeight()) {
            Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(w.code, w.isDay, 58.dp, C.Text)
                Spacer(Modifier.width(16.dp))
                BasicText("${w.temp}°", style = type(64, 300, tracking = -2.5f, lineHeight = 64))
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(WeatherApi.describe(w.code), style = type(24, 500))
                    BasicText(
                        "Feels like ${w.feels}° · ${place.name.substringBefore(",")}",
                        style = type(18, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                if (w.wind >= 0) Stat(Ic.Wind, "${w.wind} mph")
                if (w.humidity >= 0) Stat(Ic.Droplet, "${w.humidity}%")
                if (w.uv >= 0) Stat(Ic.Sun, "UV ${w.uv}")
                val beforeRise = nowSec < w.sunrise
                if (w.sunset > 0) Stat(if (beforeRise) Ic.Sunrise else Ic.Sunset, c.timeOf(if (beforeRise) w.sunrise else w.sunset))
            }
            Spacer(Modifier.weight(1f))
            val hours = w.hours.filter { it.at + 3600 > nowSec }.take(10)
            val rainy = hours.any { it.pop >= 20 }
            Row {
                hours.forEachIndexed { i, h ->
                    Column(Modifier.width(57.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicText(
                            if (i == 0) "Now" else local(h.at).format(if (c.prefs.use24h) hour24 else hour12),
                            style = type(15, if (i == 0) 600 else 400, if (i == 0) C.Text else C.Muted),
                        )
                        Glyph(Ic.weather(h.code, h.isDay), 26.dp, C.Text)
                        BasicText("${h.temp}°", style = type(19, 500))
                        if (rainy) BasicText(if (h.pop >= 20) "${h.pop}%" else " ", style = type(13, 500, C.Accent))
                    }
                }
            }
        }
        VDivider()
        DaysColumn(w)
    }
}

@Composable
private fun Stat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Glyph(icon, 20.dp, C.Muted)
        BasicText(text, style = type(18, color = C.Text.copy(alpha = .86f)))
    }
}

@Composable
private fun RowScope.DaysColumn(w: Weather) {
    val days = w.days.take(5)
    if (days.isEmpty()) return
    val min = days.minOf { it.lo }
    val max = days.maxOf { it.hi }.coerceAtLeast(min + 1)
    val today = LocalDate.now().toString()
    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val isToday = d.date == today
            Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    if (isToday) "Today" else runCatching { LocalDate.parse(d.date).format(dowFmt) }.getOrDefault(""),
                    Modifier.width(62.dp), style = type(19, 500),
                )
                Glyph(Ic.weather(d.code, true), 24.dp, C.Text)
                Spacer(Modifier.width(10.dp))
                BasicText("${d.lo}°", Modifier.width(36.dp), style = type(18, color = C.Muted).copy(textAlign = TextAlign.End))
                Spacer(Modifier.width(10.dp))
                RangeBar(d.lo, d.hi, min, max, if (isToday) w.temp else null, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                BasicText("${d.hi}°", Modifier.width(36.dp), style = type(18))
            }
        }
    }
}

private val tempStops = listOf(
    20 to Color(0xFF8AB4FF), 40 to Color(0xFF5AA9FF), 55 to Color(0xFF6FD3C1),
    68 to Color(0xFFA8E06B), 78 to Color(0xFFF5D76E), 88 to Color(0xFFF5A25D), 100 to Color(0xFFF06B5D),
)

private fun tempColor(t: Int): Color {
    if (t <= tempStops.first().first) return tempStops.first().second
    tempStops.zipWithNext { (t0, c0), (t1, c1) -> if (t <= t1) return lerp(c0, c1, (t - t0).toFloat() / (t1 - t0)) }
    return tempStops.last().second
}

/** iOS-style range: the day's low–high on the week's scale, plus a dot for "now" on today. */
@Composable
private fun RangeBar(lo: Int, hi: Int, min: Int, max: Int, now: Int?, modifier: Modifier) {
    Spacer(
        modifier.height(8.dp).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = .1f), cornerRadius = r)
            fun x(t: Int) = (t - min).toFloat() / (max - min) * size.width
            val x0 = x(lo)
            val x1 = x(hi).coerceAtLeast(x0 + size.height)
            drawRoundRect(
                Brush.horizontalGradient(listOf(tempColor(lo), tempColor(hi)), startX = x0, endX = x1),
                topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = r,
            )
            if (now != null) {
                val cx = x(now.coerceIn(lo, hi)).coerceIn(size.height / 2, size.width - size.height / 2)
                drawCircle(C.Bg, size.height * .85f, Offset(cx, size.height / 2))
                drawCircle(C.Text, size.height * .55f, Offset(cx, size.height / 2))
            }
        },
    )
}

// ---------- Now playing ----------

@Composable
private fun NowPlayingCard(c: Controller, focused: Boolean) {
    val np = c.nowPlaying
    val artShape = RoundedCornerShape(18.dp)
    Row(Modifier.width(512.dp).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(
            Modifier.size(204.dp).clip(artShape).background(Brush.linearGradient(listOf(Color(0xFF2B4C5E), Color(0xFF0F1C24)))),
            contentAlignment = Alignment.Center,
        ) {
            val art = np?.art
            val icon = np?.pkg?.let { c.icons[it] }
            when {
                art != null -> Image(art, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                icon != null -> Image(icon, null, Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else -> Glyph(Ic.Music, 56.dp, C.Muted)
            }
        }
        Column(Modifier.fillMaxHeight().weight(1f)) {
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (np?.playing == true) Equalizer() else Glyph(Ic.Music, 18.dp, C.Muted)
                val source = np?.pkg?.let { p -> c.apps.firstOrNull { it.pkg == p }?.label } ?: "Now playing"
                BasicText(source, style = type(17, 500, C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            val (title, sub) = when {
                !c.mediaAccess -> "Not set up yet" to "Needs one-time access. Press OK for how."
                np == null -> "Nothing playing" to "Music and videos show up here."
                else -> np.title to np.subtitle
            }
            BasicText(title, style = type(28, 600, tracking = -.4f, lineHeight = 34), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(sub, style = type(19, color = C.Muted, lineHeight = 26), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            if (np != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(48.dp).background(if (focused) C.Accent else Color.White.copy(alpha = .12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(if (np.playing) Ic.Pause else Ic.Play, 22.dp, if (focused) C.OnAccent else C.Text)
                    }
                    BasicText(if (np.playing) "Pause" else "Play", style = type(18, color = C.Muted))
                }
            }
        }
    }
}

/** Three bouncing bars while something plays (still when ambient motion is off). */
@Composable
private fun Equalizer() {
    val m = LocalMotion.current
    Spacer(
        Modifier.size(18.dp, 16.dp).drawBehind {
            val w = size.width / 5
            for (k in 0..2) {
                val v = if (m.on) .35f + .65f * (.5f + .5f * m.wave(.9 + k * .23, k * 1.7)) else floatArrayOf(.6f, 1f, .45f)[k]
                val h = size.height * v
                drawRoundRect(C.Accent, Offset(k * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2))
            }
        },
    )
}

// ---------- Sun & air ----------

private fun aqiColor(a: Int): Color = when {
    a <= 50 -> Color(0xFF5CD08B)
    a <= 100 -> Color(0xFFF2D45C)
    a <= 150 -> Color(0xFFF59E4C)
    a <= 200 -> Color(0xFFEF5B5B)
    a <= 300 -> Color(0xFFA66CFF)
    else -> Color(0xFFB0325A)
}

@Composable
private fun SunCard(c: Controller) {
    val w = c.weather
    if (w == null || c.place == null) {
        Placeholder(380, Ic.Sunrise, "Sun & air", if (c.place == null) "Set your ZIP code first.\nPress OK to set it." else "Loading…")
        return
    }
    val now = rememberNow()
    Column(Modifier.width(422.dp).fillMaxHeight()) {
        Header(Ic.Sunrise, "Sun & air")
        Spacer(Modifier.height(14.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(236.dp)) {
                val nowSec = now.atZone(ZoneId.systemDefault()).toEpochSecond()
                SunArc(w.sunrise, w.sunset, nowSec)
                Spacer(Modifier.height(10.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        BasicText("Sunrise", style = type(15, color = C.Muted))
                        BasicText(c.timeOf(w.sunrise), style = type(22, 500))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        BasicText("Sunset", style = type(15, color = C.Muted))
                        BasicText(c.timeOf(w.sunset), style = type(22, 500))
                    }
                }
                if (w.sunset > w.sunrise && w.sunrise > 0) {
                    val mins = (w.sunset - w.sunrise) / 60
                    Spacer(Modifier.height(4.dp))
                    BasicText("${mins / 60}h ${mins % 60}m of daylight", style = type(15, color = C.Muted))
                }
            }
            VDivider()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("Air quality", style = type(15, color = C.Muted))
                val aqi = w.aqi
                BasicText(aqi?.toString() ?: "—", style = type(60, 300, tracking = -2f, lineHeight = 66))
                if (aqi != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(aqiColor(aqi), CircleShape))
                        BasicText(WeatherApi.aqiLabel(aqi), style = type(17, 500, lineHeight = 22))
                    }
                    BasicText("US AQI", style = type(14, color = C.Muted))
                }
            }
        }
    }
}

/** The sun's path from sunrise to sunset; the solid part is the day so far. */
@Composable
private fun SunArc(rise: Long, set: Long, now: Long) {
    Spacer(
        Modifier.width(236.dp).height(80.dp).drawBehind {
            val horizon = size.height - 4.dp.toPx()
            val rx = size.width / 2 - 10.dp.toPx()
            val ry = horizon - 8.dp.toPx()
            val cx = size.width / 2
            val oval = Rect(cx - rx, horizon - ry, cx + rx, horizon + ry)
            drawLine(Color.White.copy(alpha = .18f), Offset(0f, horizon), Offset(size.width, horizon), 1.5.dp.toPx())
            val dash = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 9f)))
            drawArc(Color.White.copy(alpha = .35f), 180f, 180f, false, oval.topLeft, oval.size, style = dash)
            if (set <= rise || rise == 0L) return@drawBehind
            val p = ((now - rise).toFloat() / (set - rise))
            if (p !in 0f..1f) return@drawBehind
            drawArc(
                Brush.horizontalGradient(listOf(C.Accent.copy(alpha = .3f), C.Text), startX = cx - rx, endX = cx - rx + 2 * rx * p),
                180f, 180f * p, false, oval.topLeft, oval.size, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val a = PI * (1 - p)
            val sun = Offset(cx + rx * cos(a).toFloat(), horizon - ry * sin(a).toFloat())
            drawCircle(C.Text.copy(alpha = .18f), 13.dp.toPx(), sun)
            drawCircle(C.Text, 6.5.dp.toPx(), sun)
        },
    )
}

// ---------- World clocks ----------

@Composable
private fun ClocksCard(c: Controller) {
    val cities = c.prefs.cities.mapNotNull { n -> Cities.firstOrNull { it.name == n } }
    if (cities.isEmpty()) {
        Placeholder(340, Ic.Globe, "World clocks", "No cities yet.\nPress OK to pick up to three.")
        return
    }
    val now = rememberNow()
    val here = now.atZone(ZoneId.systemDefault())
    Column(Modifier.width(372.dp).fillMaxHeight()) {
        Header(Ic.Globe, "World clocks")
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            cities.forEach { city ->
                val there = here.withZoneSameInstant(ZoneId.of(city.zone))
                val diffMin = (there.offset.totalSeconds - here.offset.totalSeconds) / 60
                val day = when (there.toLocalDate().compareTo(here.toLocalDate())) {
                    0 -> "Today"
                    in 1..Int.MAX_VALUE -> "Tomorrow"
                    else -> "Yesterday"
                }
                val diff = when {
                    diffMin == 0 -> "same time"
                    diffMin % 60 == 0 -> "%+d hrs".format(diffMin / 60)
                    else -> "%+.1f hrs".format(diffMin / 60f)
                }
                Row(Modifier.height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (there.hour in 6..17) Ic.Sun else Ic.Moon, 22.dp, C.Muted)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(city.name, style = type(22, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText("$day · $diff", style = type(15, color = C.Muted))
                    }
                    if (c.prefs.use24h) {
                        BasicText(there.format(DateTimeFormatter.ofPattern("H:mm")), style = type(36, 300, tracking = -1f))
                    } else {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            BasicText(there.format(DateTimeFormatter.ofPattern("h:mm")), style = type(36, 300, tracking = -1f, lineHeight = 40))
                            BasicText(there.format(DateTimeFormatter.ofPattern("a")), Modifier.padding(bottom = 5.dp), style = type(15, 500, C.Muted))
                        }
                    }
                }
            }
        }
    }
}

// ---------- Connections ----------

@Composable
private fun ConnectionsCard(c: Controller) {
    val n = c.net
    Column(Modifier.width(422.dp).fillMaxHeight()) {
        Header(Ic.Wifi, "Connections") {
            val (label, color) = when {
                n == null || n.kind == NetKind.Offline -> "Offline" to Color(0xFFEF5B5B)
                n.online -> "Online" to Color(0xFF5CD08B)
                else -> "No internet" to Color(0xFFF2D45C)
            }
            Box(Modifier.size(9.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            BasicText(label, style = type(16, 500, C.Text.copy(alpha = .86f)))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (n?.kind) {
                NetKind.WiFi -> Ic.Wifi
                NetKind.Ethernet, NetKind.Other -> Ic.Network
                else -> Ic.WifiOff
            }
            Glyph(icon, 30.dp, C.Text)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val title = when (n?.kind) {
                    NetKind.WiFi -> listOfNotNull("Wi-Fi", n.band).joinToString(" · ")
                    NetKind.Ethernet -> "Ethernet"
                    NetKind.Other -> "Connected"
                    else -> "Not connected"
                }
                BasicText(title, style = type(22, 500))
                val detail = listOfNotNull(n?.ip, n?.mbps?.let { "$it Mbps" }).joinToString(" · ")
                if (detail.isNotEmpty()) BasicText(detail, style = type(16, color = C.Muted))
            }
            if (n?.kind == NetKind.WiFi && n.signal >= 0) SignalBars(n.signal)
        }
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
        BtList(c.bt)
    }
}

@Composable
private fun SignalBars(level: Int) {
    Spacer(
        Modifier.size(30.dp, 22.dp).drawBehind {
            val w = 5.dp.toPx()
            val gap = (size.width - 4 * w) / 3
            for (k in 0..3) {
                val h = size.height * (k + 1) / 4f
                drawRoundRect(
                    if (k < level) C.Text else Color.White.copy(alpha = .2f),
                    Offset(k * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2),
                )
            }
        },
    )
}

private fun BtKind.icon(): ImageVector = when (this) {
    BtKind.Remote -> Ic.Remote
    BtKind.Gamepad -> Ic.Gamepad
    BtKind.Headphones -> Ic.Headphones
    BtKind.Speaker -> Ic.Speaker
    BtKind.Keyboard -> Ic.Keyboard
    BtKind.Other -> Ic.Bluetooth
}

@Composable
private fun ColumnScope.BtList(state: BtState) {
    @Composable
    fun row(icon: ImageVector, text: String, muted: Boolean, trailing: String? = null) {
        Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(icon, 20.dp, if (muted) C.Muted else C.Text)
            Spacer(Modifier.width(14.dp))
            BasicText(
                text, Modifier.weight(1f), style = type(19, color = if (muted) C.Muted else C.Text),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) BasicText(trailing, style = type(17, color = C.Muted))
        }
    }
    when (state) {
        BtState.Unavailable -> row(Ic.Bluetooth, "Bluetooth isn't available", true)
        BtState.NoPermission -> row(Ic.Bluetooth, "Press OK to show Bluetooth devices", true)
        BtState.Off -> row(Ic.Bluetooth, "Bluetooth is off", true)
        is BtState.On -> {
            val list = state.connected
            if (list.isEmpty()) row(Ic.Bluetooth, "No Bluetooth devices connected", true)
            val shown = if (list.size > 3) list.take(2) else list
            shown.forEach { d -> row(d.kind.icon(), d.name, false, d.battery?.let { "$it%" }) }
            if (list.size > 3) row(Ic.Bluetooth, "+${list.size - 2} more", true)
        }
    }
}

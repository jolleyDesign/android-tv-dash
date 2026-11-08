package dev.jolley.dash.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jolley.dash.Controller
import dev.jolley.dash.data.ClockFace
import dev.jolley.dash.data.WeatherApi
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Wall-clock time, ticking on each minute boundary. */
@Composable
fun rememberNow(): LocalDateTime {
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
private val dayFmt = DateTimeFormatter.ofPattern("EEEE")
private val monthDayFmt = DateTimeFormatter.ofPattern("MMMM d")
private val time12 = DateTimeFormatter.ofPattern("h:mm")
private val time24 = DateTimeFormatter.ofPattern("H:mm")

/** The top-left clock. Every face stays under ~200dp tall so the widget row below never moves. */
@Composable
fun Clock(c: Controller, modifier: Modifier = Modifier) {
    val now = rememberNow()
    Crossfade(c.prefs.face, modifier, tween(260), label = "face") { face ->
        when (face) {
            ClockFace.Thin, ClockFace.Bold, ClockFace.Words -> Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = if (c.prefs.centered) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    when (face) {
                        ClockFace.Bold -> RollingText(digital(c, now), wall(128, 700, tracking = -6f, lineHeight = 124))
                        ClockFace.Words -> WordClock(now)
                        else -> RollingText(digital(c, now), wall(120, 300, tracking = -4.8f, lineHeight = 120))
                    }
                    WeatherBeside(c)
                }
                BasicText(now.format(dateFmt), style = wall(24, color = C.Muted, tracking = .5f))
            }
            ClockFace.Analog, ClockFace.Stacked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                if (face == ClockFace.Analog) AnalogFace(now, 184.dp) else Stacked(c, now)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(now.format(dayFmt), style = wall(40, 500, tracking = -.8f))
                    BasicText(now.format(monthDayFmt), style = wall(26, color = C.Muted))
                    Spacer(Modifier.size(6.dp))
                    WeatherInline(c)
                }
            }
        }
    }
}

private fun digital(c: Controller, now: LocalDateTime) = now.format(if (c.prefs.use24h) time24 else time12)

@Composable
private fun WeatherBeside(c: Controller) {
    val w = c.weather ?: return
    if (c.place == null || !c.prefs.clockWeather) return
    Column(
        Modifier
            .drawBehind { drawLine(C.Bezel, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
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

@Composable
private fun WeatherInline(c: Controller) {
    val w = c.weather ?: return
    if (c.place == null || !c.prefs.clockWeather) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WeatherIcon(w.code, w.isDay, 30.dp, C.Text)
        BasicText("${w.temp}°", style = wall(28, 400))
        BasicText(WeatherApi.describe(w.code), style = wall(22, color = C.Muted))
    }
}

// ---------- Stacked ----------

@Composable
private fun Stacked(c: Controller, now: LocalDateTime) {
    val h = if (c.prefs.use24h) now.hour else (now.hour + 11) % 12 + 1
    Column {
        Box(Modifier.capCrop(STACK_SP)) { RollingText("%02d".format(h), wall(STACK_SP, 600, tracking = -4f)) }
        Box(Modifier.capCrop(STACK_SP)) { RollingText("%02d".format(now.minute), wall(STACK_SP, 600, C.Accent, tracking = -4f)) }
    }
}

private const val STACK_SP = 104

/**
 * Shrinks a line of digits to its cap height plus [pad] each side. Font line boxes carry ascender
 * and descender room digits never use, which would push the stack into the widget row.
 */
private fun Modifier.capCrop(sizeSp: Int, pad: Dp = 9.dp) = layout { m, c ->
    val p = m.measure(c)
    val base = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: (p.height * .78f).roundToInt()
    val top = (base - (sizeSp.sp.toPx() * .72f).roundToInt() - pad.roundToPx()).coerceAtLeast(0)
    val h = (base + pad.roundToPx() - top).coerceIn(0, p.height - top)
    layout(p.width, h) { p.place(0, -top) }
}

// ---------- Words ----------

private val ones = listOf(
    "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
    "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
)

private fun number(n: Int) = if (n <= 20) ones[n] else "twenty-" + ones[n - 20]

private fun hourWord(h24: Int) = ones[(h24 + 11) % 12 + 1]

/** "Quarter past nine", "Nineteen to ten", "Noon". The hour is the emphasized part. */
private fun phrase(t: LocalDateTime): List<Pair<String, Boolean>> {
    val h = t.hour
    val m = t.minute
    val next = (h + 1) % 24
    return when {
        m == 0 && h == 0 -> listOf("midnight" to true)
        m == 0 && h == 12 -> listOf("noon" to true)
        m == 0 -> listOf(hourWord(h) to true, " o'clock" to false)
        m == 15 -> listOf("quarter past " to false, hourWord(h) to true)
        m == 30 -> listOf("half past " to false, hourWord(h) to true)
        m == 45 -> listOf("quarter to " to false, hourWord(next) to true)
        m < 30 -> listOf("${number(m)} past " to false, hourWord(h) to true)
        else -> listOf("${number(60 - m)} to " to false, hourWord(next) to true)
    }.let { parts -> parts.mapIndexed { i, (s, b) -> (if (i == 0) s.replaceFirstChar { it.uppercase() } else s) to b } }
}

@Composable
private fun WordClock(now: LocalDateTime) {
    val text = buildAnnotatedString {
        phrase(now).forEach { (s, bold) ->
            withStyle(SpanStyle(fontWeight = FontWeight(if (bold) 600 else 300), color = if (bold) C.Text else C.Text.copy(alpha = .78f))) {
                append(s)
            }
        }
    }
    val style = wall(64, 300, tracking = -1.6f, lineHeight = 68)
    if (LocalMotion.current.on) {
        Crossfade(text, Modifier.widthIn(max = 640.dp), tween(600), label = "words") { BasicText(it, style = style) }
    } else {
        BasicText(text, Modifier.widthIn(max = 640.dp), style = style)
    }
}

// ---------- Analog ----------

/**
 * With ambient motion on, reading [Motion.t] redraws every frame, so the hands sweep and an
 * accent second hand appears. Off, it repaints once a minute like every other face.
 */
@Composable
private fun AnalogFace(now: LocalDateTime, size: Dp) {
    val m = LocalMotion.current
    Spacer(
        Modifier.size(size).drawBehind {
            val live = m.on
            val t = if (live) {
                m.t
                LocalTime.now()
            } else now.toLocalTime()
            val r = this.size.minDimension / 2
            drawCircle(Color.Black.copy(alpha = .28f), r)
            drawCircle(Color.White.copy(alpha = .16f), r - .75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            for (i in 0 until 12) {
                val major = i % 3 == 0
                val a = i * PI / 6
                tick(a, r * (if (major) .76f else .83f), r * .9f, if (major) 3.dp.toPx() else 2.dp.toPx(), if (major) 1f else .45f)
            }
            val sec = t.second + t.nano / 1e9
            val min = t.minute + sec / 60
            val hr = t.hour % 12 + min / 60
            hand(hr * PI / 6, r * .5f, 7.dp.toPx(), C.Text)
            hand(min * PI / 30, r * .74f, 4.5.dp.toPx(), C.Text)
            if (live) hand(sec * PI / 30, r * .84f, 2.dp.toPx(), C.Accent, tail = r * .16f)
            drawCircle(C.Accent, 5.5.dp.toPx())
            drawCircle(C.Bg, 2.dp.toPx())
        },
    )
}

private fun DrawScope.tick(a: Double, from: Float, to: Float, w: Float, alpha: Float) {
    val dx = sin(a).toFloat()
    val dy = -cos(a).toFloat()
    drawLine(C.Text, center + Offset(dx * from, dy * from), center + Offset(dx * to, dy * to), w, StrokeCap.Round, alpha = alpha)
}

private fun DrawScope.hand(a: Double, len: Float, w: Float, color: Color, tail: Float = 0f) {
    val d = Offset(sin(a).toFloat(), -cos(a).toFloat())
    val start = center - d * tail
    val end = center + d * len
    val shadow = Offset(0f, 2.dp.toPx())
    drawLine(Color.Black.copy(alpha = .35f), start + shadow, end + shadow, w, StrokeCap.Round)
    drawLine(color, start, end, w, StrokeCap.Round)
}

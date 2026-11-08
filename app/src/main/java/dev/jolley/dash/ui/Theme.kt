@file:OptIn(ExperimentalTextApi::class)

package dev.jolley.dash.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jolley.dash.R

object C {
    val Bg = Color(0xFF0B0B0A)
    val Panel = Color(0xFF1A1917)
    val Text = Color(0xFFF2EEE6)
    val Muted = Color(0xFFA39D92)
    val Accent = Color(0xFF5AA9FF)
    val OnAccent = Color(0xFF04121F)
    val Bezel = Color(0xFF3A3732)
}

val Ease = CubicBezierEasing(.2f, .8f, .2f, 1f)

val Geist = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { w ->
        Font(R.font.geist, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

fun type(size: Int, weight: Int = 400, color: Color = C.Text, tracking: Float = 0f, lineHeight: Int? = null) = TextStyle(
    fontFamily = Geist,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    color = color,
    letterSpacing = tracking.sp,
    lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
)

val Eyebrow = type(22, 500, C.Muted)

// Soft halo so text stays legible over bright photo wallpapers; barely visible on dark ones.
val WallShadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), Offset(0f, 2f), 18f)
// Small text needs a tight, dark shadow; a wide soft halo barely separates it from a bright photo.
val WallShadowTight = androidx.compose.ui.graphics.Shadow(Color(0xE6000000), Offset(0f, 1.5f), 6f)

fun wall(size: Int, weight: Int = 400, color: Color = C.Text, tracking: Float = 0f, lineHeight: Int? = null) =
    type(size, weight, color, tracking, lineHeight).copy(shadow = if (size <= 40) WallShadowTight else WallShadow)

/** Focus ring drawn outside the element so it never shifts layout; breathes while the user is active. */
@Composable
fun Modifier.ring(alpha: () -> Float, shape: Shape, corner: Dp = 0.dp): Modifier {
    val m = LocalMotion.current
    return drawBehind { drawRing(Rect(Offset.Zero, size), shape == CircleShape, corner, alpha(), m.breath()) }
}

// ---------- Icons (Lucide paths, MIT) ----------

private fun lucide(name: String, vararg d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach {
            addPath(
                addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private const val CLOUD_BASE = "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"

object Ic {
    val Sun by lazy {
        lucide(
            "sun", "M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41",
        )
    }
    val Moon by lazy { lucide("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z") }
    val Cloud by lazy { lucide("cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z") }
    val CloudSun by lazy {
        lucide(
            "cloud-sun", "M12 2v2", "m4.93 4.93 1.41 1.41", "M20 12h2", "m19.07 4.93-1.41 1.41",
            "M15.947 12.65a4 4 0 0 0-5.925-4.128", "M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z",
        )
    }
    val CloudMoon by lazy {
        lucide("cloud-moon", "M10.188 8.5A6 6 0 0 1 16 4a1 1 0 0 0 6 6 6 6 0 0 1-3 5.197", "M13 16a3 3 0 1 1 0 6H7a5 5 0 1 1 4.9-6Z")
    }
    val Fog by lazy { lucide("cloud-fog", CLOUD_BASE, "M16 17H7", "M17 21H9") }
    val Drizzle by lazy { lucide("cloud-drizzle", CLOUD_BASE, "M8 19v1", "M8 14v1", "M16 19v1", "M16 14v1", "M12 21v1", "M12 16v1") }
    val Rain by lazy { lucide("cloud-rain", CLOUD_BASE, "M16 14v6", "M8 14v6", "M12 16v6") }
    val Snow by lazy {
        lucide("cloud-snow", CLOUD_BASE, "M8 15h.01", "M8 19h.01", "M12 17h.01", "M12 21h.01", "M16 15h.01", "M16 19h.01")
    }
    val Storm by lazy { lucide("cloud-lightning", "M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973", "m13 12-3 5h4l-3 5") }
    val Tv by lazy { lucide("tv", "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "m17 2-5 5-5-5") }
    val Wifi by lazy { lucide("wifi", "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0") }
    val Gear by lazy {
        lucide(
            "settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0",
        )
    }
    val Plus by lazy { lucide("plus", "M5 12h14", "M12 5v14") }
    val Minus by lazy { lucide("minus", "M5 12h14") }
    val ChevronDown by lazy { lucide("chevron-down", "m6 9 6 6 6-6") }
    val ChevronUp by lazy { lucide("chevron-up", "m18 15-6-6-6 6") }
    val ChevronLeft by lazy { lucide("chevron-left", "m15 18-6-6 6-6") }
    val ChevronRight by lazy { lucide("chevron-right", "m9 18 6-6-6-6") }
    val ArrowsLR by lazy { lucide("arrow-left-right", "M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4") }
    val EyeOff by lazy {
        lucide(
            "eye-off", "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20",
        )
    }
    val Info by lazy { lucide("info", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", "M12 16v-4", "M12 8h.01") }
    val Trash by lazy { lucide("trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2") }
    val Delete by lazy {
        lucide(
            "delete", "M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z",
            "m12 9 6 6", "m18 9-6 6",
        )
    }
    val Check by lazy { lucide("check", "M20 6 9 17l-5-5") }
    val ImagePlus by lazy {
        lucide(
            "image-plus", "M16 5h6", "M19 2v6", "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21", "M11 9a2 2 0 1 1-4 0 2 2 0 0 1 4 0",
        )
    }

    val Bluetooth by lazy { lucide("bluetooth", "m7 7 10 10-5 5V2l5 5L7 17") }
    val Play by lazy { lucide("play", "M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z") }
    val Pause by lazy {
        lucide(
            "pause", "M15 4h3a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
            "M6 4h3a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        )
    }
    val Music by lazy { lucide("music", "M9 18V5l12-2v13", "M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0", "M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0") }
    val Headphones by lazy {
        lucide("headphones", "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3")
    }
    val Gamepad by lazy {
        lucide(
            "gamepad-2", "M6 11h4", "M8 9v4", "M15 12h.01", "M18 10h.01",
            "M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z",
        )
    }
    val Keyboard by lazy {
        lucide(
            "keyboard", "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
            "M6 10h.01", "M10 10h.01", "M14 10h.01", "M18 10h.01", "M8 14h8",
        )
    }
    val Remote by lazy {
        lucide(
            "remote", "M9.5 2h5a2.5 2.5 0 0 1 2.5 2.5v15a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 19.5v-15A2.5 2.5 0 0 1 9.5 2z",
            "M14 8a2 2 0 1 1-4 0 2 2 0 0 1 4 0", "M12 14h.01", "M12 18h.01",
        )
    }
    val Speaker by lazy {
        lucide(
            "speaker", "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 6h.01",
            "M16 14a4 4 0 1 1-8 0 4 4 0 0 1 8 0", "M12 14h.01",
        )
    }
    val Sunrise by lazy {
        lucide(
            "sunrise", "M12 2v8", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41", "M22 22H2",
            "m8 6 4-4 4 4", "M16 18a4 4 0 0 0-8 0",
        )
    }
    val Sunset by lazy {
        lucide(
            "sunset", "M12 10V2", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41", "M22 22H2",
            "m16 6-4 4-4-4", "M16 18a4 4 0 0 0-8 0",
        )
    }
    val Wind by lazy { lucide("wind", "M12.8 19.6A2 2 0 1 0 14 16H2", "M17.5 8a2.5 2.5 0 1 1 2 4H2", "M9.8 4.4A2 2 0 1 1 11 8H2") }
    val Droplet by lazy {
        lucide("droplet", "M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z")
    }
    val Thermometer by lazy { lucide("thermometer", "M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z") }
    val Globe by lazy {
        lucide("globe", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20")
    }
    val Network by lazy {
        lucide(
            "network", "M10 2h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
            "M4 16h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z",
            "M16 16h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z", "M6 16v-3h12v3", "M12 8v5",
        )
    }
    val WifiOff by lazy {
        lucide(
            "wifi-off", "M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523", "M2 8.82a15 15 0 0 1 4.177-2.643", "M22 8.82a15 15 0 0 0-11.288-3.764", "m2 2 20 20",
        )
    }
    val Leaf by lazy {
        lucide(
            "leaf", "M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z",
            "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12",
        )
    }

    fun weather(code: Int, isDay: Boolean): ImageVector = when (code) {
        0, 1 -> if (isDay) Sun else Moon
        2 -> if (isDay) CloudSun else CloudMoon
        3 -> Cloud
        45, 48 -> Fog
        in 51..57 -> Drizzle
        in 61..67, in 80..82 -> Rain
        in 71..77, 85, 86 -> Snow
        in 95..99 -> Storm
        else -> Cloud
    }
}

@Composable
fun Glyph(v: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(v), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

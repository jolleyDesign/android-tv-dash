@file:OptIn(ExperimentalFoundationApi::class)

package dev.jolley.dash.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jolley.dash.Action
import dev.jolley.dash.Controller
import dev.jolley.dash.GRID_COLUMNS
import dev.jolley.dash.MenuTarget
import dev.jolley.dash.Overlay
import dev.jolley.dash.PhoneMode
import dev.jolley.dash.SettingItem
import dev.jolley.dash.WALL_COLS
import dev.jolley.dash.WALL_ROWS
import dev.jolley.dash.ZIP_KEY_DELETE
import dev.jolley.dash.ZIP_KEY_DONE
import dev.jolley.dash.data.Cities
import dev.jolley.dash.data.MAX_CITIES
import dev.jolley.dash.data.Wall
import dev.jolley.dash.data.WidgetKind
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.nayuki.qrcodegen.QrCode
import kotlin.math.roundToInt

/** Keeps the last value of a given overlay type alive so exit animations have content to draw. */
@Composable
private inline fun <reified T : Overlay> rememberLast(current: Overlay): T? {
    val holder = remember { arrayOfNulls<Overlay>(1) }
    if (current is T) holder[0] = current
    return holder[0] as T?
}

// ---------- All apps ----------

@Composable
fun AppsSheet(c: Controller) {
    val o = c.overlay
    val apps = (o as? Overlay.Apps) ?: ((o as? Overlay.Menu)?.under as? Overlay.Apps)
    val last = rememberLast<Overlay.Apps>(apps ?: Overlay.None)
    AnimatedVisibility(
        visible = apps != null,
        enter = slideInVertically(tween(340, easing = Ease)) { it },
        exit = slideOutVertically(tween(240)) { it },
    ) {
        val index = (apps ?: last)?.index ?: 0
        val focusable = o is Overlay.Apps
        Box(Modifier.fillMaxSize()) {
            Wallpaper(c)
            Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = .86f)))
            Box(Modifier.absoluteOffset(y = 20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Glyph(Ic.ChevronUp, 28.dp, C.Muted)
            }
            Row(Modifier.absoluteOffset(96.dp, 72.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BasicText("All apps", style = type(56, 600, tracking = -1.7f, lineHeight = 64))
                val hiddenCount = c.hidden.count { h -> c.apps.any { it.pkg == h } }
                BasicText(
                    "${c.visibleApps.size} apps" + if (hiddenCount > 0) " · $hiddenCount hidden" else "",
                    Modifier.padding(bottom = 8.dp),
                    style = type(22, color = C.Muted),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.absoluteOffset(y = 168.dp).fillMaxWidth().height(820.dp),
                contentPadding = PaddingValues(start = 96.dp, end = 96.dp, top = 32.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(44.dp),
            ) {
                itemsIndexed(c.visibleApps, key = { _, a -> a.pkg }) { i, a ->
                    AppCell(c, a.pkg, a.label, i, focused = focusable && i == index || o is Overlay.Menu && i == index, inDock = a.pkg in c.dock)
                }
            }
            KeyHints(listOf("OK" to "open", "Hold OK" to "options", "Back" to "close"), Modifier.absoluteOffset(y = 1030.dp))
        }
    }
}

@Composable
private fun AppCell(c: Controller, pkg: String, label: String, i: Int, focused: Boolean, inDock: Boolean) {
    val f by focusAnim(focused)
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bring.bringIntoView() }
    Column(
        Modifier.bringIntoViewRequester(bring).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .graphicsLayer { val s = 1 + .08f * f; scaleX = s; scaleY = s }
                    .ring({ f }, CircleShape)
                    .track(c, "grid:$i")
                    .size(112.dp)
                    .clip(CircleShape)
                    .sheen(focused),
            ) { AppDisc(c, pkg, label, 112.dp) }
            if (inDock) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(26.dp)
                        .background(Color(0xFF121110), CircleShape).padding(4.dp).background(C.Accent, CircleShape),
                )
            }
        }
        BasicText(
            label,
            style = if (focused) type(20, 500) else type(20, color = C.Muted),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// ---------- Options menu ----------

private fun Action.icon(): ImageVector = when (this) {
    Action.Move, Action.MoveShow -> Ic.ArrowsLR
    Action.AddToDock -> Ic.Plus
    Action.RemoveFromDock -> Ic.Minus
    Action.Hide -> Ic.EyeOff
    Action.Info -> Ic.Info
    Action.RemoveShow, Action.RemoveWidget -> Ic.Trash
    Action.MoveWidget -> Ic.ArrowsLR
    Action.OpenApp -> Ic.Play
    Action.BtSettings -> Ic.Bluetooth
}

@Composable
fun MenuLayer(c: Controller) {
    val o = c.overlay as? Overlay.Menu
    val last = rememberLast<Overlay.Menu>(c.overlay)
    AnimatedVisibility(
        visible = o != null,
        enter = fadeIn(tween(160)) + scaleIn(tween(200, easing = Ease), initialScale = .96f),
        exit = fadeOut(tween(120)),
    ) {
        val m = o ?: last ?: return@AnimatedVisibility
        val key = when (val t = m.target) {
            is MenuTarget.App -> t.anchorKey
            is MenuTarget.Show -> t.anchorKey
            is MenuTarget.Widget -> t.anchorKey
        }
        val anchor = c.bounds[key]
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .layout { meas, cons ->
                        val p = meas.measure(cons.copy(minWidth = 0, minHeight = 0))
                        layout(cons.maxWidth, cons.maxHeight) {
                            val a = anchor
                            val margin = 24.dp.roundToPx()
                            if (a == null) {
                                p.place((cons.maxWidth - p.width) / 2, (cons.maxHeight - p.height) / 2)
                            } else {
                                val cx = (a.center.x * density).roundToInt()
                                val x = (cx - p.width / 2).coerceIn(margin, cons.maxWidth - p.width - margin)
                                val above = (a.top * density).roundToInt() - p.height - 2 * margin
                                val y = if (above >= margin) above else (a.bottom * density).roundToInt() + margin
                                p.place(x, y)
                            }
                        }
                    }
                    .width(360.dp)
                    .background(C.Panel, RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(24.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                m.actions.forEachIndexed { i, a ->
                    val sel = i == m.index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (sel) C.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Glyph(a.icon(), 24.dp, if (sel) C.OnAccent else C.Muted)
                        BasicText(a.label, Modifier.weight(1f), style = type(22, 500, if (sel) C.OnAccent else C.Text))
                        if (a == Action.Move || a == Action.MoveShow || a == Action.MoveWidget) {
                            BasicText("◀ ▶", style = type(16, color = if (sel) C.OnAccent.copy(alpha = .7f) else C.Muted))
                        }
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------

@Composable
fun SettingsPanel(c: Controller) {
    val o = c.overlay
    val s = (o as? Overlay.Settings)
        ?: ((o as? Overlay.Phone)?.returnTo as? Overlay.Settings)
        ?: ((o as? Overlay.Notice)?.returnTo as? Overlay.Settings)
    val last = rememberLast<Overlay.Settings>(s ?: Overlay.None)
    AnimatedVisibility(
        visible = s != null,
        enter = slideInHorizontally(tween(320, easing = Ease)) { it } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(160)),
    ) {
        val st = s ?: last ?: return@AnimatedVisibility
        val active = o is Overlay.Settings
        val page = when {
            st.zip != null -> 1
            st.hiddenIndex != null -> 2
            st.cityIndex != null -> 4
            st.widgetsIndex != null -> 3
            else -> 0
        }
        Box(Modifier.fillMaxSize()) {
            // Keyed by page so each page opens scrolled to the top.
            key(page) {
                val scroll = rememberScrollState()
                if (page == 0) LaunchedEffect(st.row == 0) { if (st.row == 0) scroll.animateScrollTo(0) }
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(680.dp)
                        .fillMaxHeight()
                        .background(Color(0xF0141311))
                        .drawBehind { drawLine(Color.White.copy(alpha = .06f), Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) }
                        .verticalScroll(scroll)
                        .padding(horizontal = 48.dp, vertical = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(36.dp),
                ) {
                    when (page) {
                        1 -> ZipEntry(c, st.zip!!, st.key, active)
                        2 -> HiddenApps(c, st.hiddenIndex!!, active)
                        3 -> WidgetsPage(c, st.widgetsIndex!!, active)
                        4 -> CitiesPage(c, st.cityIndex!!, active)
                        else -> SettingsRows(c, st, active)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRows(c: Controller, st: Overlay.Settings, active: Boolean) {
    BasicText("Launcher", Modifier.padding(start = 16.dp), style = type(48, 600, tracking = -1.4f))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BasicText("Wallpaper", style = Eyebrow)
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Wall.entries.chunked(WALL_COLS).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEachIndexed { col, w ->
                        Swatch(c, w, focused = active && st.row == r && st.col == col, selected = c.prefs.wall == w)
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val p = c.prefs
        fun on(item: SettingItem) = active && st.row == WALL_ROWS + item.ordinal
        SettingRow("Clock", on(SettingItem.Clock)) { Segmented(listOf("12h", "24h"), if (p.use24h) 1 else 0) }
        SettingRow("Clock face", on(SettingItem.Face)) { Stepper(p.face.label, on(SettingItem.Face)) }
        SettingRow("Weather by clock", on(SettingItem.ClockWeather)) { Toggle(p.clockWeather) }
        SettingRow("Layout", on(SettingItem.Layout)) { Segmented(listOf("Left", "Centered"), if (p.centered) 1 else 0) }
        SettingRow("Weather", on(SettingItem.Weather)) {
            Value((c.place?.name ?: "Set ZIP code") + " · °F  ›")
        }
        SettingRow("Widgets", on(SettingItem.Widgets)) {
            Value((if (p.widgets.isEmpty()) "Off" else "${p.widgets.size} on") + "  ›")
        }
        SettingRow("Recent apps in dock", on(SettingItem.Recents)) {
            if (c.usageAccess) Toggle(p.showRecents) else Value("Needs access  ›")
        }
        SettingRow("Pinned shows row", on(SettingItem.Shows)) { Toggle(p.showShows) }
        SettingRow("Bluetooth in dock", on(SettingItem.Bluetooth)) { Toggle(p.btInDock) }
        SettingRow("Ambient motion", on(SettingItem.Motion)) { Toggle(p.motion) }
        SettingRow("Hidden apps", on(SettingItem.Hidden)) { Value("${c.hiddenApps().size}  ›") }
        SettingRow("Android settings", on(SettingItem.Android)) { Value("›") }
        SettingRow("Restore Google TV home", on(SettingItem.Restore)) { Value("›") }
    }
}

@Composable
private fun Swatch(c: Controller, w: Wall, focused: Boolean, selected: Boolean) {
    val f by focusAnim(focused)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .graphicsLayer { val s = 1 + .08f * f; scaleX = s; scaleY = s }
            .ring({ f }, shape, 20.dp)
            .size(96.dp)
            .clip(shape)
            .then(
                when (w) {
                    Wall.Custom -> Modifier.background(Color.White.copy(alpha = .06f)).drawBehind {
                        drawRoundRect(
                            C.Bezel, cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    Wall.Black -> Modifier.background(C.Bg).border(1.dp, C.Bezel, shape)
                    else -> Modifier.background(C.Panel)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val img = if (w == Wall.Custom) c.wallpaper.takeIf { c.prefs.wall == Wall.Custom } else c.wallThumbs[w]
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (w == Wall.Custom) Glyph(Ic.ImagePlus, 32.dp, C.Muted)
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp)
                    .background(C.Bg, CircleShape).padding(3.dp).background(C.Accent, CircleShape),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, focused: Boolean, value: @Composable () -> Unit) {
    val f by focusAnim(focused)
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) { if (focused) bring.bringIntoView() }
    Row(
        Modifier
            .bringIntoViewRequester(bring)
            .fillMaxWidth()
            .drawBehind {
                if (f > 0f) drawRoundRect(Color.White.copy(alpha = .08f * f), cornerRadius = CornerRadius(14.dp.toPx()))
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = type(26, 500))
        value()
    }
}

@Composable
private fun Value(text: String) = BasicText(text, style = type(22, color = C.Muted))

/** A value changed with ◀ ▶ (OK also steps forward). Arrows only show while focused. */
@Composable
private fun Stepper(text: String, focused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Glyph(Ic.ChevronLeft, 24.dp, if (focused) C.Muted else Color.Transparent)
        BasicText(text, Modifier.width(110.dp), style = type(22, 500).copy(textAlign = TextAlign.Center))
        Glyph(Ic.ChevronRight, 24.dp, if (focused) C.Muted else Color.Transparent)
    }
}

@Composable
private fun PageTitle(title: String, sub: String) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, style = type(48, 600, tracking = -1.4f))
        BasicText(sub, style = type(22, color = C.Muted, lineHeight = 30))
    }
}

@Composable
private fun WidgetsPage(c: Controller, index: Int, active: Boolean) {
    PageTitle("Widgets", "Shown above your pinned shows. Hold OK on a widget to move or remove it.")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WidgetKind.entries.forEachIndexed { i, k ->
            SettingRow(k.label, active && index == i) { Toggle(k in c.widgets) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
    SettingRow("World clock cities", active && index == WidgetKind.entries.size) {
        Value(c.prefs.cities.ifEmpty { listOf("None") }.joinToString(", ") + "  ›")
    }
}

@Composable
private fun CitiesPage(c: Controller, index: Int, active: Boolean) {
    PageTitle("World clocks", "Pick up to $MAX_CITIES cities.")
    val now = rememberNow()
    val fmt = remember(c.prefs.use24h) { DateTimeFormatter.ofPattern(if (c.prefs.use24h) "H:mm" else "h:mm a") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Cities.forEachIndexed { i, city ->
            val on = city.name in c.prefs.cities
            SettingRow(city.name, active && index == i) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val t = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(city.zone))
                    BasicText(t.format(fmt), style = type(20, color = C.Muted))
                    Glyph(Ic.Check, 26.dp, if (on) C.Accent else Color.Transparent)
                }
            }
        }
    }
}

@Composable
private fun Toggle(on: Boolean) {
    Box(
        Modifier.size(64.dp, 36.dp).background(if (on) C.Accent else Color.White.copy(alpha = .14f), RoundedCornerShape(50))
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(28.dp).background(if (on) C.OnAccent else C.Muted, CircleShape))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int) {
    Row(Modifier.background(Color.White.copy(alpha = .06f), RoundedCornerShape(50)).padding(4.dp)) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.background(if (on) C.Text else Color.Transparent, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                BasicText(s, style = type(20, if (on) 600 else 400, if (on) C.Bg else C.Muted))
            }
        }
    }
}

@Composable
private fun HiddenApps(c: Controller, index: Int, active: Boolean) {
    BasicText("Hidden apps", Modifier.padding(start = 16.dp), style = type(48, 600, tracking = -1.4f))
    val list = c.hiddenApps()
    if (list.isEmpty()) {
        BasicText("Nothing hidden.", Modifier.padding(start = 16.dp), style = type(22, color = C.Muted))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.forEachIndexed { i, a ->
            val focused = active && i == index
            SettingRow(a.label, focused) { Value(if (focused) "OK to unhide" else "") }
        }
    }
}

// ---------- ZIP keypad ----------

private val zipKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")

@Composable
private fun ZipEntry(c: Controller, zip: String, key: Int, active: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Weather location", style = type(48, 600, tracking = -1.4f))
        BasicText(
            c.place?.let { "Now showing ${it.name}. Enter a new ZIP code." } ?: "Enter your ZIP code.",
            style = type(22, color = C.Muted, lineHeight = 30),
        )
    }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(5) { i ->
            val cursor = active && i == zip.length
            Box(
                Modifier
                    .size(98.dp, 104.dp)
                    .background(Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp))
                    .border(2.dp, if (cursor) C.Accent else Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(zip.getOrNull(i)?.toString() ?: "", style = type(52, 400, tracking = -1f))
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        zipKeys.chunked(3).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEachIndexed { col, label ->
                    val i = r * 3 + col
                    val enabled = when (i) {
                        ZIP_KEY_DONE -> zip.length == 5
                        ZIP_KEY_DELETE -> zip.isNotEmpty()
                        else -> zip.length < 5
                    }
                    ZipKey(label, focused = active && key == i, enabled = enabled, done = i == ZIP_KEY_DONE)
                }
            }
        }
    }
    val status = c.zipStatus
    BasicText(
        status ?: "Use the remote, or type digits on a keyboard.",
        Modifier.padding(horizontal = 16.dp),
        style = type(20, color = if (status?.startsWith("Couldn't") == true) Color(0xFFFF8A80) else C.Muted),
    )
}

@Composable
private fun ZipKey(label: String, focused: Boolean, enabled: Boolean, done: Boolean) {
    val f by focusAnim(focused)
    val shape = RoundedCornerShape(18.dp)
    val bg = when {
        focused -> C.Accent
        done && enabled -> C.Accent.copy(alpha = .22f)
        else -> Color.White.copy(alpha = .06f)
    }
    val fg = when {
        focused -> C.OnAccent
        enabled -> C.Text
        else -> C.Muted.copy(alpha = .45f)
    }
    Box(
        Modifier
            .graphicsLayer { val s = 1 + .05f * f; scaleX = s; scaleY = s }
            .size(174.dp, 84.dp)
            .background(bg, shape),
        contentAlignment = Alignment.Center,
    ) {
        when (label) {
            "⌫" -> Glyph(Ic.Delete, 32.dp, fg)
            "✓" -> Glyph(Ic.Check, 34.dp, fg)
            else -> BasicText(label, style = type(36, 400, fg))
        }
    }
}

// ---------- Phone link (QR) ----------

@Composable
fun PhoneLayer(c: Controller) {
    val o = c.overlay as? Overlay.Phone
    val last = rememberLast<Overlay.Phone>(c.overlay)
    AnimatedVisibility(visible = o != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        val p = o ?: last ?: return@AnimatedVisibility
        val (headline, steps) = when (p.mode) {
            PhoneMode.Pin -> "Pin a show from your phone" to listOf(
                "Scan the code" to "Phone must be on the same Wi-Fi",
                "Paste a share link" to "Netflix, Plex, YouTube, Max — anything with a link",
                "It lands on your home screen" to "OK opens it straight in the app",
            )
            PhoneMode.Wallpaper -> "Set a wallpaper from your phone" to listOf(
                "Scan the code" to "Phone must be on the same Wi-Fi",
                "Choose a photo" to "It's cropped to fit your TV",
                "It becomes your wallpaper" to "The dock blurs it like frosted glass",
            )
        }
        Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = .9f))) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(120.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(640.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
                    BasicText(headline, style = type(72, 600, tracking = -2.5f, lineHeight = 76))
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        steps.forEachIndexed { i, (a, b) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                BasicText("${i + 1}", Modifier.width(32.dp), style = type(28, 600, C.Accent))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    BasicText(a, style = type(28))
                                    BasicText(b, style = type(20, color = C.Muted))
                                }
                            }
                        }
                    }
                }
                QrCard(c.phoneUrl, c.phoneStatus)
            }
            KeyHints(listOf("Back" to "close"), Modifier.absoluteOffset(y = 1030.dp))
        }
    }
}

@Composable
private fun QrCard(url: String?, status: String?) {
    Column(
        Modifier.width(420.dp).background(C.Text, RoundedCornerShape(32.dp)).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val qr = remember(url) { url?.let { QrCode.encodeText(it, QrCode.Ecc.MEDIUM) } }
        if (qr != null) {
            Canvas(Modifier.size(348.dp)) {
                val cell = size.width / qr.size
                for (y in 0 until qr.size) for (x in 0 until qr.size) {
                    if (qr.getModule(x, y)) drawRect(C.Panel, Offset(x * cell, y * cell), Size(cell + .5f, cell + .5f))
                }
            }
            BasicText(url!!.substringAfter("//").substringBefore("/"), style = type(22, 500, C.Panel))
        } else {
            Box(Modifier.size(348.dp), contentAlignment = Alignment.Center) {
                BasicText("Unavailable", style = type(22, 500, C.Panel))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).background(if (qr != null) Color(0xFF2F9E5B) else Color(0xFFC4302B), CircleShape))
            BasicText(status ?: "Waiting for your phone…", style = type(18, color = Color(0xFF5C574E)))
        }
    }
}

// ---------- Notice ----------

@Composable
fun NoticeLayer(c: Controller) {
    val o = c.overlay as? Overlay.Notice
    val last = rememberLast<Overlay.Notice>(c.overlay)
    AnimatedVisibility(visible = o != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        val n = o ?: last ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize().background(C.Bg.copy(alpha = .8f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(860.dp).background(C.Panel, RoundedCornerShape(28.dp)).padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BasicText(n.title, style = type(40, 600, tracking = -1f))
                n.body.split("\n").forEach { line ->
                    if (line.isBlank()) Spacer(Modifier.height(4.dp))
                    else if (line.startsWith("adb ")) BasicText(
                        line,
                        Modifier.fillMaxWidth().background(C.Bg, RoundedCornerShape(12.dp)).padding(18.dp),
                        style = type(20, color = C.Accent).copy(fontFamily = FontFamily.Monospace),
                    )
                    else BasicText(line, style = type(24, color = C.Muted, lineHeight = 34))
                }
                Spacer(Modifier.height(8.dp))
                BasicText("Press OK or Back to close", style = type(18, color = C.Muted))
            }
        }
    }
}

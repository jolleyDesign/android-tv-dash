package dev.jolley.dash

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.jolley.dash.data.AppEntry
import dev.jolley.dash.data.AppRepository
import dev.jolley.dash.data.PinnedShow
import dev.jolley.dash.data.Prefs
import dev.jolley.dash.data.Shows
import dev.jolley.dash.data.Store
import dev.jolley.dash.data.Usage
import dev.jolley.dash.data.Wall
import dev.jolley.dash.data.WeatherApi
import dev.jolley.dash.data.blurredCopy
import dev.jolley.dash.data.lanAddress
import dev.jolley.dash.data.saveWallpaper
import dev.jolley.dash.net.PinServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface Host {
    fun start(intent: Intent, from: Rect?): Boolean
    fun toast(msg: String)
}

enum class Zone { Shows, Dock }
enum class Util(val label: String) { Inputs("Inputs"), WiFi("Wi-Fi"), Settings("Settings") }
enum class PhoneMode { Pin, Wallpaper }
enum class SettingItem { Clock, Weather, Recents, Shows, Motion, Hidden, Android, Restore }
enum class Action(val label: String) {
    Move("Move"), AddToDock("Add to dock"), RemoveFromDock("Remove from dock"),
    Hide("Hide app"), Info("App info"), MoveShow("Move"), RemoveShow("Remove show"),
}

sealed interface DockEntry {
    data class Fav(val app: AppEntry) : DockEntry
    data class Recent(val app: AppEntry) : DockEntry
    data class Shortcut(val util: Util) : DockEntry
}

sealed interface MenuTarget {
    data class App(val app: AppEntry, val anchorKey: String) : MenuTarget
    data class Show(val show: PinnedShow, val anchorKey: String) : MenuTarget
}

sealed interface Overlay {
    data object None : Overlay
    data class Apps(val index: Int) : Overlay
    data class Menu(val target: MenuTarget, val actions: List<Action>, val index: Int, val under: Overlay) : Overlay
    data class Move(val zone: Zone, val index: Int) : Overlay
    /** [zip] non-null = the ZIP keypad is open, with [key] focused (3×4 grid: 1–9, ⌫, 0, ✓). */
    data class Settings(
        val row: Int,
        val col: Int = 0,
        val hiddenIndex: Int? = null,
        val zip: String? = null,
        val key: Int = 0,
    ) : Overlay
    data class Phone(val mode: PhoneMode, val returnTo: Overlay) : Overlay
    data class Notice(val title: String, val body: String, val returnTo: Overlay) : Overlay
}

const val GRID_COLUMNS = 8
const val WALL_COLS = 5
val WALL_ROWS = (Wall.entries.size + WALL_COLS - 1) / WALL_COLS
val SETTINGS_ROWS = WALL_ROWS + SettingItem.entries.size
const val ZIP_KEY_DELETE = 9
const val ZIP_KEY_DONE = 11
const val IDLE_MS = 30_000L

class Controller(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = Store(app)
    private val repo = AppRepository(app)
    var host: Host? = null

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    val icons = mutableStateMapOf<String, ImageBitmap>()
    var dock by mutableStateOf(store.dock.orEmpty()); private set
    var hidden by mutableStateOf(store.hidden); private set
    var recents by mutableStateOf<List<String>>(emptyList()); private set
    var usageAccess by mutableStateOf(false); private set
    var shows by mutableStateOf(store.shows); private set
    val showImages = mutableStateMapOf<String, ImageBitmap>()
    var prefs by mutableStateOf(store.prefs); private set
    var place by mutableStateOf(store.place); private set
    var weather by mutableStateOf(store.weather); private set
    var wallpaper by mutableStateOf<ImageBitmap?>(null); private set
    /** Pre-blurred, low-res copy of [wallpaper] for the glass dock. */
    var wallBlur by mutableStateOf<ImageBitmap?>(null); private set
    val wallThumbs = mutableStateMapOf<Wall, ImageBitmap>()

    var zone by mutableStateOf(Zone.Dock); private set
    var dockIndex by mutableIntStateOf(0); private set
    var showIndex by mutableIntStateOf(0); private set
    var overlay by mutableStateOf<Overlay>(Overlay.None); private set
    var phoneUrl by mutableStateOf<String?>(null); private set
    var phoneStatus by mutableStateOf<String?>(null); private set
    var zipStatus by mutableStateOf<String?>(null); private set

    /** False after [IDLE_MS] without input; idle-only motion (the breathing ring) settles. */
    var lively by mutableStateOf(true); private set
    /** Bumped each time Home goes to the background, so it can animate back in on return. */
    var returns by mutableIntStateOf(0); private set

    /** Layout bounds (design units) of focusable items, for menu anchoring and launch animations. */
    val bounds = HashMap<String, Rect>()

    private var server: PinServer? = null
    private var weatherJob: Job? = null
    private var wallJob: Job? = null
    private var idleJob: Job? = null
    private var centerDown = false
    private var longFired = false
    private val inputsIntent: Intent? by lazy { resolveInputs() }

    val visibleApps by derivedStateOf { apps.filter { it.pkg !in hidden } }

    val dockEntries by derivedStateOf {
        val byPkg = apps.associateBy { it.pkg }
        val favs = dock.mapNotNull { byPkg[it] }.filter { it.pkg !in hidden }
        val rec = if (prefs.showRecents && usageAccess) {
            recents.asSequence().filter { it !in dock && it !in hidden }.mapNotNull { byPkg[it] }.take(2).toList()
        } else emptyList()
        buildList {
            favs.forEach { add(DockEntry.Fav(it)) }
            rec.forEach { add(DockEntry.Recent(it)) }
            if (inputsIntent != null) add(DockEntry.Shortcut(Util.Inputs))
            add(DockEntry.Shortcut(Util.WiFi))
            add(DockEntry.Shortcut(Util.Settings))
        }
    }

    val showsVisible get() = prefs.showShows

    init {
        refreshApps()
        loadWallpaper()
        shows.forEach(::loadShowImage)
        app.getSystemService(LauncherApps::class.java).registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshApps()
            override fun onPackageAdded(packageName: String, user: UserHandle) = refreshApps()
            override fun onPackageChanged(packageName: String, user: UserHandle) = refreshApps()
            override fun onPackagesAvailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
            override fun onPackagesUnavailable(p: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
        })
    }

    // ---------- Lifecycle ----------

    fun onResume() {
        refreshRecents()
        startWeatherLoop()
        poke()
    }

    fun onStop() {
        weatherJob?.cancel()
        idleJob?.cancel()
        returns++
        if (overlay is Overlay.Phone) closePhone()
    }

    /** Home pressed. Returning from an app keeps your place; pressing it again on Home resets. */
    fun home(alreadyHome: Boolean) {
        if (overlay is Overlay.Phone) closePhone()
        overlay = Overlay.None
        zone = Zone.Dock
        if (alreadyHome) dockIndex = 0
    }

    // ---------- Data ----------

    private fun refreshApps() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { repo.load() }
            apps = list
            if (store.dock == null) {
                dock = defaultDock(list)
                store.dock = dock
            }
            clampFocus()
            withContext(Dispatchers.IO) {
                list.filter { it.pkg !in icons }.forEach { a ->
                    repo.icon(a, 192)?.let { bmp -> withContext(Dispatchers.Main) { icons[a.pkg] = bmp } }
                }
            }
        }
    }

    private fun defaultDock(list: List<AppEntry>): List<String> {
        val preferred = listOf(
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.plexapp.android", "com.wbd.stream",
            "com.hbo.hbonow", "com.spotify.tv.android", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv",
        )
        val installed = list.map { it.pkg }.toSet()
        val picks = preferred.filter { it in installed }.take(6)
        return picks + list.map { it.pkg }.filter { it !in picks && it.isUserFacing() }.take(6 - picks.size)
    }

    private fun String.isUserFacing() = !startsWith("com.android.") && !startsWith("com.google.android.tv")

    private fun refreshRecents() {
        usageAccess = Usage.hasAccess(app)
        if (!usageAccess) return
        scope.launch { recents = withContext(Dispatchers.IO) { Usage.recentPackages(app) } }
    }

    private fun startWeatherLoop() {
        weatherJob?.cancel()
        weatherJob = scope.launch {
            while (true) {
                val p = place
                val stale = weather?.let { System.currentTimeMillis() - it.fetchedAt > 30 * 60_000 } ?: true
                if (p != null && stale) {
                    runCatching { withContext(Dispatchers.IO) { WeatherApi.forecast(p) } }.onSuccess {
                        weather = it
                        store.weather = it
                    }
                }
                delay(5 * 60_000)
            }
        }
    }

    private fun setZip(zip: String, report: (String) -> Unit, onDone: () -> Unit = {}) {
        scope.launch {
            report("Looking up $zip…")
            val p = runCatching { withContext(Dispatchers.IO) { WeatherApi.geocode(zip) } }.getOrNull()
            if (p == null) {
                report("Couldn't find “$zip”")
                return@launch
            }
            updatePrefs(prefs.copy(zip = zip))
            place = p
            store.place = p
            weather = null
            store.weather = null
            runCatching { withContext(Dispatchers.IO) { WeatherApi.forecast(p) } }.onSuccess {
                weather = it
                store.weather = it
            }
            report("Weather set to ${p.name} ✓")
            onDone()
        }
    }

    private fun loadWallpaper() {
        val w = prefs.wall
        wallJob?.cancel()
        if (w == Wall.Black) {
            wallpaper = null
            wallBlur = null
            return
        }
        // The previous wallpaper stays up until the new one is decoded, so switching never flashes black.
        wallJob = scope.launch {
            val (img, blur) = withContext(Dispatchers.IO) {
                val res = w.res
                val bmp = if (res != null) BitmapFactory.decodeResource(app.resources, res)
                else BitmapFactory.decodeFile(File(app.filesDir, "wallpaper.jpg").path)
                bmp to bmp?.let(::blurredCopy)
            }
            wallpaper = img?.asImageBitmap()
            wallBlur = blur?.asImageBitmap()
        }
    }

    private fun loadWallThumbs() {
        if (wallThumbs.isNotEmpty()) return
        scope.launch {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            Wall.entries.forEach { w ->
                val res = w.res ?: return@forEach
                withContext(Dispatchers.IO) { BitmapFactory.decodeResource(app.resources, res, opts) }
                    ?.let { wallThumbs[w] = it.asImageBitmap() }
            }
        }
    }

    private fun receiveWallpaper(bytes: ByteArray) {
        scope.launch {
            phoneStatus = "Setting wallpaper…"
            val ok = withContext(Dispatchers.IO) { saveWallpaper(bytes, File(app.filesDir, "wallpaper.jpg")) }
            if (!ok) {
                phoneStatus = "That image couldn't be read"
                return@launch
            }
            updatePrefs(prefs.copy(wall = Wall.Custom))
            loadWallpaper()
            phoneStatus = "Wallpaper set ✓"
        }
    }

    private fun pinShow(url: String, title: String?) {
        scope.launch {
            val uri = Uri.parse(url)
            val host = uri.host.orEmpty().removePrefix("www.")
            val id = UUID.randomUUID().toString().take(8)
            phoneStatus = "Pinning…"
            val pkg = withContext(Dispatchers.IO) { Shows.handlerPackage(app, uri) }
            val source = pkg?.let { p -> apps.firstOrNull { it.pkg == p }?.label ?: repo.label(p) } ?: host
            var show = PinnedShow(id, url, title ?: host, source, pkg, null)
            shows = shows + show
            store.shows = shows
            phoneStatus = "Pinned “${show.title}” ✓"
            val meta = withContext(Dispatchers.IO) { Shows.fetchMeta(url) }
            val file = File(app.filesDir, "shows/$id.jpg")
            val hasImage = meta.image?.let { withContext(Dispatchers.IO) { Shows.downloadImage(it, file) } } == true
            show = show.copy(title = title ?: meta.title ?: show.title, image = if (hasImage) file.path else null)
            shows = shows.map { if (it.id == id) show else it }
            store.shows = shows
            loadShowImage(show)
            phoneStatus = "Pinned “${show.title}” ✓"
        }
    }

    private fun loadShowImage(s: PinnedShow) {
        val path = s.image ?: return
        scope.launch {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }?.let { showImages[s.id] = it }
        }
    }

    private fun updatePrefs(p: Prefs) {
        prefs = p
        store.prefs = p
    }

    private fun saveDock(list: List<String>) {
        dock = list
        store.dock = list
    }

    // ---------- Actions ----------

    private fun launch(a: AppEntry, anchorKey: String) {
        if (host?.start(repo.launchIntent(a), bounds[anchorKey]) != true) host?.toast("Couldn't open ${a.label}")
    }

    private fun openShow(s: PinnedShow) {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse(s.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val h = host ?: return
        val from = bounds["show:${shows.indexOf(s)}"]
        val ok = (s.pkg != null && h.start(Intent(base).setPackage(s.pkg), from)) || h.start(base, from)
        if (!ok) h.toast("No app can open this link")
    }

    private fun runShortcut(u: Util) {
        when (u) {
            Util.Inputs -> inputsIntent?.let { host?.start(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null) }
            Util.WiFi -> host?.start(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
            Util.Settings -> {
                loadWallThumbs()
                val i = Wall.entries.indexOf(prefs.wall).coerceAtLeast(0)
                overlay = Overlay.Settings(i / WALL_COLS, col = i % WALL_COLS)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveInputs(): Intent? {
        val pm = app.packageManager
        return listOf("com.android.tv.action.VIEW_INPUTS", "com.google.android.tv.action.VIEW_INPUTS")
            .map { Intent(it) }
            .firstOrNull { pm.queryIntentActivities(it, 0).isNotEmpty() }
    }

    private fun appInfo(a: AppEntry) {
        host?.start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            null,
        )
    }

    private fun openPhone(mode: PhoneMode, returnTo: Overlay) {
        val ip = lanAddress(app)
        val s = PinServer(
            onPin = { u, t -> scope.launch { pinShow(u, t) } },
            onZip = { z -> scope.launch { setZip(z, { phoneStatus = it }) } },
            onWallpaper = { b -> scope.launch { receiveWallpaper(b) } },
        )
        phoneStatus = null
        if (ip == null || !s.start()) {
            phoneUrl = null
            phoneStatus = if (ip == null) "Not connected to a network" else "Couldn't start (port ${PinServer.PORT} busy)"
        } else {
            server = s
            phoneUrl = "http://$ip:${PinServer.PORT}/?t=${s.token}&m=${mode.name.lowercase()}"
        }
        overlay = Overlay.Phone(mode, returnTo)
    }

    private fun closePhone() {
        server?.stop()
        server = null
        phoneUrl = null
        val o = overlay
        overlay = if (o is Overlay.Phone) o.returnTo else Overlay.None
    }

    private fun perform(m: Overlay.Menu, action: Action) {
        overlay = m.under
        when (val t = m.target) {
            is MenuTarget.App -> when (action) {
                Action.Move -> overlay = Overlay.Move(Zone.Dock, dock.indexOf(t.app.pkg).coerceAtLeast(0))
                Action.AddToDock -> saveDock(dock + t.app.pkg)
                Action.RemoveFromDock -> saveDock(dock - t.app.pkg)
                Action.Hide -> {
                    hidden = hidden + t.app.pkg
                    store.hidden = hidden
                }
                Action.Info -> appInfo(t.app)
                else -> Unit
            }
            is MenuTarget.Show -> when (action) {
                Action.MoveShow -> overlay = Overlay.Move(Zone.Shows, shows.indexOf(t.show))
                Action.RemoveShow -> {
                    shows = shows - t.show
                    store.shows = shows
                    t.show.image?.let { File(it).delete() }
                    showImages.remove(t.show.id)
                }
                else -> Unit
            }
        }
        clampFocus()
    }

    private fun clampFocus() {
        dockIndex = dockIndex.coerceIn(0, (dockEntries.size - 1).coerceAtLeast(0))
        showIndex = showIndex.coerceIn(0, shows.size)
        (overlay as? Overlay.Apps)?.let { overlay = it.copy(index = it.index.coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))) }
    }

    // ---------- Input ----------

    private fun poke() {
        lively = true
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            lively = false
        }
    }

    fun onKey(e: KeyEvent): Boolean {
        if (e.type == KeyEventType.KeyDown) poke()
        val k = e.key
        val down = e.type == KeyEventType.KeyDown
        val isCenter = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter
        if (isCenter) {
            if (down) {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    centerDown = true
                    longFired = false
                } else if (centerDown && !longFired) {
                    // TV remotes start auto-repeating ~500ms into a hold: treat that as long-press.
                    longFired = true
                    longPress()
                }
            } else {
                if (centerDown && !longFired) select()
                centerDown = false
            }
            return true
        }
        if (k == Key.Back || k == Key.Escape) {
            if (!down) back()
            return true
        }
        if (k == Key.Menu) {
            if (!down) longPress()
            return true
        }
        if (!down) return false
        val o = overlay
        if (o is Overlay.Settings && o.zip != null) {
            digitOf(k)?.let { typeZip(o, it); return true }
            if (k == Key.Backspace || k == Key.Delete) { deleteZip(o); return true }
        }
        when (k) {
            Key.DirectionUp -> move(0, -1)
            Key.DirectionDown -> move(0, 1)
            Key.DirectionLeft -> move(-1, 0)
            Key.DirectionRight -> move(1, 0)
            else -> return false
        }
        return true
    }

    fun back() {
        when (val o = overlay) {
            Overlay.None -> {
                zone = Zone.Dock
            }
            is Overlay.Apps -> overlay = Overlay.None
            is Overlay.Menu -> overlay = o.under
            is Overlay.Move -> overlay = Overlay.None
            is Overlay.Settings -> overlay = when {
                o.zip != null -> o.copy(zip = null)
                o.hiddenIndex != null -> o.copy(hiddenIndex = null)
                else -> Overlay.None
            }
            is Overlay.Phone -> closePhone()
            is Overlay.Notice -> overlay = o.returnTo
        }
    }

    private fun move(dx: Int, dy: Int) {
        when (val o = overlay) {
            Overlay.None -> moveHome(dx, dy)
            is Overlay.Apps -> {
                val n = visibleApps.size
                val i = o.index
                when {
                    dy < 0 && i < GRID_COLUMNS -> overlay = Overlay.None
                    dy < 0 -> overlay = o.copy(index = i - GRID_COLUMNS)
                    dy > 0 && i + GRID_COLUMNS < n -> overlay = o.copy(index = i + GRID_COLUMNS)
                    dy > 0 && (i / GRID_COLUMNS) < (n - 1) / GRID_COLUMNS -> overlay = o.copy(index = n - 1)
                    dx != 0 -> overlay = o.copy(index = (i + dx).coerceIn(0, n - 1))
                }
            }
            is Overlay.Menu -> if (dy != 0) overlay = o.copy(index = (o.index + dy).coerceIn(0, o.actions.size - 1))
            is Overlay.Move -> if (dx != 0) moveItem(o, dx)
            is Overlay.Settings -> when {
                o.zip != null -> {
                    val col = (o.key % 3 + dx).coerceIn(0, 2)
                    val row = (o.key / 3 + dy).coerceIn(0, 3)
                    overlay = o.copy(key = row * 3 + col)
                }
                o.hiddenIndex != null -> if (dy != 0) {
                    overlay = o.copy(hiddenIndex = (o.hiddenIndex + dy).coerceIn(0, (hidden.size - 1).coerceAtLeast(0)))
                }
                dy != 0 -> {
                    val row = (o.row + dy).coerceIn(0, SETTINGS_ROWS - 1)
                    overlay = o.copy(row = row, col = if (row < WALL_ROWS) wallCol(row, o.col) else o.col)
                }
                o.row < WALL_ROWS -> overlay = o.copy(col = wallCol(o.row, o.col + dx))
            }
            else -> Unit
        }
    }

    /** Clamp a column in the wallpaper grid, whose last row may be short. */
    private fun wallCol(row: Int, col: Int): Int =
        col.coerceIn(0, minOf(WALL_COLS, Wall.entries.size - row * WALL_COLS) - 1)

    private fun moveHome(dx: Int, dy: Int) {
        if (zone == Zone.Dock) {
            when {
                dx != 0 -> dockIndex = (dockIndex + dx).coerceIn(0, dockEntries.size - 1)
                dy < 0 && showsVisible -> {
                    zone = Zone.Shows
                    showIndex = showIndex.coerceIn(0, shows.size)
                }
                dy > 0 -> overlay = Overlay.Apps(0)
            }
        } else {
            when {
                dx != 0 -> showIndex = (showIndex + dx).coerceIn(0, shows.size)
                dy > 0 -> zone = Zone.Dock
            }
        }
    }

    private fun moveItem(o: Overlay.Move, dx: Int) {
        val target = o.index + dx
        if (o.zone == Zone.Dock) {
            if (target !in dock.indices) return
            saveDock(dock.toMutableList().apply { add(target, removeAt(o.index)) })
            dockIndex = favIndexToDockIndex(target)
        } else {
            if (target !in shows.indices) return
            shows = shows.toMutableList().apply { add(target, removeAt(o.index)) }
            store.shows = shows
            showIndex = target
        }
        overlay = o.copy(index = target)
    }

    /** Dock entries skip favorites that are hidden or uninstalled, so map by package. */
    private fun favIndexToDockIndex(favIndex: Int): Int {
        val pkg = dock.getOrNull(favIndex) ?: return dockIndex
        return dockEntries.indexOfFirst { it is DockEntry.Fav && it.app.pkg == pkg }.coerceAtLeast(0)
    }

    private fun select() {
        when (val o = overlay) {
            Overlay.None -> if (zone == Zone.Dock) {
                when (val e = dockEntries.getOrNull(dockIndex)) {
                    is DockEntry.Fav -> launch(e.app, "dock:$dockIndex")
                    is DockEntry.Recent -> launch(e.app, "dock:$dockIndex")
                    is DockEntry.Shortcut -> runShortcut(e.util)
                    null -> Unit
                }
            } else {
                shows.getOrNull(showIndex)?.let(::openShow) ?: openPhone(PhoneMode.Pin, Overlay.None)
            }
            is Overlay.Apps -> visibleApps.getOrNull(o.index)?.let {
                launch(it, "grid:${o.index}")
                overlay = Overlay.None
            }
            is Overlay.Menu -> perform(o, o.actions[o.index])
            is Overlay.Move -> overlay = Overlay.None
            is Overlay.Settings -> selectSetting(o)
            is Overlay.Phone -> closePhone()
            is Overlay.Notice -> overlay = o.returnTo
        }
    }

    private fun selectSetting(o: Overlay.Settings) {
        if (o.zip != null) {
            when (o.key) {
                ZIP_KEY_DELETE -> deleteZip(o)
                ZIP_KEY_DONE -> submitZip(o)
                10 -> typeZip(o, 0)
                else -> typeZip(o, o.key + 1)
            }
            return
        }
        if (o.hiddenIndex != null) {
            val pkg = hiddenApps().getOrNull(o.hiddenIndex)?.pkg ?: return
            hidden = hidden - pkg
            store.hidden = hidden
            overlay = if (hidden.isEmpty()) o.copy(hiddenIndex = null) else o.copy(hiddenIndex = o.hiddenIndex.coerceAtMost(hidden.size - 1))
            return
        }
        if (o.row < WALL_ROWS) {
            val wall = Wall.entries[o.row * WALL_COLS + o.col]
            if (wall == Wall.Custom) openPhone(PhoneMode.Wallpaper, o) else {
                updatePrefs(prefs.copy(wall = wall))
                loadWallpaper()
            }
            return
        }
        when (SettingItem.entries[o.row - WALL_ROWS]) {
            SettingItem.Clock -> updatePrefs(prefs.copy(use24h = !prefs.use24h))
            SettingItem.Weather -> {
                zipStatus = null
                overlay = o.copy(zip = "", key = 0)
            }
            SettingItem.Recents -> if (usageAccess) updatePrefs(prefs.copy(showRecents = !prefs.showRecents)) else {
                overlay = Overlay.Notice(
                    "Allow usage access",
                    "Recent apps need one-time permission. From your computer, run:\n\nadb shell appops set ${app.packageName} GET_USAGE_STATS allow",
                    o,
                )
            }
            SettingItem.Shows -> {
                updatePrefs(prefs.copy(showShows = !prefs.showShows))
                if (!prefs.showShows) zone = Zone.Dock
            }
            SettingItem.Motion -> updatePrefs(prefs.copy(motion = !prefs.motion))
            SettingItem.Hidden -> if (hidden.isNotEmpty()) overlay = o.copy(hiddenIndex = 0)
            SettingItem.Android -> host?.start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)
            SettingItem.Restore -> overlay = Overlay.Notice(
                "Restore Google TV home",
                "From your computer, run:\n\nadb shell pm enable com.google.android.apps.tv.launcherx\n\nThen press Home and pick Google TV.",
                o,
            )
        }
    }

    fun hiddenApps(): List<AppEntry> = apps.filter { it.pkg in hidden }

    // ---------- ZIP keypad ----------

    private fun digitOf(k: Key): Int? = when (k) {
        Key.Zero, Key.NumPad0 -> 0
        Key.One, Key.NumPad1 -> 1
        Key.Two, Key.NumPad2 -> 2
        Key.Three, Key.NumPad3 -> 3
        Key.Four, Key.NumPad4 -> 4
        Key.Five, Key.NumPad5 -> 5
        Key.Six, Key.NumPad6 -> 6
        Key.Seven, Key.NumPad7 -> 7
        Key.Eight, Key.NumPad8 -> 8
        Key.Nine, Key.NumPad9 -> 9
        else -> null
    }

    private fun typeZip(o: Overlay.Settings, d: Int) {
        val z = o.zip ?: return
        if (z.length >= 5) return
        val next = z + d
        zipStatus = null
        // A full ZIP jumps focus to ✓ so confirming is one press.
        overlay = o.copy(zip = next, key = if (next.length == 5) ZIP_KEY_DONE else o.key)
    }

    private fun deleteZip(o: Overlay.Settings) {
        val z = o.zip ?: return
        zipStatus = null
        overlay = o.copy(zip = z.dropLast(1))
    }

    private fun submitZip(o: Overlay.Settings) {
        val z = o.zip ?: return
        if (z.length != 5) return
        setZip(z, { zipStatus = it }) {
            val cur = overlay
            if (cur is Overlay.Settings && cur.zip != null) overlay = cur.copy(zip = null)
        }
    }

    private fun longPress() {
        val o = overlay
        when {
            o == Overlay.None && zone == Zone.Dock -> when (val e = dockEntries.getOrNull(dockIndex)) {
                is DockEntry.Fav -> openMenu(MenuTarget.App(e.app, "dock:$dockIndex"), o)
                is DockEntry.Recent -> openMenu(MenuTarget.App(e.app, "dock:$dockIndex"), o)
                else -> Unit
            }
            o == Overlay.None && zone == Zone.Shows ->
                shows.getOrNull(showIndex)?.let { openMenu(MenuTarget.Show(it, "show:$showIndex"), o) }
            o is Overlay.Apps -> visibleApps.getOrNull(o.index)?.let { openMenu(MenuTarget.App(it, "grid:${o.index}"), o) }
        }
    }

    private fun openMenu(t: MenuTarget, under: Overlay) {
        val actions = when (t) {
            is MenuTarget.App -> {
                val inDock = t.app.pkg in dock
                val fromDock = under == Overlay.None && inDock
                listOfNotNull(
                    Action.Move.takeIf { fromDock },
                    if (inDock) Action.RemoveFromDock else Action.AddToDock,
                    Action.Hide,
                    Action.Info,
                )
            }
            is MenuTarget.Show -> listOfNotNull(Action.MoveShow.takeIf { shows.size > 1 }, Action.RemoveShow)
        }
        overlay = Overlay.Menu(t, actions, 0, under)
    }
}

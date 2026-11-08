package dev.jolley.dash.data

import android.content.Context
import dev.jolley.dash.R
import org.json.JSONArray
import org.json.JSONObject

/** Bundled photos (res/drawable-nodpi), then plain black, then the photo sent from the phone. */
enum class Wall(val res: Int? = null) {
    City(R.drawable.wall_city),
    Aurora(R.drawable.wall_aurora),
    Alpine(R.drawable.wall_alpine),
    Coast(R.drawable.wall_coast),
    Forest(R.drawable.wall_forest),
    Dunes(R.drawable.wall_dunes),
    Ripple(R.drawable.wall_ripple),
    Pillars(R.drawable.wall_pillars),
    Black,
    Custom;

    companion object {
        val Default get() = entries.first()
    }
}

enum class ClockFace(val label: String) { Thin("Thin"), Bold("Bold"), Analog("Analog"), Stacked("Stacked"), Words("Words") }

enum class WidgetKind(val label: String) {
    Weather("Weather"), NowPlaying("Now playing"), Sun("Sun & air"), Clocks("World clocks"), Connections("Connections"),
}

data class City(val name: String, val zone: String)

val Cities = listOf(
    City("Honolulu", "Pacific/Honolulu"), City("Los Angeles", "America/Los_Angeles"), City("Denver", "America/Denver"),
    City("Chicago", "America/Chicago"), City("New York", "America/New_York"), City("Mexico City", "America/Mexico_City"),
    City("São Paulo", "America/Sao_Paulo"), City("London", "Europe/London"), City("Paris", "Europe/Paris"),
    City("Berlin", "Europe/Berlin"), City("Athens", "Europe/Athens"), City("Dubai", "Asia/Dubai"),
    City("Mumbai", "Asia/Kolkata"), City("Singapore", "Asia/Singapore"), City("Hong Kong", "Asia/Hong_Kong"),
    City("Tokyo", "Asia/Tokyo"), City("Seoul", "Asia/Seoul"), City("Sydney", "Australia/Sydney"),
    City("Auckland", "Pacific/Auckland"),
)

const val MAX_CITIES = 3

data class Prefs(
    val wall: Wall = Wall.Default,
    val use24h: Boolean = false,
    val showRecents: Boolean = true,
    val showShows: Boolean = true,
    val zip: String? = null,
    val motion: Boolean = true,
    val face: ClockFace = ClockFace.Thin,
    val clockWeather: Boolean = true,
    /** Clock, widgets and shows centered (true) or aligned to the left margin. */
    val centered: Boolean = true,
    val btInDock: Boolean = false,
    /** Enabled widgets, in display order. */
    val widgets: List<WidgetKind> = WidgetKind.entries,
    /** World-clock city names (from [Cities]). */
    val cities: List<String> = listOf("London", "Tokyo"),
)

data class Place(val name: String, val lat: Double, val lon: Double)

data class PinnedShow(
    val id: String,
    val url: String,
    val title: String,
    val source: String,
    val pkg: String?,
    val image: String?,
)

data class Hour(val at: Long, val code: Int, val temp: Int, val pop: Int, val isDay: Boolean)

data class Day(val date: String, val code: Int, val hi: Int, val lo: Int)

/** Times are epoch seconds. Fields after [fetchedAt] were added in v0.4; older caches lack them. */
data class Weather(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val fetchedAt: Long,
    val feels: Int = temp,
    val humidity: Int = -1,
    val wind: Int = -1,
    val uv: Int = -1,
    val sunrise: Long = 0,
    val sunset: Long = 0,
    val hours: List<Hour> = emptyList(),
    val days: List<Day> = emptyList(),
    val aqi: Int? = null,
)

/** Small enough that SharedPreferences + org.json beats pulling in DataStore. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("dash", Context.MODE_PRIVATE)

    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) = sp.edit().putString("dock", v?.joinToString(",")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    var prefs: Prefs
        get() = Prefs(
            wall = runCatching { Wall.valueOf(sp.getString("wall", null)!!) }.getOrDefault(Wall.Default),
            use24h = sp.getBoolean("use24h", false),
            showRecents = sp.getBoolean("showRecents", true),
            showShows = sp.getBoolean("showShows", true),
            zip = sp.getString("zip", null),
            motion = sp.getBoolean("motion", true),
            face = runCatching { ClockFace.valueOf(sp.getString("face", null)!!) }.getOrDefault(ClockFace.Thin),
            btInDock = sp.getBoolean("btInDock", false),
            clockWeather = sp.getBoolean("clockWeather", true),
            centered = sp.getBoolean("centered", true),
            widgets = sp.getString("widgets", null)?.split(',')?.mapNotNull { n -> WidgetKind.entries.firstOrNull { it.name == n } }
                ?: WidgetKind.entries,
            cities = sp.getString("cities", null)?.split('|')?.filter { n -> Cities.any { it.name == n } } ?: listOf("London", "Tokyo"),
        )
        set(p) = sp.edit()
            .putString("wall", p.wall.name)
            .putBoolean("use24h", p.use24h)
            .putBoolean("showRecents", p.showRecents)
            .putBoolean("showShows", p.showShows)
            .putString("zip", p.zip)
            .putBoolean("motion", p.motion)
            .putString("face", p.face.name)
            .putBoolean("btInDock", p.btInDock)
            .putBoolean("clockWeather", p.clockWeather)
            .putBoolean("centered", p.centered)
            .putString("widgets", p.widgets.joinToString(",") { it.name })
            .putString("cities", p.cities.joinToString("|"))
            .apply()

    var place: Place?
        get() = sp.getString("place", null)?.let {
            runCatching { JSONObject(it).run { Place(getString("name"), getDouble("lat"), getDouble("lon")) } }.getOrNull()
        }
        set(p) = sp.edit().putString(
            "place",
            p?.let { JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).toString() },
        ).apply()

    var weather: Weather?
        get() = sp.getString("weather", null)?.let {
            runCatching {
                JSONObject(it).run {
                    val t = getInt("t")
                    val hs = optJSONArray("hours") ?: JSONArray()
                    val ds = optJSONArray("days") ?: JSONArray()
                    Weather(
                        t, getInt("c"), getBoolean("d"), getInt("hi"), getInt("lo"), getLong("at"),
                        feels = optInt("feels", t), humidity = optInt("hum", -1), wind = optInt("wind", -1),
                        uv = optInt("uv", -1), sunrise = optLong("rise"), sunset = optLong("set"),
                        hours = (0 until hs.length()).map { i ->
                            hs.getJSONObject(i).run { Hour(getLong("at"), getInt("c"), getInt("t"), getInt("p"), getBoolean("d")) }
                        },
                        days = (0 until ds.length()).map { i ->
                            ds.getJSONObject(i).run { Day(getString("date"), getInt("c"), getInt("hi"), getInt("lo")) }
                        },
                        aqi = if (has("aqi")) getInt("aqi") else null,
                    )
                }
            }.getOrNull()
        }
        set(w) = sp.edit().putString(
            "weather",
            w?.let {
                JSONObject().put("t", it.temp).put("c", it.code).put("d", it.isDay)
                    .put("hi", it.hi).put("lo", it.lo).put("at", it.fetchedAt)
                    .put("feels", it.feels).put("hum", it.humidity).put("wind", it.wind).put("uv", it.uv)
                    .put("rise", it.sunrise).put("set", it.sunset)
                    .put("hours", JSONArray().apply {
                        it.hours.forEach { h ->
                            put(JSONObject().put("at", h.at).put("c", h.code).put("t", h.temp).put("p", h.pop).put("d", h.isDay))
                        }
                    })
                    .put("days", JSONArray().apply {
                        it.days.forEach { d -> put(JSONObject().put("date", d.date).put("c", d.code).put("hi", d.hi).put("lo", d.lo)) }
                    })
                    .apply { it.aqi?.let { a -> put("aqi", a) } }
                    .toString()
            },
        ).apply()

    var shows: List<PinnedShow>
        get() = runCatching {
            val a = JSONArray(sp.getString("shows", "[]"))
            (0 until a.length()).map { i ->
                a.getJSONObject(i).run {
                    PinnedShow(
                        id = getString("id"),
                        url = getString("url"),
                        title = getString("title"),
                        source = optString("source"),
                        pkg = optString("pkg").ifBlank { null },
                        image = optString("image").ifBlank { null },
                    )
                }
            }
        }.getOrDefault(emptyList())
        set(list) {
            val a = JSONArray()
            list.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)
                        .put("source", it.source).put("pkg", it.pkg ?: "").put("image", it.image ?: ""),
                )
            }
            sp.edit().putString("shows", a.toString()).apply()
        }
}

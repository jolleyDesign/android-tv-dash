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

data class Prefs(
    val wall: Wall = Wall.Default,
    val use24h: Boolean = false,
    val showRecents: Boolean = true,
    val showShows: Boolean = true,
    val zip: String? = null,
    val motion: Boolean = true,
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

data class Weather(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val fetchedAt: Long,
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
        )
        set(p) = sp.edit()
            .putString("wall", p.wall.name)
            .putBoolean("use24h", p.use24h)
            .putBoolean("showRecents", p.showRecents)
            .putBoolean("showShows", p.showShows)
            .putString("zip", p.zip)
            .putBoolean("motion", p.motion)
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
                    Weather(getInt("t"), getInt("c"), getBoolean("d"), getInt("hi"), getInt("lo"), getLong("at"))
                }
            }.getOrNull()
        }
        set(w) = sp.edit().putString(
            "weather",
            w?.let {
                JSONObject().put("t", it.temp).put("c", it.code).put("d", it.isDay)
                    .put("hi", it.hi).put("lo", it.lo).put("at", it.fetchedAt).toString()
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

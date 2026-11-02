package dev.jolley.dash.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Process
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.roundToInt

private const val DESKTOP_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

private fun <T> http(url: String, ua: String = "Dash/0.1 (Android TV launcher)", read: (InputStream) -> T): T {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 10000
    conn.setRequestProperty("User-Agent", ua)
    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
    try {
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        return conn.inputStream.use(read)
    } finally {
        conn.disconnect()
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

object WeatherApi {
    fun geocode(query: String): Place? {
        val us = if (query.matches(Regex("\\d{5}"))) "&countryCode=US" else ""
        val body = http("https://geocoding-api.open-meteo.com/v1/search?name=${enc(query)}&count=1&language=en&format=json$us") {
            it.bufferedReader().readText()
        }
        val r = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
        val name = listOfNotNull(r.optString("name").ifBlank { null }, r.optString("admin1").ifBlank { null })
            .distinct().joinToString(", ")
        return Place(name, r.getDouble("latitude"), r.getDouble("longitude"))
    }

    fun forecast(p: Place): Weather {
        val body = http(
            "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=fahrenheit&timezone=auto&forecast_days=1",
        ) { it.bufferedReader().readText() }
        val j = JSONObject(body)
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        return Weather(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    /** WMO weather interpretation codes, as used by Open-Meteo. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorms"
        else -> "—"
    }
}

object Usage {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun recentPackages(ctx: Context): List<String> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7L * 24 * 3600 * 1000, now)
            .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
    }
}

object Shows {
    /** The app that should open a show link, skipping browsers so the TV app wins. */
    @Suppress("DEPRECATION")
    fun handlerPackage(ctx: Context, uri: Uri): String? {
        val pm = ctx.packageManager
        val browsers = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")), 0)
            .map { it.activityInfo.packageName }.toSet()
        return pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, uri), 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it !in browsers && it != ctx.packageName }
    }

    data class Meta(val title: String?, val image: String?)

    fun fetchMeta(url: String): Meta = runCatching {
        // Metadata lives in <head>, but some sites (YouTube) inline ~800KB of script before it.
        val html = http(url, DESKTOP_UA) { s ->
            val out = StringBuilder()
            val reader = s.bufferedReader()
            val chunk = CharArray(32_768)
            while (out.length < 2_000_000) {
                val r = reader.read(chunk)
                if (r < 0) break
                out.appendRange(chunk, 0, r)
                if (out.indexOf("</head>", (out.length - r - 7).coerceAtLeast(0)) >= 0) break
            }
            out.toString()
        }
        fun og(prop: String): String? {
            val a = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val b = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE)
            return (a.find(html) ?: b.find(html))?.groupValues?.get(1)?.let(::unescape)
        }
        val title = og("og:title")
            ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::unescape)
        Meta(title?.let(::cleanTitle), og("og:image"))
    }.getOrDefault(Meta(null, null))

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()

    // "Watch Severance | Apple TV+" → "Severance"
    private fun cleanTitle(s: String) = s.substringBefore(" | ").substringBefore(" - Netflix")
        .removePrefix("Watch ").trim()

    fun downloadImage(url: String, dest: File): Boolean = runCatching {
        val bytes = http(url, DESKTOP_UA) { it.readBytes() }
        val bmp = decodeScaled(bytes, 640) ?: return false
        dest.parentFile?.mkdirs()
        dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    }.getOrDefault(false)
}

/** Decodes with subsampling (no full-size bitmaps in memory) and applies EXIF rotation. */
fun decodeScaled(bytes: ByteArray, targetWidth: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth <= 0) return null
    var sample = 1
    while (o.outWidth / (sample * 2) >= targetWidth) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val rotation = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    val rotated = if (rotation == 0f) bmp else
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    val scale = targetWidth.toFloat() / max(1, rotated.width)
    return if (scale >= 1f) rotated else
        Bitmap.createScaledBitmap(rotated, targetWidth, (rotated.height * scale).roundToInt(), true)
}

/** Center-crops to 16:9 at 1920×1080 so the wallpaper never needs scaling at draw time. */
fun saveWallpaper(bytes: ByteArray, dest: File): Boolean {
    val src = decodeScaled(bytes, 1920) ?: return false
    val targetRatio = 16f / 9f
    val (w, h) = if (src.width.toFloat() / src.height > targetRatio)
        (src.height * targetRatio).roundToInt() to src.height else src.width to (src.width / targetRatio).roundToInt()
    val cropped = Bitmap.createBitmap(src, (src.width - w) / 2, (src.height - h) / 2, w, h)
    val out = Bitmap.createScaledBitmap(cropped, 1920, 1080, true)
    dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return true
}

fun lanAddress(ctx: Context): String? {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return lp.linkAddresses.map { it.address }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
}

/**
 * A heavily blurred copy for the glass dock: downscale to ~1/8, then three box-blur passes
 * (≈ Gaussian). Drawn upscaled with bilinear filtering it matches a 40dp RenderEffect blur, but
 * costs nothing per frame — so the wallpaper can drift without re-blurring every frame.
 */
fun blurredCopy(src: Bitmap, width: Int = 240, radius: Int = 3): Bitmap {
    val h = max(1, (src.height * width.toFloat() / src.width).roundToInt())
    val small = Bitmap.createScaledBitmap(src, width, h, true).copy(Bitmap.Config.ARGB_8888, true)
    val px = IntArray(width * h)
    small.getPixels(px, 0, width, 0, 0, width, h)
    val tmp = IntArray(px.size)
    repeat(3) {
        boxBlur(px, tmp, width, h, radius, horizontal = true)
        boxBlur(tmp, px, width, h, radius, horizontal = false)
    }
    small.setPixels(px, 0, width, 0, 0, width, h)
    return small
}

private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val div = 2 * r + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, len - 1)
            return if (horizontal) src[line * w + c] else src[c * w + line]
        }
        var rs = 0; var gs = 0; var bs = 0
        for (i in -r..r) at(i).let { rs += it shr 16 and 0xFF; gs += it shr 8 and 0xFF; bs += it and 0xFF }
        for (i in 0 until len) {
            val o = if (horizontal) line * w + i else i * w + line
            dst[o] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
            val add = at(i + r + 1); val sub = at(i - r)
            rs += (add shr 16 and 0xFF) - (sub shr 16 and 0xFF)
            gs += (add shr 8 and 0xFF) - (sub shr 8 and 0xFF)
            bs += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

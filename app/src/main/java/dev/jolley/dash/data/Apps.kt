package dev.jolley.dash.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class AppEntry(
    val pkg: String,
    val cls: String,
    val label: String,
    val leanback: Boolean,
)

class AppRepository(private val ctx: Context) {
    private val pm = ctx.packageManager

    @Suppress("DEPRECATION")
    fun load(): List<AppEntry> {
        val byPkg = LinkedHashMap<String, AppEntry>()
        fun scan(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo
                if (ai.packageName == ctx.packageName || ai.packageName in byPkg) continue
                byPkg[ai.packageName] = AppEntry(ai.packageName, ai.name, ri.loadLabel(pm).toString().trim(), leanback)
            }
        }
        // TV-native entry points win; phone-only apps (sideloads) still show up.
        scan(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        scan(Intent.CATEGORY_LAUNCHER, false)
        return byPkg.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setClassName(app.pkg, app.cls)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun label(pkg: String): String? = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    /**
     * Renders the app's icon into a circle. Many TV apps ship only a 16:9 banner, so when there's
     * no real icon we center-crop the banner instead of showing Android's generic robot.
     */
    fun icon(app: AppEntry, size: Int): ImageBitmap? = runCatching {
        val ai = pm.getActivityInfo(ComponentName(app.pkg, app.cls), 0)
        val hasIcon = ai.icon != 0 || ai.applicationInfo.icon != 0
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        val icon = if (hasIcon) ai.loadIcon(pm) else null
        when {
            icon is AdaptiveIconDrawable -> {
                // Adaptive layers are 108dp with a 72dp visible area: overdraw by 25% per side.
                val inset = size / 4
                listOfNotNull(icon.background, icon.foreground).forEach {
                    it.setBounds(-inset, -inset, size + inset, size + inset)
                    it.draw(c)
                }
            }
            icon != null -> drawLegacy(c, icon, size)
            else -> {
                val banner: Drawable = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) ?: return null
                val iw = banner.intrinsicWidth.coerceAtLeast(1)
                val ih = banner.intrinsicHeight.coerceAtLeast(1)
                val w = size * iw / ih
                val left = (size - w) / 2
                banner.setBounds(left, 0, left + w, size)
                banner.draw(c)
            }
        }
        bmp.asImageBitmap()
    }.getOrNull()

    /**
     * Legacy (non-adaptive) icons come in arbitrary shapes and paddings. Trim the transparent margin;
     * if what's left is a solid shape (square, squircle, circle) blow it up so the circle crops it —
     * the inscribed circle always fits inside any of those. Otherwise it's a free-standing logo, so
     * center it on a dark disc.
     */
    private fun drawLegacy(c: Canvas, icon: Drawable, size: Int) {
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(src))
        val px = IntArray(size * size).also { src.getPixels(it, 0, size, 0, 0, size, size) }
        fun alpha(x: Int, y: Int) = px[y.coerceIn(0, size - 1) * size + x.coerceIn(0, size - 1)] ushr 24
        var l = size; var t = size; var r = -1; var b = -1
        for (y in 0 until size) for (x in 0 until size) {
            if (alpha(x, y) > 24) {
                if (x < l) l = x; if (x > r) r = x
                if (y < t) t = y; if (y > b) b = y
            }
        }
        if (r < l || b < t) return
        val w = r - l + 1
        val h = b - t + 1
        val ix = w / 10
        val iy = h / 10
        val cx = l + w / 2
        val cy = t + h / 2
        val solid = listOf(cx to cy, l + ix to cy, r - ix to cy, cx to t + iy, cx to b - iy).all { (x, y) -> alpha(x, y) > 230 }
        val dst = if (solid) {
            // Cover, plus a hair extra so anti-aliased edges never show inside the circle.
            val s = size / minOf(w, h).toFloat() * 1.04f
            RectF(size / 2f - w * s / 2, size / 2f - h * s / 2, size / 2f + w * s / 2, size / 2f + h * s / 2)
        } else {
            c.drawColor(0xFF2A2926.toInt())
            val s = size * 0.6f / maxOf(w, h)
            RectF(size / 2f - w * s / 2, size / 2f - h * s / 2, size / 2f + w * s / 2, size / 2f + h * s / 2)
        }
        c.drawBitmap(src, Rect(l, t, r + 1, b + 1), dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        src.recycle()
    }
}

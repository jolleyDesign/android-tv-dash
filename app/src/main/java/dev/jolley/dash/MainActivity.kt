package dev.jolley.dash

import android.app.ActivityOptions
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import dev.jolley.dash.ui.DashRoot

class DashApp : Application() {
    val controller by lazy { Controller(this) }
}

class MainActivity : ComponentActivity(), Host {
    private val c get() = (application as DashApp).controller
    private val focus = FocusRequester()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        c.host = this
        setContent { DashRoot(c, focus) }
        // A launcher must never finish on Back; the controller decides what Back means.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = c.back()
        })
    }

    private var visible = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) c.home(alreadyHome = visible)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        c.host = this
        c.onResume()
        runCatching { focus.requestFocus() }
    }

    override fun onStop() {
        super.onStop()
        visible = false
        c.onStop()
    }

    override fun onDestroy() {
        if (c.host === this) c.host = null
        super.onDestroy()
    }

    override fun start(intent: Intent, from: Rect?): Boolean {
        val root = window.decorView
        val s = root.width / 1920f
        val opts = from?.let {
            ActivityOptions.makeScaleUpAnimation(
                root, (it.left * s).toInt(), (it.top * s).toInt(), (it.width * s).toInt(), (it.height * s).toInt(),
            ).toBundle()
        }
        return try {
            startActivity(intent, opts)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    override fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

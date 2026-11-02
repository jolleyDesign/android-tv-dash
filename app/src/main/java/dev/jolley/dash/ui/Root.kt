package dev.jolley.dash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.jolley.dash.Controller

/**
 * The whole UI is laid out on a fixed 1920×1080 canvas (1dp = 1 design pixel) and scaled to the
 * screen, so the Paper/Pencil measurements carry over 1:1 regardless of the TV's density.
 *
 * Focus is a single state machine in [Controller] rather than Compose's focus tree: one focusable
 * root receives every key, which keeps D-pad movement deterministic and cheap.
 */
@Composable
fun DashRoot(c: Controller, focus: FocusRequester = remember { FocusRequester() }) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        val scale = constraints.maxWidth / 1920f
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), LocalMotion provides rememberMotion(c)) {
            Box(
                Modifier
                    .size(1920.dp, 1080.dp)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent(c::onKey),
            ) {
                Wallpaper(c)
                Home(c)
                AppsSheet(c)
                SettingsPanel(c)
                MenuLayer(c)
                PhoneLayer(c)
                NoticeLayer(c)
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

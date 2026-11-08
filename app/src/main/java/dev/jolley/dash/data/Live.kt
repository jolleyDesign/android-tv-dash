package dev.jolley.dash.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

/**
 * Never does anything itself: being an enabled notification listener is what lets Dash read the
 * active media sessions. Granted once over adb (`cmd notification allow_listener`).
 */
class MediaListener : NotificationListenerService()

// ---------- Now playing ----------

data class NowPlaying(
    val pkg: String,
    val title: String,
    val subtitle: String?,
    val art: ImageBitmap?,
    val playing: Boolean,
)

class Media(private val ctx: Context, private val onChange: (NowPlaying?) -> Unit) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(ctx, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var current: MediaController? = null
    private var artFor: MediaMetadata? = null
    private var art: ImageBitmap? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onSessionDestroyed() = pick()
    }
    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    fun hasAccess(): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)

    /** Idempotent; returns false while access hasn't been granted. */
    fun start(): Boolean {
        if (!hasAccess()) return false
        if (!started) {
            started = runCatching { msm.addOnActiveSessionsChangedListener(sessions, component, main) }.isSuccess
        }
        if (started) pick()
        return started
    }

    fun toggle() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    val pkg: String? get() = current?.packageName

    private fun pick(list: List<MediaController>? = null) {
        val all = list ?: runCatching { msm.getActiveSessions(component) }.getOrDefault(emptyList())
        val next = all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: all.firstOrNull { it.metadata != null }
        if (next?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            next?.registerCallback(callback, main)
            current = next
        }
        onChange(next?.let(::describe))
    }

    private fun describe(c: MediaController): NowPlaying? {
        val m = c.metadata ?: return null
        val title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.ifBlank { null }
            ?: m.getString(MediaMetadata.METADATA_KEY_TITLE)?.ifBlank { null }
            ?: return null
        val subtitle = listOf(
            MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ALBUM,
        ).firstNotNullOfOrNull { k -> m.getString(k)?.ifBlank { null } }
        if (m !== artFor) {
            artFor = m
            art = listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                .firstNotNullOfOrNull { k -> m.getBitmap(k) }
                ?.let(::shrink)?.asImageBitmap()
        }
        return NowPlaying(c.packageName, title, subtitle, art, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    private fun shrink(b: Bitmap): Bitmap {
        val max = 320
        if (b.width <= max && b.height <= max) return b
        val s = max.toFloat() / maxOf(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true)
    }
}

// ---------- Connections ----------

enum class NetKind { WiFi, Ethernet, Other, Offline }

data class NetInfo(val kind: NetKind, val band: String?, val signal: Int, val ip: String?, val online: Boolean, val mbps: Int?)

enum class BtKind { Remote, Gamepad, Headphones, Speaker, Keyboard, Other }

data class BtDevice(val name: String, val kind: BtKind, val battery: Int?)

sealed interface BtState {
    data object Unavailable : BtState
    data object NoPermission : BtState
    data object Off : BtState
    data class On(val connected: List<BtDevice>) : BtState
}

class Connections(private val ctx: Context, private val onNet: (NetInfo) -> Unit, private val onBt: (BtState) -> Unit) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = postNet()
        override fun onLost(network: Network) = postNet()
        override fun onLinkPropertiesChanged(network: Network, lp: android.net.LinkProperties) = postNet()
    }

    private val btReceiver = object : BroadcastReceiver() {
        // Connection state settles a moment after the ACL event.
        override fun onReceive(c: Context, i: Intent) {
            main.removeCallbacks(btRefresh)
            main.postDelayed(btRefresh, 600)
        }
    }
    private val btRefresh = Runnable { onBt(bluetooth()) }

    fun start() {
        if (!started) {
            started = true
            runCatching { cm.registerDefaultNetworkCallback(netCallback, main) }
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            }
            ctx.registerReceiver(btReceiver, f, Context.RECEIVER_EXPORTED)
        }
        refresh()
    }

    fun refresh() {
        onNet(network())
        onBt(bluetooth())
    }

    private fun postNet() { main.post { onNet(network()) } }

    fun hasBtPermission() =
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun network(): NetInfo {
        val n = cm.activeNetwork ?: return NetInfo(NetKind.Offline, null, -1, null, false, null)
        val caps = cm.getNetworkCapabilities(n) ?: return NetInfo(NetKind.Offline, null, -1, null, false, null)
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val ip = lanAddress(ctx)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val w = caps.transportInfo as? WifiInfo
                val band = w?.frequency?.let { f ->
                    when {
                        f >= 5925 -> "6 GHz"
                        f >= 4900 -> "5 GHz"
                        f > 0 -> "2.4 GHz"
                        else -> null
                    }
                }
                val rssi = w?.rssi ?: -127
                val signal = when {
                    rssi <= -100 -> 0
                    rssi >= -55 -> 4
                    rssi >= -66 -> 3
                    rssi >= -77 -> 2
                    else -> 1
                }
                NetInfo(NetKind.WiFi, band, signal, ip, online, w?.linkSpeed?.takeIf { it > 0 })
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetInfo(NetKind.Ethernet, null, -1, ip, online, null)
            else -> NetInfo(NetKind.Other, null, -1, ip, online, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetooth(): BtState {
        val a = bt ?: return BtState.Unavailable
        if (!hasBtPermission()) return BtState.NoPermission
        if (!a.isEnabled) return BtState.Off
        val devices = runCatching { a.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .filter { isConnected(it) }
            .map { d ->
                val name = runCatching { d.alias ?: d.name }.getOrNull() ?: "Device"
                BtDevice(name, kindOf(d, name), battery(d))
            }
            .sortedBy { it.kind.ordinal }
        return BtState.On(devices)
    }

    // Neither is in the public SDK, but both are on the non-SDK allow-list ("unsupported") apps may call.
    private fun isConnected(d: BluetoothDevice): Boolean =
        runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)

    private fun battery(d: BluetoothDevice): Int? =
        runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int }.getOrNull()?.takeIf { it in 0..100 }

    @SuppressLint("MissingPermission")
    private fun kindOf(d: BluetoothDevice, name: String): BtKind {
        val n = name.lowercase()
        val cls = runCatching { d.bluetoothClass }.getOrNull()
        return when {
            "remote" in n -> BtKind.Remote
            listOf("controller", "gamepad", "xbox", "dualsense", "dualshock", "joy-con", "8bitdo").any { it in n } -> BtKind.Gamepad
            cls == null -> BtKind.Other
            cls.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> when (cls.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BtKind.Speaker
                else -> BtKind.Headphones
            }
            cls.majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL -> when {
                cls.deviceClass and 0x40 != 0 -> BtKind.Keyboard
                cls.deviceClass and 0x3C == 0x08 || cls.deviceClass and 0x3C == 0x04 -> BtKind.Gamepad
                else -> BtKind.Remote
            }
            else -> BtKind.Other
        }
    }
}

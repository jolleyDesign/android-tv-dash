package dev.jolley.dash.net

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Tiny LAN-only HTTP server for the phone companion page. It only runs while the QR screen is open,
 * and every request must carry the random token embedded in the QR code, so other devices on the
 * network can't push links or wallpapers without seeing the TV.
 */
class PinServer(
    private val onPin: (url: String, title: String?) -> Unit,
    private val onZip: (zip: String) -> Unit,
    private val onWallpaper: (bytes: ByteArray) -> Unit,
) {
    val token: String = ByteArray(9).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    @Volatile private var socket: ServerSocket? = null

    fun start(port: Int = PORT): Boolean = runCatching {
        val s = ServerSocket(port)
        socket = s
        thread(name = "dash-pin-server", isDaemon = true) {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (_: SocketException) { break }
                runCatching { client.use(::handle) }
            }
        }
        true
    }.getOrDefault(false)

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val head = readHead(input) ?: return
        val lines = head.split("\r\n")
        val (method, target) = lines.first().split(" ").let { it.getOrNull(0) to it.getOrNull(1) }
        if (method == null || target == null) return
        val headers = lines.drop(1).mapNotNull { l ->
            l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() }
        }.toMap()
        val path = target.substringBefore('?')
        val query = parseForm(target.substringAfter('?', ""))

        if (query["t"] != token) return respond(out, 403, "text/plain", "Scan the code on your TV again.")

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val limit = if (path == "/wallpaper") 25_000_000 else 16_000
        if (length > limit) return respond(out, 413, "text/plain", "Too large")
        val body = readBody(input, length)

        when {
            method == "GET" && path == "/" -> respond(out, 200, "text/html; charset=utf-8", PAGE)
            method == "POST" && path == "/pin" -> {
                val form = parseForm(String(body))
                val url = form["url"]?.trim().orEmpty()
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return respond(out, 400, "text/plain", "That doesn't look like a link.")
                }
                onPin(url, form["title"]?.trim()?.ifBlank { null })
                respond(out, 200, "text/plain", "ok")
            }
            method == "POST" && path == "/zip" -> {
                val zip = parseForm(String(body))["zip"]?.trim().orEmpty()
                if (zip.length < 3) return respond(out, 400, "text/plain", "Enter a ZIP code or city.")
                onZip(zip)
                respond(out, 200, "text/plain", "ok")
            }
            method == "POST" && path == "/wallpaper" -> {
                if (body.isEmpty()) return respond(out, 400, "text/plain", "No image received.")
                onWallpaper(body)
                respond(out, 200, "text/plain", "ok")
            }
            else -> respond(out, 404, "text/plain", "Not found")
        }
    }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (buf.size() < 16_384) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b == 13) 1 else 0
            if (matched == 4) return buf.toString(Charsets.ISO_8859_1.name()).trimEnd()
        }
        return null
    }

    private fun readBody(input: InputStream, length: Int): ByteArray {
        val body = ByteArray(length)
        var n = 0
        while (n < length) {
            val r = input.read(body, n, length - n)
            if (r < 0) break
            n += r
        }
        return if (n == length) body else body.copyOf(n)
    }

    private fun parseForm(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate {
        val k = it.substringBefore('=')
        val v = it.substringAfter('=', "")
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 413 -> "Payload Too Large"; else -> "Not Found" }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    companion object {
        const val PORT = 8642

        private val PAGE = """
<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Dash</title>
<style>
:root{--bg:#0B0B0A;--panel:#1A1917;--text:#F2EEE6;--muted:#A39D92;--accent:#5AA9FF;--on:#04121F;--bezel:#3A3732}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.45 system-ui,-apple-system,sans-serif;padding:24px 16px 48px}
main{max-width:520px;margin:0 auto;display:flex;flex-direction:column;gap:28px}
h1{font-size:28px;letter-spacing:-.02em;margin:0}p{margin:0;color:var(--muted)}
section{background:var(--panel);border-radius:20px;padding:20px;display:flex;flex-direction:column;gap:12px}
h2{font-size:15px;color:var(--muted);margin:0;font-weight:500}
input{width:100%;font:inherit;color:var(--text);background:#0F0F0E;border:1px solid var(--bezel);border-radius:12px;padding:14px}
input:focus{outline:2px solid var(--accent);outline-offset:1px}
button,label.btn{font:600 16px system-ui;background:var(--accent);color:var(--on);border:0;border-radius:12px;padding:14px;text-align:center;cursor:pointer}
input[type=file]{display:none}
#msg{min-height:22px;color:var(--accent);font-weight:600}
</style></head><body><main>
<div><h1>Dash</h1><p>Send things to your TV. Nothing leaves your home network.</p></div>
<section id="s-pin"><h2>Pin a show</h2>
<form id="pin"><input name="url" type="url" placeholder="Paste a share link" required><div style="height:10px"></div>
<input name="title" placeholder="Title (optional)"><div style="height:12px"></div><button style="width:100%">Pin to TV</button></form></section>
<section id="s-zip"><h2>Weather location</h2>
<form id="zip"><input name="zip" inputmode="numeric" autocomplete="postal-code" placeholder="ZIP code" required><div style="height:12px"></div><button style="width:100%">Save</button></form></section>
<section id="s-wall"><h2>Wallpaper</h2><label class="btn">Choose a photo<input id="wp" type="file" accept="image/*"></label></section>
<div id="msg"></div>
</main><script>
const q=new URLSearchParams(location.search),T=q.get('t'),M=q.get('m');
if(M){const s=document.getElementById('s-'+M);if(s)s.parentNode.insertBefore(s,s.parentNode.children[1]);}
async function post(p,body,type){msg.textContent='Sending…';
 try{const r=await fetch(p+'?t='+T,{method:'POST',body,headers:{'Content-Type':type}});
 msg.textContent=r.ok?'Sent to your TV ✓':await r.text();}catch(e){msg.textContent='Could not reach the TV. Is the code still on screen?';}}
pin.onsubmit=e=>{e.preventDefault();post('/pin',new URLSearchParams(new FormData(pin)),'application/x-www-form-urlencoded');pin.reset();};
zip.onsubmit=e=>{e.preventDefault();post('/zip',new URLSearchParams(new FormData(zip)),'application/x-www-form-urlencoded');};
wp.onchange=()=>{const f=wp.files[0];if(f)post('/wallpaper',f,f.type||'application/octet-stream');};
</script></body></html>
""".trimIndent()
    }
}

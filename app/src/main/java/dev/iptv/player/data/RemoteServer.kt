package dev.iptv.player.data

import android.content.Context
import dev.iptv.player.R
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

enum class PhoneMode { ADD, BACKUP }

interface RemoteListener {
    fun onPlaylistAdded(name: String)
    fun onBackupExported()
    fun onBackupRestored()
}

/**
 * Tiny LAN-only HTTP server so a phone can add a playlist (or move a backup) without typing on the TV remote.
 * Every request needs the random [token] in `?t=`; only private-range clients are served; bodies are size limited.
 * The owner screen must call [stop] when it closes.
 */
class RemoteServer private constructor(
    host: String,
    port: Int,
    private val repo: Repository,
    private val mode: PhoneMode,
    private val listener: RemoteListener,
    val token: String,
) : NanoHTTPD(host, port) {

    val url: String = "http://$host:$port/?t=$token"

    @Volatile private var failures = 0
    private val json = Json { ignoreUnknownKeys = true }

    override fun serve(session: IHTTPSession): Response {
        val context = runBlocking { repo.settings.localizedContext(repo.appContext) }
        if (!isLocalClient(session.remoteIpAddress)) return plain(Response.Status.FORBIDDEN, context.getString(R.string.data_remote_forbidden))
        if (failures >= MAX_FAILURES) return plain(Response.Status.FORBIDDEN, context.getString(R.string.data_remote_locked))
        val given = session.parameters["t"]?.firstOrNull().orEmpty()
        if (!MessageDigest.isEqual(given.toByteArray(), token.toByteArray())) {
            failures++
            return plain(Response.Status.FORBIDDEN, context.getString(R.string.data_remote_forbidden))
        }
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> html(if (mode == PhoneMode.ADD) addPage(context) else backupPage(context))
                mode == PhoneMode.ADD && session.method == Method.POST && session.uri == "/api/add" -> handleAdd(session, context)
                mode == PhoneMode.BACKUP && session.method == Method.GET && session.uri == "/api/backup" -> handleExport()
                mode == PhoneMode.BACKUP && session.method == Method.POST && session.uri == "/api/restore" -> handleRestore(session, context)
                else -> plain(Response.Status.NOT_FOUND, context.getString(R.string.data_remote_not_found))
            }
        } catch (e: InvalidBackupException) {
            val message = e.unsupportedVersion?.let { context.getString(R.string.data_backup_version, it) }
                ?: context.getString(R.string.data_backup_invalid)
            jsonResponse(Response.Status.BAD_REQUEST, false, message)
        } catch (_: SerializationException) {
            jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_request_invalid))
        } catch (_: IllegalArgumentException) {
            jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_request_invalid))
        } catch (_: UnsupportedOperationException) {
            jsonResponse(Response.Status.INTERNAL_ERROR, false, context.getString(R.string.data_backup_unavailable))
        } catch (_: Exception) {
            // Parser and network exception messages can contain submitted passwords or full URLs.
            jsonResponse(Response.Status.INTERNAL_ERROR, false, context.getString(R.string.data_operation_failed))
        }
    }

    private fun readBody(session: IHTTPSession, limit: Long): String? {
        val length = session.headers["content-length"]?.toLongOrNull() ?: return null
        if (length < 0 || length > limit) return null
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"]
    }

    private fun handleAdd(session: IHTTPSession, context: Context): Response {
        val body = readBody(session, ADD_LIMIT) ?: return jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_request_invalid))
        val o = json.parseToJsonElement(body).jsonObject
        fun field(name: String) = (o[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().take(FIELD_LIMIT)
        val name = field("name")
        val source: PlaylistSource
        if (field("type") == "xtream") {
            val server = field("server")
            if (!server.startsWith("http://") && !server.startsWith("https://")) return jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_url_scheme))
            if (field("username").isEmpty() || field("password").isEmpty()) return jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_credentials_required))
            source = PlaylistSource.Xtream(server, field("username"), field("password"))
        } else {
            val url = field("url")
            if (!url.startsWith("http://") && !url.startsWith("https://")) return jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_url_scheme))
            source = PlaylistSource.M3u(url)
        }
        val label = name.ifBlank { if (source is PlaylistSource.Xtream) source.server else context.getString(R.string.data_m3u_playlist) }
        val playlist = Playlist(UUID.randomUUID().toString(), label, source)
        try {
            runBlocking {
                repo.verify(playlist)
                repo.store.add(playlist)
            }
        } catch (e: Exception) {
            return jsonResponse(Response.Status.OK, false, (e as? ProviderMessage)?.message ?: context.getString(R.string.data_verification_failed))
        }
        listener.onPlaylistAdded(label)
        return jsonResponse(Response.Status.OK, true, context.getString(R.string.data_playlist_added, label))
    }

    private fun handleExport(): Response {
        val data = BackupSource.current.exportJson()
        listener.onBackupExported()
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", data).apply {
            addHeader("Content-Disposition", "attachment; filename=\"iptv-yedek.json\"")
            addHeader("Cache-Control", "no-store")
        }
    }

    private fun handleRestore(session: IHTTPSession, context: Context): Response {
        val body = readBody(session, RESTORE_LIMIT) ?: return jsonResponse(Response.Status.BAD_REQUEST, false, context.getString(R.string.data_file_invalid))
        BackupSource.current.importJson(body)
        listener.onBackupRestored()
        return jsonResponse(Response.Status.OK, true, context.getString(R.string.data_backup_restored))
    }

    private fun jsonResponse(status: Response.IStatus, ok: Boolean, message: String): Response {
        val o: JsonObject = buildJsonObject {
            put("ok", ok)
            put("message", message)
        }
        return newFixedLengthResponse(status, "application/json; charset=utf-8", o.toString()).apply { addHeader("Cache-Control", "no-store") }
    }

    private fun plain(status: Response.IStatus, text: String) = newFixedLengthResponse(status, "text/plain; charset=utf-8", text)

    private fun html(body: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body).apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
        addHeader("Referrer-Policy", "no-referrer")
    }

    companion object {
        private const val MAX_FAILURES = 20
        private const val ADD_LIMIT = 8 * 1024L
        private const val RESTORE_LIMIT = 8 * 1024 * 1024L
        private const val FIELD_LIMIT = 2000
        private val PORTS = 8090..8110
        private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

        fun newToken(): String {
            val r = SecureRandom()
            return buildString { repeat(6) { append(ALPHABET[r.nextInt(ALPHABET.length)]) } }
        }

        fun isLocalClient(ip: String?): Boolean {
            if (ip.isNullOrEmpty()) return false
            return runCatching {
                val a = InetAddress.getByName(ip.substringBefore('%'))
                a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress
            }.getOrDefault(false)
        }

        /** The TV's private IPv4 address on the LAN, if any. */
        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        /** Starts a server on the first free port; null when there is no LAN address or no port is free. */
        fun start(repo: Repository, mode: PhoneMode, listener: RemoteListener): RemoteServer? {
            val host = lanAddress() ?: return null
            val token = newToken()
            for (port in PORTS) {
                val server = RemoteServer(host, port, repo, mode, listener, token)
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (_: Exception) {
                    // port busy, try the next one
                }
            }
            return null
        }
    }
}

private const val STYLE = """
<style>
*{box-sizing:border-box}body{margin:0;font-family:system-ui,sans-serif;background:#080B10;color:#E8ECF2;padding:20px}
.card{max-width:520px;margin:0 auto;background:#111620;border-radius:18px;padding:22px}
h1{font-size:22px;margin:0 0 6px}p{color:#9AA5B4;margin:0 0 16px;font-size:15px}
label{display:block;font-size:13px;color:#9AA5B4;margin:12px 0 4px}
input,button{width:100%;font-size:17px;padding:13px;border-radius:12px;border:2px solid #2A3344;background:#1B2230;color:#E8ECF2}
button{background:#5B8CFF;border:0;font-weight:700;margin-top:18px}button:disabled{opacity:.5}
.tabs{display:flex;gap:8px;margin-bottom:8px}.tabs button{margin:0;background:#1B2230;border:2px solid #2A3344}.tabs button.on{border-color:#5B8CFF}
#msg{margin-top:16px;padding:12px;border-radius:12px;display:none;font-size:15px}.ok{background:#12301f;color:#7be0a0}.err{background:#3a151a;color:#ff8f9b}
a.btn{display:block;text-align:center;text-decoration:none;background:#5B8CFF;color:#fff;border-radius:12px;padding:14px;font-weight:700;margin-top:10px}
</style>
"""

private fun htmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private fun Context.htmlText(resource: Int): String = htmlEscape(getString(resource))

/** JSON quoting handles JavaScript syntax; escaping '<' also prevents an embedded closing script tag. */
private fun Context.scriptText(resource: Int): String = JsonPrimitive(getString(resource)).toString()
    .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
    .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

private fun addPage(context: Context): String = with(context) {
    """<!doctype html><html lang="${htmlEscape(resources.configuration.locales[0].language)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${htmlText(R.string.data_add_title)}</title>$STYLE</head><body><div class="card">
<h1>${htmlText(R.string.data_add_title)}</h1><p>${htmlText(R.string.data_add_hint)}</p>
<div class="tabs"><button id="tx" class="on" type="button">Xtream</button><button id="tm" type="button">${htmlText(R.string.data_m3u_link)}</button></div>
<label for="nm">${htmlText(R.string.data_optional_name)}</label><input id="nm" autocomplete="off">
<div id="fx"><label for="server">${htmlText(R.string.data_server_url)}</label><input id="server" placeholder="http://example.com:8080" inputmode="url" autocapitalize="none" autocomplete="off">
<label for="user">${htmlText(R.string.data_username)}</label><input id="user" autocapitalize="none" autocomplete="off"><label for="pass">${htmlText(R.string.data_password)}</label><input id="pass" type="password" autocomplete="off"></div>
<div id="fm" style="display:none"><label for="url">${htmlText(R.string.data_m3u_url)}</label><input id="url" placeholder="http://..." inputmode="url" autocapitalize="none" autocomplete="off"></div>
<button id="go" type="button">${htmlText(R.string.data_send_tv)}</button><div id="msg" role="status" aria-live="polite"></div></div>
<script>
var x=true,q=new URLSearchParams(location.search).get('t');
function mode(v){x=v;fx.style.display=v?'':'none';fm.style.display=v?'none':'';tx.className=v?'on':'';tm.className=v?'':'on'}
tx.onclick=function(){mode(true)};tm.onclick=function(){mode(false)};
go.onclick=function(){go.disabled=true;msg.style.display='block';msg.className='';msg.textContent=${scriptText(R.string.data_verifying)};
var b=x?{type:'xtream',name:nm.value,server:server.value,username:user.value,password:pass.value}:{type:'m3u',name:nm.value,url:url.value};
fetch('/api/add?t='+encodeURIComponent(q),{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify(b)})
.then(function(r){return r.json()}).then(function(j){msg.className=j.ok?'ok':'err';msg.textContent=j.message;go.disabled=false})
.catch(function(){msg.className='err';msg.textContent=${scriptText(R.string.data_tv_unreachable)};go.disabled=false})};
</script></body></html>"""
}

private fun backupPage(context: Context): String = with(context) {
    """<!doctype html><html lang="${htmlEscape(resources.configuration.locales[0].language)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${htmlText(R.string.data_backup_title)}</title>$STYLE</head><body><div class="card">
<h1>${htmlText(R.string.data_backup_title)}</h1><p>${htmlText(R.string.data_backup_hint)}</p>
<a class="btn" id="dl" href="#">${htmlText(R.string.data_backup_download)}</a>
<label for="f" style="margin-top:22px">${htmlText(R.string.data_backup_file)}</label><input id="f" type="file" accept=".json,application/json">
<button id="go" type="button">${htmlText(R.string.data_restore)}</button><div id="msg" role="status" aria-live="polite"></div></div>
<script>
var q=new URLSearchParams(location.search).get('t');dl.href='/api/backup?t='+encodeURIComponent(q);
go.onclick=function(){var file=f.files[0];msg.style.display='block';if(!file){msg.className='err';msg.textContent=${scriptText(R.string.data_choose_file)};return}
go.disabled=true;msg.className='';msg.textContent=${scriptText(R.string.data_uploading)};
file.text().then(function(t){return fetch('/api/restore?t='+encodeURIComponent(q),{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:t})})
.then(function(r){return r.json()}).then(function(j){msg.className=j.ok?'ok':'err';msg.textContent=j.message;go.disabled=false})
.catch(function(){msg.className='err';msg.textContent=${scriptText(R.string.data_operation_failed)};go.disabled=false})};
</script></body></html>"""
}

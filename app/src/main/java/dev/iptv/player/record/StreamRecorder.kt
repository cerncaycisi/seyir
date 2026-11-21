package dev.iptv.player.record

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

enum class RecorderError { EMPTY_RESPONSE, INTERRUPTED, ENCRYPTED_HLS, UNSUPPORTED_HLS, HTTP }

/** Technical failure code; the service supplies a localized message without exposing source URLs. */
class RecorderException(val reason: RecorderError, val httpCode: Int = 0) : IOException(reason.name)

/**
 * Writes a live stream to [out] by downloading the raw bytes. Blocking: call [run] from an IO thread, [stop] from any other.
 *
 *  - Progressive streams (MPEG-TS over HTTP, the usual Xtream `.ts` URL): bytes are copied as they arrive; the connection
 *    is re-opened (appending) when it drops.
 *  - HLS (`#EXTM3U`): the media playlist is polled and every new segment is appended in order. A master playlist is resolved
 *    to its highest-bandwidth variant. Segments are concatenated, which yields a valid .ts (or fMP4 when an init segment exists).
 *
 * Limitations: encrypted or byte-range HLS is rejected, DASH/RTMP/RTSP are not supported, the highest-bitrate variant is
 * always recorded, and no remuxing happens (the file keeps the source container).
 */
class StreamRecorder(
    private val client: OkHttpClient,
    private val url: String,
    private val headers: Map<String, String>,
    private val out: File,
    private val onBytes: (Long) -> Unit = {},
) {
    @Volatile private var stopped = false
    @Volatile private var call: Call? = null

    /** Bytes written so far. */
    @Volatile var written = 0L
        private set

    fun stop() {
        stopped = true
        call?.cancel()
    }

    @Throws(IOException::class)
    fun run() {
        FileOutputStream(out, true).use { fos ->
            val os = fos.buffered(256 * 1024)
            try {
                val first = open(url)
                val body = first.body ?: run { first.close(); throw RecorderException(RecorderError.EMPTY_RESPONSE) }
                val source = body.source()
                val hls = source.request(7) && source.peek().readUtf8(7) == "#EXTM3U"
                if (hls) {
                    val text = body.string()
                    val base = first.request.url.toString()
                    first.close()
                    recordHls(text, base, os)
                } else {
                    recordProgressive(first, os)
                }
            } catch (e: IOException) {
                if (!stopped) throw e
            } finally {
                runCatching { os.flush() }
            }
        }
    }

    private fun open(u: String): Response {
        val req = Request.Builder().url(u).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val c = client.newCall(req)
        call = c
        if (stopped) c.cancel()
        val r = c.execute()
        if (!r.isSuccessful) {
            val code = r.code
            r.close()
            throw RecorderException(RecorderError.HTTP, code)
        }
        return r
    }

    private fun copy(source: BufferedSource, os: OutputStream) {
        val buf = ByteArray(64 * 1024)
        while (!stopped) {
            val n = source.read(buf)
            if (n < 0) return
            os.write(buf, 0, n)
            written += n
            onBytes(written)
        }
    }

    private fun sleep(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (!stopped && System.currentTimeMillis() < end) Thread.sleep(minOf(250L, ms))
    }

    private fun recordProgressive(firstResponse: Response, os: OutputStream) {
        var response: Response? = firstResponse
        var failures = 0
        while (!stopped) {
            val r = response ?: try {
                open(url)
            } catch (e: IOException) {
                if (stopped) break
                if (++failures > MAX_FAILURES) throw e
                sleep(2_000L * failures)
                continue
            }
            response = null
            val before = written
            try {
                r.use { resp -> copy((resp.body ?: throw RecorderException(RecorderError.EMPTY_RESPONSE)).source(), os) }
            } catch (e: IOException) {
                if (stopped) break
            }
            if (written > before) failures = 0 else if (++failures > MAX_FAILURES) throw RecorderException(RecorderError.INTERRUPTED)
            sleep(1_000)
        }
    }

    private fun fetchText(u: String): Pair<String, String> {
        open(u).use { r ->
            return (r.body?.string() ?: "") to r.request.url.toString()
        }
    }

    private fun download(u: String, os: OutputStream) {
        // A disconnected segment must not leave partial bytes in the final file before it is retried.
        val part = File.createTempFile("record-segment-", ".part", out.parentFile)
        try {
            open(u).use { r ->
                val source = (r.body ?: throw RecorderException(RecorderError.EMPTY_RESPONSE)).source()
                part.outputStream().buffered().use { target ->
                    val bytes = ByteArray(64 * 1024)
                    while (!stopped) {
                        val n = source.read(bytes)
                        if (n < 0) break
                        target.write(bytes, 0, n)
                    }
                }
            }
            if (!stopped) part.inputStream().use { input ->
                val bytes = ByteArray(64 * 1024)
                while (!stopped) {
                    val n = input.read(bytes)
                    if (n < 0) break
                    os.write(bytes, 0, n)
                    written += n
                    onBytes(written)
                }
            }
        } finally {
            part.delete()
        }
    }

    private fun recordHls(firstText: String, firstUrl: String, os: OutputStream) {
        var playlistUrl = firstUrl
        var parsed = HlsPlaylist.parse(firstText, playlistUrl)
        if (parsed.encrypted) throw RecorderException(RecorderError.ENCRYPTED_HLS)
        if (parsed.unsupported) throw RecorderException(RecorderError.UNSUPPORTED_HLS)
        parsed.bestVariant()?.let { v ->
            val (text, resolved) = fetchText(v.url)
            playlistUrl = resolved
            parsed = HlsPlaylist.parse(text, playlistUrl)
        }
        var lastSeq = -1L
        var wroteInit: String? = null
        var errors = 0
        while (!stopped) {
            if (parsed.encrypted) throw RecorderException(RecorderError.ENCRYPTED_HLS)
            if (parsed.unsupported || parsed.variants.isNotEmpty()) throw RecorderException(RecorderError.UNSUPPORTED_HLS)
            val init = parsed.initUrl
            if (wroteInit != null && init != wroteInit) throw RecorderException(RecorderError.UNSUPPORTED_HLS)
            if (wroteInit == null && init != null) {
                download(init, os)
                wroteInit = init
            }
            // Start near "now": only the newest segments of the first window, then everything that appears.
            val fresh = if (lastSeq < 0) parsed.segments.takeLast(START_SEGMENTS) else parsed.segments.filter { it.seq > lastSeq }
            var failedSegment = false
            for (s in fresh) {
                if (stopped) break
                try {
                    download(s.url, os)
                    errors = 0
                    lastSeq = s.seq
                } catch (e: IOException) {
                    if (stopped) break
                    if (++errors > MAX_FAILURES) throw e
                    failedSegment = true
                    break
                }
            }
            if ((parsed.endList && !failedSegment) || stopped) break
            sleep(maxOf(1_000L, parsed.targetDurationSec * 500L))
            try {
                val (text, resolved) = fetchText(playlistUrl)
                playlistUrl = resolved
                parsed = HlsPlaylist.parse(text, playlistUrl)
            } catch (e: IOException) {
                if (stopped) break
                if (++errors > MAX_FAILURES) throw e
            }
        }
    }

    companion object {
        private const val MAX_FAILURES = 5
        private const val START_SEGMENTS = 2

        fun newClient(userAgent: String): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", userAgent).build() else req)
            }
            .build()
    }
}

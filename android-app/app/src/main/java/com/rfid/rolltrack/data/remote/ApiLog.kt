package com.rfid.rolltrack.data.remote

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One HTTP call to the ERP, as seen by the app. Passwords are masked before storing.
 */
data class ApiCall(
    val id: Long,
    val at: Long,
    val method: String,
    val url: String,
    val requestBody: String?,
    val httpCode: Int?,
    val responseBody: String?,
    val durationMs: Long,
    val error: String?,
    /** Filled in by OrdsReply after interpreting the body: OK / REJECTED / UNCERTAIN */
    val verdict: String? = null,
    val verdictMessage: String? = null
) {
    val ok: Boolean get() = error == null && httpCode != null && httpCode in 200..299 && verdict != "REJECTED"

    fun toText(): String = buildString {
        append("[").append(ApiLog.fmt(at)).append("] ").append(method).append(' ').append(url).append('\n')
        if (!requestBody.isNullOrBlank()) append("  request : ").append(requestBody).append('\n')
        append("  http    : ").append(httpCode?.toString() ?: "-").append("  (").append(durationMs).append(" ms)\n")
        if (error != null) append("  error   : ").append(error).append('\n')
        if (responseBody != null) append("  response: ").append(responseBody.take(2000)).append('\n')
        if (verdict != null) append("  verdict : ").append(verdict).append(" - ").append(verdictMessage.orEmpty()).append('\n')
    }
}

/**
 * In-memory log of the last [MAX] API calls, mirrored to Logcat under tag "RollTrack".
 * Watch live with:  adb logcat -s RollTrack:*
 */
object ApiLog {
    const val TAG = "RollTrack"
    private const val MAX = 100

    private val _calls = MutableStateFlow<List<ApiCall>>(emptyList())
    val calls: StateFlow<List<ApiCall>> = _calls.asStateFlow()

    private var nextId = 1L

    @Synchronized
    fun newId(): Long = nextId++

    fun record(call: ApiCall) {
        val masked = call.copy(
            url = mask(call.url),
            requestBody = call.requestBody?.let { mask(it) },
            responseBody = call.responseBody?.let { shortenHtml(it) }
        )
        _calls.update { (listOf(masked) + it).take(MAX) }
        val line = masked.toText().trimEnd()
        if (masked.error != null || (masked.httpCode ?: 0) !in 200..299) safeLog(Log.ERROR, line) else safeLog(Log.DEBUG, line)
    }

    /** Attach the app's interpretation of the response body to an already-logged call. */
    fun setVerdict(id: Long, verdict: String, message: String) {
        _calls.update { list -> list.map { if (it.id == id) it.copy(verdict = verdict, verdictMessage = message) else it } }
        safeLog(if (verdict == "REJECTED") Log.ERROR else if (verdict == "UNCERTAIN") Log.WARN else Log.INFO, "call #$id verdict=$verdict : $message")
    }

    fun info(msg: String) = safeLog(Log.INFO, msg)
    fun warn(msg: String) = safeLog(Log.WARN, msg)

    fun clear() { _calls.value = emptyList() }

    fun dump(): String = _calls.value.joinToString("\n") { it.toText() }

    fun fmt(t: Long): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(t))

    /** ORDS error pages (404 etc.) are long HTML: keep only their title so the log stays readable. */
    private fun shortenHtml(body: String): String {
        val t = body.trimStart()
        if (!t.startsWith("<")) return body
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.trim()
        val h1 = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(t)
            ?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
        return "(HTML page, ${body.length} chars) " + listOfNotNull(title, h1).distinct().joinToString(" - ").ifBlank { "no title" }
    }

    private fun mask(s: String): String =
        s.replace(Regex("(USER_PASSWORD=)[^&]*", RegexOption.IGNORE_CASE), "$1***")
            .replace(Regex("(\"USER_PASSWORD\"\\s*:\\s*\")[^\"]*", RegexOption.IGNORE_CASE), "$1***")

    private fun safeLog(priority: Int, msg: String) {
        // Logcat truncates ~4000 chars per line; split long entries.
        try {
            msg.chunked(3500).forEach { Log.println(priority, TAG, it) }
        } catch (_: Throwable) {
            // android.util.Log is not available in local JVM unit tests
        }
    }
}

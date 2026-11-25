package com.rfid.rolltrack.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns a raw ORDS answer into success / rejection. Never assumes success.
 *
 * The IDEAERP PL/SQL procedures behind the PDA endpoints answer through P_RESPONSE with:
 *    "0"  -> request body was empty
 *    "-1" -> USER_ID missing, or a database exception (WHEN OTHERS)
 *    "-2" -> REF_ID missing
 *    "-3" -> ITEMS missing / not an array
 *    anything else (e.g. "4 item(s)") -> success text
 * The ORDS handler may wrap that in JSON ({"P_RESPONSE": ".."}, {"STATUS":"E","MESSAGE":".."}) or send plain text,
 * so all shapes are handled.
 */
object OrdsReply {

    private val procCodes = mapOf(
        "0" to "Server got an empty request body (code 0)",
        "-1" to "Server rejected: USER_ID missing or DB error (code -1)",
        "-2" to "Server rejected: REF_ID missing (code -2)",
        "-3" to "Server rejected: ITEMS list missing (code -3)"
    )

    private val messageKeys = arrayOf("P_RESPONSE", "RESPONSE", "MESSAGE", "MSG", "RESULT", "OUT", "P_OUT4", "P_OUT")
    private val statusKeys = arrayOf("STATUS", "P_STATUS", "SUCCESS")

    /**
     * @param endpoint short label for messages, e.g. "load"
     * @return BulkResult(ok=true) when the server clearly accepted, or (ok=true, uncertain=true) when it answered 2xx
     *         without a usable message.
     * @param sentTags the tags in the request. When given, a bulk reply such as
     *        "2 loaded, 1 rejected - <tag> <reason>; ..." is split into per-tag [ItemOutcome]s so only the
     *        rejected tags are marked FAILED on the device.
     * @throws ApiException when the server clearly rejected (HTTP error or a negative/zero procedure code).
     */
    fun interpret(
        endpoint: String,
        res: RawResponse,
        successFallback: String = "Accepted",
        sentTags: List<String> = emptyList()
    ): BulkResult {
        val raw = res.body
        val trimmed = raw.trim()

        if (res.code !in 200..299) {
            val (code, msg) = when (res.code) {
                404 -> "NOT_ON_SERVER" to "'$endpoint' does not exist on the ERP server (HTTP 404)"
                405 -> "WRONG_METHOD" to "'$endpoint' exists but not for this HTTP method (HTTP 405)"
                401, 403 -> "NOT_AUTHORIZED" to "ERP server refused access to '$endpoint' (HTTP ${res.code})"
                in 500..599 -> "SERVER_ERROR" to "ERP server error on '$endpoint' (HTTP ${res.code}): ${snippet(trimmed)}"
                else -> "HTTP_${res.code}" to "Unexpected HTTP ${res.code} from '$endpoint': ${snippet(trimmed)}"
            }
            reject(res, code, msg)
        }

        var message: String? = null
        var status: String? = null
        val el = try { LenientJson.json.parseToJsonElement(trimmed) } catch (_: Exception) { null }
        when (el) {
            is JsonObject -> {
                // ORDS handlers report exceptions as {"error":"ORA-01403: no data found"} with HTTP 200.
                val error = LenientJson.findString(el, "error")?.trim()
                if (!error.isNullOrEmpty()) reject(res, "SERVER_ERROR", "Server DB error on '$endpoint': $error")
                status = LenientJson.findString(el, *statusKeys)
                message = LenientJson.findString(el, *messageKeys)
                if (message == null) {
                    // ORDS collection format: {"items":[{...}]}
                    val first = LenientJson.findArray(el, "items")?.firstOrNull() as? JsonObject
                    if (first != null) {
                        status = status ?: LenientJson.findString(first, *statusKeys)
                        message = LenientJson.findString(first, *messageKeys)
                    }
                }
            }
            is JsonPrimitive -> message = el.content
            else -> if (trimmed.isNotEmpty() && !trimmed.startsWith("<")) message = trimmed
        }

        val msg = message?.trim()?.trim('"')
        if (status != null && status.trim().uppercase() in setOf("E", "ERROR", "F", "FAIL", "FAILED", "N", "FALSE")) {
            // The ORDS handlers map the procedures' catch-all code -1 to "User ID not found",
            // so this text also appears for DB errors such as a non-numeric REF_ID.
            val hint = if (msg.equals("User ID not found", true))
                " (server uses this text for any DB error too, e.g. an invalid REF_ID or tag list)" else ""
            reject(res, "REJECTED", (msg ?: "Server returned STATUS=$status") + hint)
        }
        if (msg != null && procCodes.containsKey(msg)) {
            reject(res, "PROC_$msg", procCodes.getValue(msg))
        }
        // Any other negative code, e.g. "-9: ORA-xxxxx" from the fixed procedures.
        if (msg != null && Regex("^-\\d+").containsMatchIn(msg)) {
            reject(res, "PROC_ERROR", "Server DB error: $msg")
        }
        // "0 loaded, 2 rejected (...)" - nothing was saved.
        if (msg != null && Regex("^0 (loaded|unloaded|issued|saved)\\b", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
            reject(res, "NONE_SAVED", msg)
        }
        if (msg.isNullOrBlank()) {
            val text = "Server answered HTTP ${res.code} with no message - DB write NOT confirmed"
            if (res.callId != 0L) ApiLog.setVerdict(res.callId, "UNCERTAIN", text)
            return BulkResult(ok = true, message = text, httpCode = res.code, raw = raw, uncertain = true)
        }
        val bulk = if (sentTags.isNotEmpty()) parseBulk(msg, sentTags) else null
        if (bulk != null && bulk.rejected > 0) {
            val items = bulk.reasons.map { (tag, why) ->
                ItemOutcome(tag, accepted = false, code = "REJECTED", message = why.ifBlank { "rejected by ERP" })
            }
            // Some rejected tags were not named (server cut the list short): the device cannot tell which,
            // so the batch is flagged uncertain instead of silently counting them as saved.
            val unnamed = bulk.rejected - items.size
            val text = if (unnamed > 0) "$msg ($unnamed rejected tag(s) not named by the server - check them in the ERP)" else msg
            val uncertain = unnamed > 0
            if (res.callId != 0L) ApiLog.setVerdict(res.callId, if (uncertain) "UNCERTAIN" else "PARTIAL", text)
            return BulkResult(ok = true, message = text, items = items, httpCode = res.code, raw = raw, uncertain = uncertain)
        }
        if (res.callId != 0L) ApiLog.setVerdict(res.callId, "OK", msg)
        return BulkResult(ok = true, message = msg.ifBlank { successFallback }, httpCode = res.code, raw = raw)
    }

    internal data class BulkReply(val accepted: Int, val rejected: Int, val reasons: Map<String, String>)

    private val bulkHead = Regex("^(\\d+) (loaded|unloaded|issued|saved), (\\d+) rejected", RegexOption.IGNORE_CASE)

    /**
     * Parses the reply of the fixed bulk procedures (see docs/proposed_db_fix.sql):
     *   "<n> loaded, <m> rejected[, <k> duplicate(s) ignored][ - <tag> <reason>; <tag> <reason>...]"
     * Reason text may itself contain "; ", so a fragment only starts a new entry when its first word is one of
     * [sentTags] (compared ignoring case; the returned keys are the tags exactly as sent).
     * @return null when [msg] is not in that format.
     */
    internal fun parseBulk(msg: String, sentTags: List<String>): BulkReply? {
        val head = bulkHead.find(msg) ?: return null
        val accepted = head.groupValues[1].toInt()
        val rejected = head.groupValues[3].toInt()
        val reasons = LinkedHashMap<String, String>()
        val dash = msg.indexOf(" - ", head.range.last + 1)
        if (dash >= 0) {
            val bySent = sentTags.associateBy { it.trim().uppercase() }
            var current: String? = null
            for (part in msg.substring(dash + 3).split("; ")) {
                val p = part.trim()
                if (p.isEmpty()) continue
                val space = p.indexOf(' ')
                val first = if (space < 0) p else p.substring(0, space)
                val tag = bySent[first.uppercase()]
                if (tag != null) {
                    current = tag
                    reasons[tag] = if (space < 0) "" else p.substring(space + 1).trim()
                } else if (current != null) {
                    reasons[current] = reasons.getValue(current) + "; " + p
                }
            }
        }
        return BulkReply(accepted, rejected, reasons)
    }

    private fun reject(res: RawResponse, code: String, msg: String): Nothing {
        if (res.callId != 0L) ApiLog.setVerdict(res.callId, "REJECTED", msg)
        throw ApiException(code = code, message = msg, httpCode = res.code, raw = res.body)
    }

    private fun snippet(body: String): String {
        // ORDS error pages (e.g. HTTP 555) carry the real Oracle error inside: show that, not the page's CSS.
        val ora = Regex("ORA-\\d{5}:[^\\\\\"<\\n]*").findAll(body).map { it.value.trim() }.distinct().toList()
        if (ora.isNotEmpty()) {
            val main = ora.firstOrNull { !it.startsWith("ORA-06550") } ?: ora.first()
            return (listOf(main) + ora.filter { it != main && it.startsWith("ORA-06550") }.take(1)).joinToString(" / ")
        }
        val text = body.replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), " ")
        return text.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(160).ifBlank { "(empty body)" }
    }
}

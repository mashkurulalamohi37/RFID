package com.rfid.rolltrack.data.remote

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Client for the ORDS "PDA" module (snym_erp/api). Endpoints that exist on the server (open-api-catalog):
 *   POST pda-login, POST req-scan, POST load, POST unload, PUT ref-weight, POST sub-contract-issue
 * Request field names follow the backend's existing parameters exactly (see PDA.postman_collection.json):
 *   load / unload        : { USER_ID, ITEMS[] }
 *   sub-contract-issue   : { REF_ID, USER_ID, ITEMS[] }
 *   ref-weight (query)   : USER_ID, ITEM, WEIGHT
 *   req-scan (query)     : USER_ID, REF_CODE_NO
 *   pda-login (query)    : USER_NAME, USER_PASSWORD
 * work-orders, program, loading-expected, store-delivery, trace are NOT on the server yet (HTTP 404).
 */
class OrdsErpApi(
    private val http: HttpTransport,
    private val getBaseUrl: () -> String,
    private val getFormat: () -> com.rfid.rolltrack.scanner.TagIdNormalizer.ErpFormat
) : ErpApi {

    private fun ordsUrl(path: String): String = "${getBaseUrl().trimEnd('/')}/$path"

    private fun enc(value: String): String = URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name())

    private fun itemsPayload(userId: String, tags: List<String>, refId: String? = null): String = buildJsonObject {
        if (refId != null) put("REF_ID", refId)
        put("USER_ID", userId)
        putJsonArray("ITEMS") { tags.forEach { add(JsonPrimitive(it)) } }
    }.toString()

    override suspend fun login(user: String, pass: String): LoginResult {
        // Kept permissive on purpose for now (dev phase, working under the Sumon account).
        // Every call and its raw answer is visible in Diagnostics / Logcat.
        val url = ordsUrl("snym_erp/api/pda-login?USER_NAME=${enc(user)}&USER_PASSWORD=${enc(pass)}")
        val res = http.postJson(url, "{}")
        val obj = LenientJson.parseObject(res.body)
        val status = LenientJson.findString(obj, "STATUS", "status")
        if (status.equals("E", ignoreCase = true)) {
            val msg = LenientJson.findString(obj, "MESSAGE", "message") ?: "Invalid credentials"
            throw ApiException(code = "AUTH_FAILED", message = msg, httpCode = res.code, raw = res.body)
        }
        val uid = LenientJson.findString(obj, "USER_ID", "user_id")
        if (res.code !in 200..299 || uid == null) {
            ApiLog.warn("pda-login: HTTP ${res.code}, USER_ID ${if (uid == null) "MISSING -> falling back to 1" else uid}. Body: ${res.body.take(300)}")
        }
        val name = LenientJson.findString(obj, "FULL_NAME", "full_name", "user_name") ?: user
        return LoginResult(userId = uid ?: "1", displayName = name)
    }

    override suspend fun workOrders(userId: String, query: String): List<WorkOrder> {
        // Not on server yet -> empty list; the 404 is visible in the API log.
        val url = ordsUrl("snym_erp/api/work-orders?USER_ID=${enc(userId)}&Q=${enc(query)}")
        return try {
            val res = http.get(url)
            if (res.code !in 200..299) return emptyList()
            val items = LenientJson.findArray(LenientJson.parseObject(res.body), "items", "data") ?: return emptyList()
            items.mapNotNull {
                val o = it as? JsonObject ?: return@mapNotNull null
                WorkOrder(
                    woNo = LenientJson.findString(o, "WO_NO", "wo_no").orEmpty(),
                    programNo = LenientJson.findString(o, "PROGRAM_NO", "program_no").orEmpty(),
                    unit = LenientJson.findString(o, "UNIT", "unit").orEmpty(),
                    location = LenientJson.findString(o, "LOCATION", "location").orEmpty(),
                    supplier = LenientJson.findString(o, "SUPPLIER", "supplier").orEmpty()
                )
            }
        } catch (e: ApiException) {
            ApiLog.warn("work-orders failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Turns an ORDS lookup reply into a clear error instead of empty data:
     * HTTP 404 = the service does not exist on the ERP yet; {"error":...} or a missing key field = not found.
     */
    private fun requireFound(res: RawResponse, obj: kotlinx.serialization.json.JsonObject, keyPresent: Boolean, what: String, code: String) {
        if (res.code == 404) {
            throw ApiException(code = "NOT_AVAILABLE", message = "$what is not available on the ERP yet", httpCode = 404)
        }
        val error = LenientJson.findString(obj, "error", "ERROR")
        val status = LenientJson.findString(obj, "status", "STATUS")
        val statusBad = status != null && !status.equals("SUCCESS", true) && !status.equals("S", true)
        if (res.code !in 200..299 || error != null || statusBad || !keyPresent) {
            val notFound = res.code in 200..299 && (error == null || error.contains("ORA-01403"))
            val msg = if (notFound) "$what not found: ${code.trim()}"
            else "$what failed (HTTP ${res.code}): ${error ?: LenientJson.findString(obj, "MESSAGE", "message") ?: status ?: "no reply"}"
            throw ApiException(code = if (notFound) "NOT_FOUND" else "LOOKUP_FAILED", message = msg, httpCode = res.code)
        }
    }

    override suspend fun lookupRequest(userId: String, refCode: String): IssueRequest {
        val url = ordsUrl("snym_erp/api/req-scan?USER_ID=${enc(userId)}&REF_CODE_NO=${enc(refCode)}")
        val res = http.postJson(url, "{}")
        if (res.code !in 200..299) OrdsReply.interpret("req-scan", res) // throws with a clear message
        val obj = LenientJson.parseObject(res.body)
        if (obj.isEmpty()) {
            // Plain-text answer such as "-1": let the interpreter turn it into an error.
            val r = OrdsReply.interpret("req-scan", res)
            throw ApiException(code = "BAD_RESPONSE", message = "req-scan did not return request details: ${r.message}", httpCode = res.code, raw = res.body)
        }
        val src = (LenientJson.findArray(obj, "items")?.firstOrNull() as? JsonObject) ?: obj
        // REF_ID must be the ERP's numeric id: sub-contract-issue fails on anything else.
        val refId = LenientJson.findString(src, "REF_ID", "ref_id")?.trim()
        if (refId.isNullOrBlank() || !refId.all { it.isDigit() }) {
            val err = LenientJson.findString(src, "error", "ERROR", "MESSAGE", "message")
            ApiLog.warn("req-scan: no numeric REF_ID for '$refCode'. Body: ${res.body.take(300)}")
            val notFound = err == null || err.contains("ORA-01403")
            throw ApiException(
                code = if (notFound) "NOT_FOUND" else "LOOKUP_FAILED",
                message = if (notFound) "Request not found on the ERP: ${refCode.trim()}" else "Request lookup failed: $err",
                httpCode = res.code, raw = res.body
            )
        }
        return IssueRequest(
            refId = refId,
            refCode = LenientJson.findString(src, "REF_CODE_NO", "ref_code_no") ?: refCode,
            supplier = LenientJson.findString(src, "SUPPLIER_NAME", "supplier") ?: "",
            program = LenientJson.findString(src, "PROGRAM_NO", "program") ?: "",
            workOrder = LenientJson.findString(src, "WO_NO", "work_order") ?: "",
            item = LenientJson.findString(src, "ITEM_NAME", "item") ?: "",
            color = LenientJson.findString(src, "COLOR", "color") ?: "",
            required = LenientJson.findInt(src, "TOTAL_TAG", "NO_OF_ITEMS", "required") ?: 0,
            issued = LenientJson.findInt(src, "ISSUED_TAG", "issued") ?: 0,
            balance = LenientJson.findInt(src, "BALANCE_TAG", "balance") ?: 0
        )
    }

    override suspend fun issueTags(
        userId: String,
        refId: String,
        tagCount: Int?,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        // Real path is snym_erp (xact_erp returns 404).
        val res = http.postJson(ordsUrl("snym_erp/api/sub-contract-issue"), itemsPayload(userId, tags, refId))
        return OrdsReply.interpret("sub-contract-issue", res, "Issued", tags)
    }

    override suspend fun programContext(userId: String, code: String): ProgramContext {
        // Not on server yet -> use the scanned code as program; the 404 is visible in the API log.
        val url = ordsUrl("snym_erp/api/program?USER_ID=${enc(userId)}&CODE=${enc(code)}")
        val res = http.get(url)
        // Service not built on the ERP yet: keep working with the code the operator chose.
        if (res.code == 404) return ProgramContext(programNo = code.trim(), woNo = "")
        val obj = LenientJson.parseObject(res.body)
        val programNo = LenientJson.findString(obj, "PROGRAM_NO", "program_no")
        requireFound(res, obj, !programNo.isNullOrBlank(), "Program", code)
        return ProgramContext(
            programNo = programNo!!,
            woNo = LenientJson.findString(obj, "WO_NO", "wo_no") ?: "",
            supplier = LenientJson.findString(obj, "SUPPLIER", "supplier") ?: "",
            supervisor = LenientJson.findString(obj, "SUPERVISOR", "supervisor") ?: ""
        )
    }

    override suspend fun saveWeights(
        userId: String,
        programNo: String,
        items: List<WeightItem>,
        txnId: String
    ): BulkResult {
        // One PUT per roll: ref-weight?USER_ID=&ITEM=&WEIGHT=
        val outcomes = mutableListOf<ItemOutcome>()
        var last: RawResponse? = null
        for (item in items) {
            val url = ordsUrl("snym_erp/api/ref-weight?USER_ID=${enc(userId)}&ITEM=${enc(item.item)}&WEIGHT=${enc(item.weight.toString())}")
            try {
                val res = http.putJson(url, "{}")
                last = res
                val r = OrdsReply.interpret("ref-weight", res, "Saved")
                outcomes += ItemOutcome(item.item, accepted = true, message = r.message)
            } catch (e: ApiException) {
                if (e.isNetwork && outcomes.isEmpty()) throw e // nothing reached the server: queue whole batch
                outcomes += ItemOutcome(item.item, accepted = false, code = e.code, message = e.message)
            }
        }
        val saved = outcomes.count { it.accepted }
        val firstErr = outcomes.firstOrNull { !it.accepted }?.message
        val msg = "$saved / ${items.size} weights saved" + (firstErr?.let { " - $it" } ?: "")
        if (saved == 0) throw ApiException(code = outcomes.firstOrNull()?.code ?: "REJECTED", message = msg, httpCode = last?.code ?: 0, raw = last?.body)
        return BulkResult(ok = true, message = msg, items = outcomes, httpCode = last?.code ?: 0, raw = last?.body.orEmpty())
    }

    override suspend fun load(
        userId: String,
        header: LoadHeader,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        // Backend parameters are USER_ID + ITEMS only; vehicle/challan stay on the device for now.
        val res = http.postJson(ordsUrl("snym_erp/api/load"), itemsPayload(userId, tags))
        val r = OrdsReply.interpret("load", res, "Loaded", tags)
        val ref = LenientJson.findString(LenientJson.parseObject(res.body), "LOADING_REF", "loading_ref").orEmpty()
        return r.copy(details = mapOf("LOADING_REF" to ref))
    }

    override suspend fun loadingExpected(userId: String, loadingRef: String): ExpectedList {
        // Not on server yet -> empty expected list; the 404 is visible in the API log.
        val url = ordsUrl("snym_erp/api/loading-expected?USER_ID=${enc(userId)}&LOADING_REF=${enc(loadingRef)}")
        val res = http.get(url)
        // Service not built on the ERP yet: open the trip without an expected list.
        if (res.code == 404) return ExpectedList(loadingRef)
        val obj = LenientJson.parseObject(res.body)
        val itemsOrNull = LenientJson.findArray(obj, "items")
        requireFound(res, obj, itemsOrNull != null, "Loading ref", loadingRef)
        return run {
            val items = itemsOrNull!!
            val tags = mutableListOf<String>()
            val rollNos = mutableMapOf<String, String>()
            for (el in items) {
                val o = el as? JsonObject ?: continue
                val tag = LenientJson.findString(o, "ITEM", "item") ?: continue
                tags.add(tag)
                rollNos[tag] = LenientJson.findString(o, "ROLL_NO", "roll_no") ?: ""
            }
            ExpectedList(loadingRef = loadingRef, tagIds = tags, rollNos = rollNos)
        }
    }

    override suspend fun unload(
        userId: String,
        loadingRef: String,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        val res = http.postJson(ordsUrl("snym_erp/api/unload"), itemsPayload(userId, tags))
        return OrdsReply.interpret("unload", res, "Unloaded", tags)
    }

    override suspend fun delivery(
        userId: String,
        storeCode: String,
        tubeTag: String,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        // Endpoint not on server yet: this will come back as a clear "does not exist (HTTP 404)" error.
        val payload = buildJsonObject {
            put("USER_ID", userId)
            put("STORE_CODE", storeCode)
            put("TUBE_TAG", tubeTag)
            putJsonArray("ITEMS") { tags.forEach { add(JsonPrimitive(it)) } }
        }.toString()
        val res = http.postJson(ordsUrl("snym_erp/api/store-delivery"), payload)
        return OrdsReply.interpret("store-delivery", res, "Delivered")
    }

    override suspend fun trace(userId: String, code: String): TraceResult {
        val res = http.get(ordsUrl("snym_erp/api/trace?USER_ID=${enc(userId)}&CODE=${enc(code)}"))
        if (res.code !in 200..299) OrdsReply.interpret("trace", res) // throws a clear error (404 today)
        val obj = LenientJson.parseObject(res.body)
        val item = LenientJson.findString(obj, "ITEM", "tag_id")
        val roll = LenientJson.findString(obj, "ROLL_NO", "roll_no")
        requireFound(res, obj, !item.isNullOrBlank() || !roll.isNullOrBlank(), "Roll", code)
        return TraceResult(
            tagId = item ?: code,
            rollNo = LenientJson.findString(obj, "ROLL_NO", "roll_no") ?: "",
            status = LenientJson.findString(obj, "STATUS_CODE", "status") ?: "UNKNOWN",
            weightKg = LenientJson.findDouble(obj, "WEIGHT", "weight"),
            qrToken = LenientJson.findString(obj, "QR_TOKEN", "qr_token"),
            program = LenientJson.findString(obj, "PROGRAM_NO", "program_no"),
            supplier = LenientJson.findString(obj, "SUPPLIER", "supplier")
        )
    }
}

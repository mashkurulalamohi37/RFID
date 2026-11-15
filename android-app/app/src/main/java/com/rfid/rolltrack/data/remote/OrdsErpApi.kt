package com.rfid.rolltrack.data.remote

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class OrdsErpApi(
    private val http: HttpTransport,
    private val getBaseUrl: () -> String,
    private val getFormat: () -> com.rfid.rolltrack.scanner.TagIdNormalizer.ErpFormat
) : ErpApi {

    private fun ordsUrl(path: String): String {
        val base = getBaseUrl().trimEnd('/')
        return "$base/$path"
    }

    private fun enc(value: String): String {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name())
    }

    override suspend fun login(user: String, pass: String): LoginResult {
        // Correctly URL-encode username and password (e.g. sumon##1031 -> sumon%23%231031)
        val url = ordsUrl("snym_erp/api/pda-login?USER_NAME=${enc(user)}&USER_PASSWORD=${enc(pass)}")
        val res = http.postJson(url, "{}")
        val obj = LenientJson.parseObject(res.body)
        val status = LenientJson.findString(obj, "STATUS", "status") ?: "S"
        if (status.equals("E", ignoreCase = true)) {
            val msg = LenientJson.findString(obj, "MESSAGE", "message") ?: "Invalid credentials"
            throw ApiException(code = "AUTH_FAILED", message = msg, httpCode = res.code)
        }
        val uid = LenientJson.findString(obj, "USER_ID", "user_id") ?: "1"
        val name = LenientJson.findString(obj, "FULL_NAME", "full_name", "user_name") ?: user
        return LoginResult(userId = uid, displayName = name)
    }

    override suspend fun workOrders(userId: String, query: String): List<WorkOrder> {
        val url = ordsUrl("snym_erp/api/work-orders?USER_ID=${enc(userId)}&Q=${enc(query)}")
        return try {
            val res = http.get(url)
            val obj = LenientJson.parseObject(res.body)
            val items = LenientJson.findArray(obj, "items", "data") ?: return emptyList()
            items.mapNotNull {
                val o = it as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                WorkOrder(
                    woNo = LenientJson.findString(o, "WO_NO", "wo_no").orEmpty(),
                    programNo = LenientJson.findString(o, "PROGRAM_NO", "program_no").orEmpty(),
                    unit = LenientJson.findString(o, "UNIT", "unit").orEmpty(),
                    location = LenientJson.findString(o, "LOCATION", "location").orEmpty(),
                    supplier = LenientJson.findString(o, "SUPPLIER", "supplier").orEmpty()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun lookupRequest(userId: String, refCode: String): IssueRequest {
        val url = ordsUrl("snym_erp/api/req-scan?USER_ID=${enc(userId)}&REF_CODE_NO=${enc(refCode)}")
        val res = http.postJson(url, "{}")
        val obj = LenientJson.parseObject(res.body)
        return IssueRequest(
            refId = LenientJson.findString(obj, "REF_ID", "ref_id") ?: refCode,
            refCode = LenientJson.findString(obj, "REF_CODE_NO", "ref_code_no") ?: refCode,
            supplier = LenientJson.findString(obj, "SUPPLIER_NAME", "supplier") ?: "",
            program = LenientJson.findString(obj, "PROGRAM_NO", "program") ?: "",
            workOrder = LenientJson.findString(obj, "WO_NO", "work_order") ?: "",
            item = LenientJson.findString(obj, "ITEM_NAME", "item") ?: "",
            color = LenientJson.findString(obj, "COLOR", "color") ?: "",
            required = LenientJson.findInt(obj, "TOTAL_TAG", "required") ?: 0,
            issued = LenientJson.findInt(obj, "ISSUED_TAG", "issued") ?: 0,
            balance = LenientJson.findInt(obj, "BALANCE_TAG", "balance") ?: 0
        )
    }

    override suspend fun issueTags(
        userId: String,
        refId: String,
        tagCount: Int?,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        val url = ordsUrl("xact_erp/api/sub-contract-issue")
        val payload = buildJsonObject {
            put("REF_ID", refId)
            put("USER_ID", userId)
            put("CLIENT_TXN_ID", txnId)
            if (tagCount != null) put("TAG_COUNT", tagCount)
            putJsonArray("ITEMS") {
                for (t in tags) add(kotlinx.serialization.json.JsonPrimitive(t))
            }
        }.toString()
        val res = http.postJson(url, payload)
        val obj = LenientJson.parseObject(res.body)
        val status = LenientJson.findString(obj, "STATUS", "status") ?: "S"
        val msg = LenientJson.findString(obj, "MESSAGE", "message") ?: "Submitted"
        return BulkResult(ok = !status.equals("E", ignoreCase = true), message = msg)
    }

    override suspend fun programContext(userId: String, code: String): ProgramContext {
        val url = ordsUrl("snym_erp/api/program?USER_ID=${enc(userId)}&CODE=${enc(code)}")
        return try {
            val res = http.get(url)
            val obj = LenientJson.parseObject(res.body)
            ProgramContext(
                programNo = LenientJson.findString(obj, "PROGRAM_NO", "program_no") ?: code,
                woNo = LenientJson.findString(obj, "WO_NO", "wo_no") ?: "",
                supplier = LenientJson.findString(obj, "SUPPLIER", "supplier") ?: "",
                supervisor = LenientJson.findString(obj, "SUPERVISOR", "supervisor") ?: ""
            )
        } catch (_: Exception) {
            ProgramContext(programNo = code, woNo = code)
        }
    }

    override suspend fun saveWeights(
        userId: String,
        programNo: String,
        items: List<WeightItem>,
        txnId: String
    ): BulkResult {
        // Fallback: loop per roll if bulk endpoint not available
        var saved = 0
        for (item in items) {
            val url = ordsUrl("snym_erp/api/ref-weight?USER_ID=${enc(userId)}&ITEM=${enc(item.item)}&WEIGHT=${enc(item.weight.toString())}")
            try {
                http.putJson(url, "{}")
                saved++
            } catch (_: Exception) {}
        }
        return BulkResult(ok = saved > 0, message = "$saved / ${items.size} weights saved")
    }

    override suspend fun load(
        userId: String,
        header: LoadHeader,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        val url = ordsUrl("snym_erp/api/load")
        val payload = buildJsonObject {
            put("USER_ID", userId)
            put("VEHICLE_NO", header.vehicleNo)
            put("CHALLAN_NO", header.challanRef)
            put("SUPPLIER", header.supplier)
            put("CLIENT_TXN_ID", txnId)
            putJsonArray("ITEMS") {
                for (t in tags) add(kotlinx.serialization.json.JsonPrimitive(t))
            }
        }.toString()
        val res = http.postJson(url, payload)
        val obj = LenientJson.parseObject(res.body)
        val status = LenientJson.findString(obj, "STATUS", "status") ?: "S"
        val msg = LenientJson.findString(obj, "MESSAGE", "message") ?: "Loaded"
        val ref = LenientJson.findString(obj, "LOADING_REF", "loading_ref") ?: ""
        return BulkResult(ok = !status.equals("E", ignoreCase = true), message = msg, details = mapOf("LOADING_REF" to ref))
    }

    override suspend fun loadingExpected(userId: String, loadingRef: String): ExpectedList {
        val url = ordsUrl("snym_erp/api/loading-expected?USER_ID=${enc(userId)}&LOADING_REF=${enc(loadingRef)}")
        return try {
            val res = http.get(url)
            val obj = LenientJson.parseObject(res.body)
            val items = LenientJson.findArray(obj, "items") ?: return ExpectedList(loadingRef)
            val tags = mutableListOf<String>()
            val rollNos = mutableMapOf<String, String>()
            for (el in items) {
                val o = el as? kotlinx.serialization.json.JsonObject ?: continue
                val tag = LenientJson.findString(o, "ITEM", "item") ?: continue
                val roll = LenientJson.findString(o, "ROLL_NO", "roll_no") ?: ""
                tags.add(tag)
                rollNos[tag] = roll
            }
            ExpectedList(loadingRef = loadingRef, tagIds = tags, rollNos = rollNos)
        } catch (_: Exception) {
            ExpectedList(loadingRef = loadingRef)
        }
    }

    override suspend fun unload(
        userId: String,
        loadingRef: String,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        val url = ordsUrl("snym_erp/api/unload")
        val payload = buildJsonObject {
            put("USER_ID", userId)
            put("LOADING_REF", loadingRef)
            put("CLIENT_TXN_ID", txnId)
            putJsonArray("ITEMS") {
                for (t in tags) add(kotlinx.serialization.json.JsonPrimitive(t))
            }
        }.toString()
        val res = http.postJson(url, payload)
        val obj = LenientJson.parseObject(res.body)
        val status = LenientJson.findString(obj, "STATUS", "status") ?: "S"
        val msg = LenientJson.findString(obj, "MESSAGE", "message") ?: "Unloaded"
        return BulkResult(ok = !status.equals("E", ignoreCase = true), message = msg)
    }

    override suspend fun delivery(
        userId: String,
        storeCode: String,
        tubeTag: String,
        tags: List<String>,
        txnId: String
    ): BulkResult {
        val url = ordsUrl("snym_erp/api/store-delivery")
        val payload = buildJsonObject {
            put("USER_ID", userId)
            put("STORE_CODE", storeCode)
            put("TUBE_TAG", tubeTag)
            put("CLIENT_TXN_ID", txnId)
            putJsonArray("ITEMS") {
                for (t in tags) add(kotlinx.serialization.json.JsonPrimitive(t))
            }
        }.toString()
        val res = http.postJson(url, payload)
        val obj = LenientJson.parseObject(res.body)
        val status = LenientJson.findString(obj, "STATUS", "status") ?: "S"
        val msg = LenientJson.findString(obj, "MESSAGE", "message") ?: "Delivered"
        return BulkResult(ok = !status.equals("E", ignoreCase = true), message = msg)
    }

    override suspend fun trace(userId: String, code: String): TraceResult {
        val url = ordsUrl("snym_erp/api/trace?USER_ID=${enc(userId)}&CODE=${enc(code)}")
        val res = http.get(url)
        val obj = LenientJson.parseObject(res.body)
        return TraceResult(
            tagId = LenientJson.findString(obj, "ITEM", "tag_id") ?: code,
            rollNo = LenientJson.findString(obj, "ROLL_NO", "roll_no") ?: "",
            status = LenientJson.findString(obj, "STATUS_CODE", "status") ?: "UNKNOWN",
            weightKg = LenientJson.findDouble(obj, "WEIGHT", "weight"),
            qrToken = LenientJson.findString(obj, "QR_TOKEN", "qr_token"),
            program = LenientJson.findString(obj, "PROGRAM_NO", "program_no"),
            supplier = LenientJson.findString(obj, "SUPPLIER", "supplier")
        )
    }
}

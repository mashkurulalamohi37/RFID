package com.rfid.rolltrack.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DemoErpApi : ErpApi {
    private val mutex = Mutex()

    private val sampleOrders = listOf(
        WorkOrder("WO-W261000014", "TEX-PRG-26100136", "BLUE PLANET KNIT COMPOSITE LTD.", "Barotopa, Rathura, Mawna, Sreepur, Gazipur", "Habib Knitting"),
        WorkOrder("WO-W261000012", "TEX-PRG-26100046", "BLUE PLANET KNIT COMPOSITE LTD.", "Barotopa, Rathura, Mawna, Sreepur, Gazipur", "Akij Textiles"),
        WorkOrder("WO-W261000011", "TEX-PRG-26100045", "BLUE PLANET KNIT COMPOSITE LTD.", "Barotopa, Rathura, Mawna, Sreepur, Gazipur", "Standard Fabrics")
    )

    private val requests = mutableMapOf(
        "BPKWL-13/IR25060017" to IssueRequest(
            refId = "REQ-101",
            refCode = "BPKWL-13/IR25060017",
            supplier = "Habib Knitting",
            program = "TEX-PRG-26100136",
            workOrder = "WO-W261000014",
            item = "100% Cotton Single Jersey",
            color = "Navy Blue",
            required = 50,
            issued = 20,
            balance = 30
        ),
        "BPKWL-13/IR25060018" to IssueRequest(
            refId = "REQ-102",
            refCode = "BPKWL-13/IR25060018",
            supplier = "Akij Textiles",
            program = "TEX-PRG-26100046",
            workOrder = "WO-W261000012",
            item = "CVC Fleece",
            color = "Melange Grey",
            required = 40,
            issued = 28,
            balance = 12
        )
    )

    private val loadings = mutableMapOf<String, ExpectedList>()
    private var loadSeq = 1

    override suspend fun login(user: String, pass: String): LoginResult = mutex.withLock {
        if (pass.isBlank()) {
            throw ApiException(code = "INVALID_CREDENTIALS", message = "Password cannot be empty")
        }
        LoginResult(userId = "1", displayName = user.ifBlank { "Operator" })
    }

    override suspend fun workOrders(userId: String, query: String): List<WorkOrder> = mutex.withLock {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withLock sampleOrders
        sampleOrders.filter { it.woNo.lowercase().contains(q) || it.programNo.lowercase().contains(q) || it.supplier.lowercase().contains(q) }
    }

    override suspend fun lookupRequest(userId: String, refCode: String): IssueRequest = mutex.withLock {
        val clean = refCode.trim()
        val found = requests[clean] ?: requests.values.firstOrNull {
            it.refCode.equals(clean, ignoreCase = true) || it.refId.equals(clean, ignoreCase = true)
        }
        if (found != null) return@withLock found

        // Dynamic fallback for any scanned RFID or code
        IssueRequest(
            refId = "REQ-" + clean.takeLast(4),
            refCode = clean,
            supplier = "Blue Planet Unit 2",
            program = "TEX-PRG-26100136",
            workOrder = "WO-W261000014",
            item = "Single Jersey Knit Fabric",
            color = "Black",
            required = 50,
            issued = 10,
            balance = 40
        )
    }

    override suspend fun issueTags(
        userId: String,
        refId: String,
        tagCount: Int?,
        tags: List<String>,
        txnId: String
    ): BulkResult = mutex.withLock {
        val count = tagCount ?: tags.size
        BulkResult(
            ok = true,
            message = "$count tags issued successfully",
            items = tags.map { ItemOutcome(it, accepted = true, message = "Issued") }
        )
    }

    override suspend fun programContext(userId: String, code: String): ProgramContext = mutex.withLock {
        val clean = code.trim()
        val wo = sampleOrders.firstOrNull {
            it.programNo.equals(clean, ignoreCase = true) || it.woNo.equals(clean, ignoreCase = true)
        } ?: sampleOrders.first()

        ProgramContext(
            programNo = wo.programNo,
            woNo = wo.woNo,
            supplier = wo.supplier,
            supervisor = "Md. Selim",
            eligibleTags = emptyList(),
            pendingFifo = listOf("1E70FD78", "2029875230", "2029967302")
        )
    }

    override suspend fun saveWeights(
        userId: String,
        programNo: String,
        items: List<WeightItem>,
        txnId: String
    ): BulkResult = mutex.withLock {
        BulkResult(
            ok = true,
            message = "${items.size} rolls weighed and saved",
            items = items.mapIndexed { idx, it ->
                ItemOutcome(it.item, accepted = true, rollNo = "RL-%05d".format(1000 + idx))
            }
        )
    }

    override suspend fun load(
        userId: String,
        header: LoadHeader,
        tags: List<String>,
        txnId: String
    ): BulkResult = mutex.withLock {
        val loadRef = "LOAD-%05d".format(loadSeq++)
        loadings[loadRef] = ExpectedList(
            loadingRef = loadRef,
            tagIds = tags,
            rollNos = tags.associateWith { "RL-%s".format(it.takeLast(4)) }
        )
        BulkResult(
            ok = true,
            message = "${tags.size} rolls loaded on vehicle ${header.vehicleNo}",
            items = tags.map { ItemOutcome(it, accepted = true, message = "Loaded") },
            details = mapOf("LOADING_REF" to loadRef)
        )
    }

    override suspend fun loadingExpected(userId: String, loadingRef: String): ExpectedList = mutex.withLock {
        val clean = loadingRef.trim().uppercase()
        loadings[clean] ?: ExpectedList(
            loadingRef = clean,
            tagIds = listOf("1E70FD78", "2029875230", "2029967302", "2029967390"),
            rollNos = mapOf(
                "1E70FD78" to "RL-1001",
                "2029875230" to "RL-1002",
                "2029967302" to "RL-1003",
                "2029967390" to "RL-1004"
            )
        )
    }

    override suspend fun unload(
        userId: String,
        loadingRef: String,
        tags: List<String>,
        txnId: String
    ): BulkResult = mutex.withLock {
        val expected = loadingExpected(userId, loadingRef)
        val expectedSet = expected.tagIds.toSet()
        val scannedSet = tags.toSet()

        val matched = scannedSet.intersect(expectedSet)
        val extra = scannedSet - expectedSet
        val missing = expectedSet - scannedSet

        val items = tags.map { tag ->
            if (tag in expectedSet) {
                ItemOutcome(tag, accepted = true, rollNo = expected.rollNos[tag] ?: "RL-UNKNOWN", message = "Matched")
            } else {
                ItemOutcome(tag, accepted = true, message = "Extra roll (not in manifest)")
            }
        }

        BulkResult(
            ok = true,
            message = "Unloaded: ${matched.size} matched, ${missing.size} missing, ${extra.size} extra",
            items = items,
            table = ResultTable(
                title = "Gate Register - $loadingRef",
                rows = listOf(
                    listOf("Supplier", "Blue Planet Knit Composite"),
                    listOf("Matched Rolls", "${matched.size}"),
                    listOf("Missing Rolls", "${missing.size}"),
                    listOf("Extra Rolls", "${extra.size}")
                )
            )
        )
    }

    override suspend fun delivery(
        userId: String,
        storeCode: String,
        tubeTag: String,
        tags: List<String>,
        txnId: String
    ): BulkResult = mutex.withLock {
        BulkResult(
            ok = true,
            message = "${tags.size} rolls packed into tube $tubeTag for store $storeCode",
            items = tags.map { ItemOutcome(it, accepted = true, message = "Delivered to $storeCode") }
        )
    }

    override suspend fun trace(userId: String, code: String): TraceResult = mutex.withLock {
        val clean = code.trim()
        TraceResult(
            tagId = clean,
            rollNo = "RL-260900001",
            status = "GATE_RECEIVED",
            weightKg = 24.5,
            qrToken = "BSR:92837465",
            program = "TEX-PRG-26100136",
            supplier = "Habib Knitting",
            events = listOf(
                TraceEvent("2026-10-01 10:00", "Tag Issued", "BPKWL-13/IR25060017"),
                TraceEvent("2026-10-02 14:30", "Production Weighed", "24.5 kg"),
                TraceEvent("2026-10-03 09:15", "Loaded", "Vehicle DHA-11-2034"),
                TraceEvent("2026-10-03 16:45", "Gate Received", "Unloading Completed")
            )
        )
    }
}

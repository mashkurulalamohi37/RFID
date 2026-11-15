package com.rfid.rolltrack.data.remote

interface ErpApi {
    suspend fun login(user: String, pass: String): LoginResult
    suspend fun workOrders(userId: String, query: String): List<WorkOrder>
    suspend fun lookupRequest(userId: String, refCode: String): IssueRequest
    suspend fun issueTags(userId: String, refId: String, tagCount: Int?, tags: List<String>, txnId: String): BulkResult
    suspend fun programContext(userId: String, code: String): ProgramContext
    suspend fun saveWeights(userId: String, programNo: String, items: List<WeightItem>, txnId: String): BulkResult
    suspend fun load(userId: String, header: LoadHeader, tags: List<String>, txnId: String): BulkResult
    suspend fun loadingExpected(userId: String, loadingRef: String): ExpectedList
    suspend fun unload(userId: String, loadingRef: String, tags: List<String>, txnId: String): BulkResult
    suspend fun delivery(userId: String, storeCode: String, tubeTag: String, tags: List<String>, txnId: String): BulkResult
    suspend fun trace(userId: String, code: String): TraceResult
}

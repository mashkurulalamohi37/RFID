package com.rfid.rolltrack.data.remote

import com.rfid.rolltrack.config.AppSettings
import kotlinx.coroutines.flow.StateFlow

class SwitchingErpApi(
    private val ordsApi: OrdsErpApi,
    private val demoApi: DemoErpApi,
    private val currentSettings: () -> AppSettings
) : ErpApi {

    private fun target(): ErpApi {
        val s = currentSettings()
        if (s.simulateOffline) {
            throw ApiException(code = "OFFLINE_SIMULATED", message = "Offline mode simulated in Settings")
        }
        return if (s.demoMode) demoApi else ordsApi
    }

    override suspend fun login(user: String, pass: String): LoginResult = target().login(user, pass)
    override suspend fun workOrders(userId: String, query: String): List<WorkOrder> = target().workOrders(userId, query)
    override suspend fun lookupRequest(userId: String, refCode: String): IssueRequest = target().lookupRequest(userId, refCode)
    override suspend fun issueTags(userId: String, refId: String, tagCount: Int?, tags: List<String>, txnId: String): BulkResult =
        target().issueTags(userId, refId, tagCount, tags, txnId)
    override suspend fun programContext(userId: String, code: String): ProgramContext = target().programContext(userId, code)
    override suspend fun saveWeights(userId: String, programNo: String, items: List<WeightItem>, txnId: String): BulkResult =
        target().saveWeights(userId, programNo, items, txnId)
    override suspend fun load(userId: String, header: LoadHeader, tags: List<String>, txnId: String): BulkResult =
        target().load(userId, header, tags, txnId)
    override suspend fun loadingExpected(userId: String, loadingRef: String): ExpectedList = target().loadingExpected(userId, loadingRef)
    override suspend fun unload(userId: String, loadingRef: String, tags: List<String>, txnId: String): BulkResult =
        target().unload(userId, loadingRef, tags, txnId)
    override suspend fun delivery(userId: String, storeCode: String, tubeTag: String, tags: List<String>, txnId: String): BulkResult =
        target().delivery(userId, storeCode, tubeTag, tags, txnId)
    override suspend fun trace(userId: String, code: String): TraceResult = target().trace(userId, code)
}

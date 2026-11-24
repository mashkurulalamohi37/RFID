package com.rfid.rolltrack.data

import android.content.Context
import com.rfid.rolltrack.data.remote.ApiLog

/**
 * Remembers, on this device, how many tags were issued to each requisition (by ERP REF_ID).
 *
 * Why: the ERP's req-scan always returns the same NO_OF_ITEMS and never an issued count, so without this
 * the balance would reset after every submit and a requisition with balance 10 could take unlimited tags.
 * Only batches the server accepted are recorded. The count is per device (another PDA keeps its own).
 */
class IssuedLedger(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("issued_ledger", Context.MODE_PRIVATE)

    @Synchronized
    fun issued(refId: String): Int = prefs.getInt(countKey(refId), 0)

    @Synchronized
    fun issuedTags(refId: String): Set<String> = prefs.getStringSet(tagsKey(refId), emptySet()).orEmpty()

    /** Add an accepted batch: [count] tags, of which [tags] are the tag ids (empty for count-only). */
    @Synchronized
    fun record(refId: String, count: Int, tags: Collection<String>) {
        if (refId.isBlank() || count <= 0) return
        val newCount = issued(refId) + count
        val newTags = issuedTags(refId) + tags
        prefs.edit().putInt(countKey(refId), newCount).putStringSet(tagsKey(refId), newTags).apply()
        ApiLog.info("Tag Issue ledger: REF_ID $refId +$count -> $newCount issued on this device")
    }

    private fun countKey(refId: String) = "count_${refId.trim()}"
    private fun tagsKey(refId: String) = "tags_${refId.trim()}"
}

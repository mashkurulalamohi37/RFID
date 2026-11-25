package com.rfid.rolltrack.data

import android.content.Context
import com.rfid.rolltrack.data.remote.ApiLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One roll weight the server accepted. */
data class WeighedTag(val tagId: String, val weightKg: Double, val at: Long)

/**
 * Remembers, on this device, which roll tags got a weight (Record weight screen) and what it was.
 * Cleared for a tag when it is unloaded, so the tag starts fresh for the next lot.
 */
class WeighedLedger(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("weighed_ledger", Context.MODE_PRIVATE)

    private val _all = MutableStateFlow(readAll())
    /** Newest first. */
    val all: StateFlow<List<WeighedTag>> = _all.asStateFlow()

    fun get(tagId: String): WeighedTag? = _all.value.firstOrNull { it.tagId == tagId }

    @Synchronized
    fun record(tagId: String, weightKg: Double) {
        if (tagId.isBlank()) return
        val now = System.currentTimeMillis()
        prefs.edit().putString(KEY_PREFIX + tagId, "$weightKg|$now").apply()
        _all.value = (listOf(WeighedTag(tagId, weightKg, now)) + _all.value.filter { it.tagId != tagId })
        ApiLog.info("Record weight: $tagId = $weightKg kg saved on this device")
    }

    /** Removes the given tags' weights (after Unloading). Returns how many were removed. */
    @Synchronized
    fun forget(tags: Collection<String>): Int {
        val present = tags.filter { prefs.contains(KEY_PREFIX + it) }
        if (present.isEmpty()) return 0
        val edit = prefs.edit()
        present.forEach { edit.remove(KEY_PREFIX + it) }
        edit.apply()
        _all.value = _all.value.filter { it.tagId !in present }
        return present.size
    }

    private fun readAll(): List<WeighedTag> = prefs.all.mapNotNull { (k, v) ->
        if (!k.startsWith(KEY_PREFIX) || v !is String) return@mapNotNull null
        val parts = v.split('|')
        val w = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
        WeighedTag(k.removePrefix(KEY_PREFIX), w, parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }.sortedByDescending { it.at }

    private companion object { const val KEY_PREFIX = "w_" }
}

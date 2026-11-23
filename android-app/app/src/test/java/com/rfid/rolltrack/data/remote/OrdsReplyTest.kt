package com.rfid.rolltrack.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OrdsReplyTest {

    private val a = "E2806A960000402D1EB05D2F"
    private val b = "E2806A960000502D1EB05952"
    private val c = "E2806A960000602D1EB01111"

    private fun ok(body: String) = RawResponse(200, body)

    @Test
    fun allAccepted_noItems() {
        val r = OrdsReply.interpret("load", ok("""{"P_RESPONSE":"2 loaded, 0 rejected"}"""), "Loaded", listOf(a, b))
        assertTrue(r.ok)
        assertFalse(r.uncertain)
        assertTrue(r.items.isEmpty())
        assertEquals("2 loaded, 0 rejected", r.message)
    }

    @Test
    fun partial_marksOnlyNamedTagsRejected() {
        val body = """{"P_RESPONSE":"1 loaded, 2 rejected - ${b.lowercase()} already loaded; $c not registered as a roll"}"""
        val r = OrdsReply.interpret("load", ok(body), "Loaded", listOf(a, b, c))
        assertTrue(r.ok)
        assertFalse(r.uncertain)
        assertEquals(listOf(b, c), r.items.map { it.tagId })   // keys are the tags exactly as sent
        assertTrue(r.items.none { it.accepted })
        assertEquals("already loaded", r.items[0].message)
        assertEquals("not registered as a roll", r.items[1].message)
    }

    @Test
    fun reasonContainingSemicolon_staysWithItsTag() {
        val body = "1 unloaded, 1 rejected - $b not unloaded: Roll not loaded; contact store"
        val r = OrdsReply.interpret("unload", ok(body), "Unloaded", listOf(a, b))
        assertEquals(1, r.items.size)
        assertEquals("not unloaded: Roll not loaded; contact store", r.items[0].message)
    }

    @Test
    fun duplicatesOnly_isPlainSuccess() {
        val r = OrdsReply.interpret("load", ok("2 loaded, 0 rejected, 1 duplicate(s) ignored"), "Loaded", listOf(a, b))
        assertTrue(r.items.isEmpty())
        assertFalse(r.uncertain)
    }

    @Test
    fun rejectedButNotNamed_isUncertain() {
        val r = OrdsReply.interpret("load", ok("1 loaded, 2 rejected - $b already loaded"), "Loaded", listOf(a, b, c))
        assertTrue(r.uncertain)
        assertEquals(listOf(b), r.items.map { it.tagId })
        assertTrue(r.message.contains("1 rejected tag(s) not named"))
    }

    @Test
    fun noneSaved_throws() {
        try {
            OrdsReply.interpret("load", ok("0 loaded, 1 rejected - $a already loaded"), "Loaded", listOf(a))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("NONE_SAVED", e.code)
        }
    }

    @Test
    fun dbError_throws() {
        try {
            OrdsReply.interpret("load", ok("""{"P_RESPONSE":"-9: ORA-00942: table or view does not exist"}"""), "Loaded", listOf(a))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("PROC_ERROR", e.code)
        }
    }

    @Test
    fun errorKey_throws() {
        try {
            OrdsReply.interpret("load", ok("""{ "error":"ORA-01403: no data found" }"""), "Loaded", listOf(a))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("SERVER_ERROR", e.code)
            assertTrue(e.message!!.contains("ORA-01403"))
        }
    }

    @Test
    fun liveServerShapes_fromPostman() {
        // Saved example responses in PDA.postman_collection.json
        val load = OrdsReply.interpret("load", ok("""{ "status":"SUCCESS" ,"message":"2 item(s)" }"""), "Loaded", listOf(a, b))
        assertEquals("2 item(s)", load.message)
        assertFalse(load.uncertain)
        try {
            OrdsReply.interpret("unload", ok("""{ "status":"ERROR" ,"message":"User ID not found" }"""), "Unloaded", listOf(a))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("REJECTED", e.code)
        }
    }

    @Test
    fun oldReplyFormat_unchanged() {
        val r = OrdsReply.interpret("load", ok("""{"P_RESPONSE":"2 item(s)"}"""), "Loaded", listOf(a, b))
        assertTrue(r.ok)
        assertTrue(r.items.isEmpty())
        assertEquals("2 item(s)", r.message)
    }

    @Test
    fun parseBulk_otherText_isNull() {
        assertNull(OrdsReply.parseBulk("Accepted", listOf(a)))
    }
}

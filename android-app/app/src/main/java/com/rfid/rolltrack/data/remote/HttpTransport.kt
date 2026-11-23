package com.rfid.rolltrack.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class HttpTransport(
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val pingClient: OkHttpClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String): RawResponse = withContext(Dispatchers.IO) {
        execute(buildOrThrow(url) { Request.Builder().url(it).get().build() }, null)
    }

    suspend fun postJson(url: String, json: String): RawResponse = withContext(Dispatchers.IO) {
        execute(buildOrThrow(url) { Request.Builder().url(it).post(json.toRequestBody(jsonMedia)).build() }, json)
    }

    suspend fun putJson(url: String, json: String): RawResponse = withContext(Dispatchers.IO) {
        execute(buildOrThrow(url) { Request.Builder().url(it).put(json.toRequestBody(jsonMedia)).build() }, json)
    }

    /** A malformed server URL in Settings (e.g. half-typed "…:7007ord") must show an error, not crash the app. */
    private inline fun buildOrThrow(url: String, build: (String) -> Request): Request = try {
        build(url)
    } catch (e: IllegalArgumentException) {
        ApiLog.warn("invalid URL: $url (${e.message})")
        throw ApiException(code = "BAD_URL", message = "Invalid server URL in Settings: ${e.message}", cause = e)
    }

    /**
     * Reachability check: any HTTP answer (even 404/405) means the ORDS server is up.
     * Not recorded in ApiLog so the background health check does not flood the log.
     * Returns round-trip ms, or throws ApiException(NETWORK_ERROR).
     */
    suspend fun ping(url: String): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val request = buildOrThrow(url) { Request.Builder().url(it).get().build() }
        try {
            pingClient.newCall(request).execute().use { }
            System.currentTimeMillis() - start
        } catch (e: IOException) {
            throw ApiException(code = "NETWORK_ERROR", message = describe(e), cause = e)
        }
    }

    private fun execute(request: Request, body: String?): RawResponse {
        val id = ApiLog.newId()
        val start = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val ms = System.currentTimeMillis() - start
                ApiLog.record(ApiCall(id, start, request.method, request.url.toString(), body, response.code, bodyString, ms, null))
                return RawResponse(response.code, bodyString, id)
            }
        } catch (e: IOException) {
            val ms = System.currentTimeMillis() - start
            val msg = describe(e)
            ApiLog.record(ApiCall(id, start, request.method, request.url.toString(), body, null, null, ms, msg))
            throw ApiException(code = "NETWORK_ERROR", message = msg, cause = e)
        }
    }

    private fun describe(e: IOException): String = when (e) {
        is UnknownHostException -> "No internet / DNS failed (${e.message})"
        is ConnectException -> "Cannot connect to ERP server (${e.message})"
        is SocketTimeoutException -> "ERP server timed out (${e.message})"
        else -> e.message ?: e.javaClass.simpleName
    }
}

data class RawResponse(
    val code: Int,
    val body: String,
    val callId: Long = 0
)

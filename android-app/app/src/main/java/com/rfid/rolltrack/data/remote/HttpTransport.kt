package com.rfid.rolltrack.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpTransport(
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun get(url: String): RawResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        execute(request)
    }

    suspend fun postJson(url: String, json: String): RawResponse = withContext(Dispatchers.IO) {
        val body = json.toRequestBody(jsonMedia)
        val request = Request.Builder().url(url).post(body).build()
        execute(request)
    }

    suspend fun putJson(url: String, json: String): RawResponse = withContext(Dispatchers.IO) {
        val body = json.toRequestBody(jsonMedia)
        val request = Request.Builder().url(url).put(body).build()
        execute(request)
    }

    private fun execute(request: Request): RawResponse {
        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                return RawResponse(response.code, bodyString)
            }
        } catch (e: IOException) {
            throw ApiException(code = "NETWORK_ERROR", message = e.message ?: "Network error", cause = e)
        }
    }
}

data class RawResponse(
    val code: Int,
    val body: String
)

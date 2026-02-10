package com.talentonet.securityall.locationforegroundservice

import com.getcapacitor.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HttpLocationTransmitter : LocationTransmitter {

    // HTTP is stateless, so it's always "connected" in the sense that it can try to send.
    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override fun initialize(config: TrackingOptions) {
        // HTTP client is stateless/lazy initialized, no specific setup needed here
    }

    override suspend fun send(payload: LocationFGService.LocationPayload, config: TrackingOptions): Boolean {
        val json = payload.toJson(config.metadata)
        val request = Request.Builder()
            .url(config.endpoint)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                config.headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                Logger.info(TAG, "Respuesta HTTP > ${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Fallo en envío HTTP: ${e.message}", e)
            false
        }
    }

    override suspend fun shutdown() {
        // OkHttpClient handles its own connection pool cleanup automatically
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun LocationFGService.LocationPayload.toJson(metadata: Map<String, String>): String {
        val location = JSONObject()
        location.put("lat", latitude)
        location.put("lng", longitude)
        location.put("accuracy", accuracy)
        location.put("altitude", altitude)
        location.put("speed", speed)
        location.put("bearing", bearing)

        val json = JSONObject()
        json.put("location", location)
        json.put("timestamp", timestamp)

        if (metadata.isNotEmpty()) {
            val extras = JSONObject()
            metadata.forEach { (key, value) -> extras.put(key, value) }
            json.put("metadata", extras)
        }
        return json.toString()
    }

    companion object {
        private const val TAG = "HttpLocationTransmitter"
        private const val JSON_MEDIA = "application/json; charset=utf-8"
        private val JSON_MEDIA_TYPE = JSON_MEDIA.toMediaType()
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val WRITE_TIMEOUT_SECONDS = 20L
    }
}

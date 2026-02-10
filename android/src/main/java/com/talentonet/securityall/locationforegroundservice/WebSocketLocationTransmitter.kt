package com.talentonet.securityall.locationforegroundservice

import com.getcapacitor.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketLocationTransmitter(private val scope: CoroutineScope) : LocationTransmitter {

    private var socket: WebSocket? = null
    private var config: TrackingOptions? = null
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Keep alive
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    private var retryCount = 0
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var reconnectJob: Job? = null
    private var isDisposing = false
    private val connectionMutex = Mutex()

    override fun initialize(config: TrackingOptions) {
        this.config = config
        this.isDisposing = false
        scope.launch {
            Logger.info(TAG, "Inicializando transmisor WebSocket")
            connect()
        }
    }

    private suspend fun connect() = connectionMutex.withLock {
        Logger.info(TAG, "Iniciando conexión WebSocket isDisposing=${isDisposing}")
        if (isDisposing) return

        // Cerrar socket anterior para evitar fugas
        socket?.close(1000, "Reconectando")
        socket = null

        val conf = config ?: return
        val request = Request.Builder()
            .url(conf.endpoint)
            .apply {
                conf.headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()

        Logger.info(TAG, "Conectando WebSocket a ${conf.endpoint}")
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Logger.info(TAG, "WebSocket conectado")
                _isConnected.value = true
                retryCount = 0
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logger.info(TAG, "WebSocket error: ${t.message}")
                _isConnected.value = false
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Logger.info(TAG, "WebSocket cerrado: $reason")
                _isConnected.value = false
                if (!isDisposing) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        Logger.info(TAG, "Programando reconexión WebSocket, isDisposing=${isDisposing} _conected=${_isConnected.value}")
        if (_isConnected.value || isDisposing) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = calculateRetryDelay()
            Logger.info(TAG, "Reconectando en ${delayMs}ms (Intento ${retryCount + 1})")
            delay(delayMs)
            retryCount++
            connect()
        }
    }

    private fun calculateRetryDelay(): Long {
        return when {
            retryCount < RETRY_IMMEDIATE_MAX_N -> 0L // Inmediato
            retryCount < RETRY_IMMEDIATE_MAX_N + RETRY_SHORT_PHASE_MAX -> RETRY_DELAY_SHORT_X
            else -> RETRY_DELAY_LONG_Y
        }
    }

    override suspend fun send(payload: LocationFGService.LocationPayload, config: TrackingOptions): Boolean {

        Logger.info(TAG, "Enviando ubicación vía WebSocket connected=${_isConnected.value}  payload=$payload")
        if (!_isConnected.value) {
            return false
        }

        val json = payload.toJson(config.metadata)
        return socket?.send(json) == true
    }

    override suspend fun shutdown() = connectionMutex.withLock {
        Logger.info(TAG, "Apagando transmisor WebSocket")
        isDisposing = true
        reconnectJob?.cancel()
        try {
            socket?.close(1000, "Servicio detenido")
        } catch (e: Exception) {
            // Ignorar
        }
        socket = null
        _isConnected.value = false
        httpClient.dispatcher.executorService.shutdown()
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
        private const val TAG = "WebSocketLocationTransmitter"
        
        // Constantes configurables de reconexión
        private const val RETRY_IMMEDIATE_MAX_N = 5 // N intentos inmediatos
        private const val RETRY_SHORT_PHASE_MAX = 10 // Cantidad de intentos con espera X antes de pasar a Y
        private const val RETRY_DELAY_SHORT_X = 10000L // X: 15 segundos
        private const val RETRY_DELAY_LONG_Y = 15000L // Y: 10 minutos
    }
}

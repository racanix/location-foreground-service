package com.talentonet.securityall.locationforegroundservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.getcapacitor.Logger
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.ArrayDeque
import java.util.HashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Servicio en primer plano responsable de obtener ubicaciones y enviarlas con reintentos.
 */
class LocationFGService : Service() {

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val pendingPayloads: ArrayDeque<LocationPayload> = ArrayDeque()
    private val queueLock = Any()
    private var locationCallback: LocationCallback? = null
    private var currentConfig: TrackingOptions? = null
    private var flushJob: Job? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val arrivalTriggered = AtomicBoolean(false)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(DEFAULT_NOTIFICATION_BODY, DEFAULT_NOTIFICATION_TITLE))
        running.set(true)
        Logger.info(TAG, "Servicio creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStartIntent(intent)
            ACTION_STOP -> stopSelf()
            else -> Logger.info(TAG, "Comando ignorado: ${intent?.action}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        scope.cancel()
        flushJob?.cancel()
        synchronized(queueLock) { pendingPayloads.clear() }
        running.set(false)
        Logger.info(TAG, "Servicio destruido")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStartIntent(intent: Intent) {
        val endpoint = intent.getStringExtra(EXTRA_ENDPOINT)
        if (endpoint.isNullOrBlank()) {
            Logger.error(TAG, "No se recibió endpoint válido, deteniendo servicio", null)
            stopSelf()
            return
        }

        arrivalTriggered.set(false)

        val headers = readSerializableMap(intent, EXTRA_HEADERS)
        val metadata = readSerializableMap(intent, EXTRA_METADATA)
        val minInterval = intent.getLongExtra(EXTRA_MIN_INTERVAL, DEFAULT_MIN_INTERVAL)
        val fastestInterval = intent.getLongExtra(EXTRA_FASTEST_INTERVAL, DEFAULT_FASTEST_INTERVAL)
        val minDistance = intent.getFloatExtra(EXTRA_MIN_DISTANCE, DEFAULT_MIN_DISTANCE)
        val notificationTitle = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE) ?: DEFAULT_NOTIFICATION_TITLE
        val notificationBody = intent.getStringExtra(EXTRA_NOTIFICATION_BODY) ?: DEFAULT_NOTIFICATION_BODY
        val retryDelay = intent.getLongExtra(EXTRA_RETRY_DELAY, DEFAULT_RETRY_DELAY)
        val queueCapacity = max(intent.getIntExtra(EXTRA_QUEUE_CAPACITY, DEFAULT_QUEUE_CAPACITY), 1)
        val accuracy = intent.getStringExtra(EXTRA_ACCURACY)?.let { runCatching { LocationAccuracy.valueOf(it) }.getOrNull() } ?: LocationAccuracy.HIGH
        val targetLat = intent.getDoubleExtra(EXTRA_TARGET_LAT, Double.NaN)
        val targetLng = intent.getDoubleExtra(EXTRA_TARGET_LNG, Double.NaN)
        val targetRange = intent.getDoubleExtra(EXTRA_TARGET_RANGE, DEFAULT_TARGET_RANGE)
        val targetLocation = if (!targetLat.isNaN() && !targetLng.isNaN()) {
            TargetLocation(targetLat, targetLng, targetRange)
        } else {
            null
        }

        val config = TrackingOptions(
            endpoint = endpoint,
            headers = headers,
            metadata = metadata,
            minUpdateIntervalMillis = minInterval,
            fastestIntervalMillis = fastestInterval,
            minUpdateDistanceMeters = minDistance,
            notificationTitle = notificationTitle,
            notificationBody = notificationBody,
            retryDelayMillis = retryDelay,
            queueCapacity = queueCapacity,
            accuracy = accuracy,
            targetLocation = targetLocation,
        )

        currentConfig = config
        updateNotification(notificationBody, notificationTitle)
        restartLocationUpdates(config)
        flushQueue()
        Logger.info(TAG, "Servicio iniciado")
    }

    private fun restartLocationUpdates(config: TrackingOptions) {
        stopLocationUpdates()
        val priority = when (config.accuracy) {
            LocationAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
            LocationAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val request = LocationRequest.Builder(priority, config.minUpdateIntervalMillis)
            .setMinUpdateIntervalMillis(config.fastestIntervalMillis)
            .setMinUpdateDistanceMeters(config.minUpdateDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    val target = currentConfig?.targetLocation
                    if (target != null && hasReachedTarget(location, target)) {
                        handleArrival()
                        return
                    }
                    val payload = LocationPayload(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = location.altitude,
                        speed = location.speed,
                        bearing = location.bearing,
                        timestamp = System.currentTimeMillis(),
                    )
                    enqueuePayload(payload)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        locationCallback = callback
    }

    private fun hasReachedTarget(current: Location, target: TargetLocation): Boolean {
        val distance = FloatArray(1)
        Location.distanceBetween(
            current.latitude,
            current.longitude,
            target.latitude,
            target.longitude,
            distance,
        )
        return distance.first().toDouble() <= target.rangeMeters
    }

    private fun handleArrival() {
        if (!arrivalTriggered.compareAndSet(false, true)) {
            return
        }
        showArrivalAlert()
        stopSelf()
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun enqueuePayload(payload: LocationPayload) {
        val config = currentConfig ?: return
        synchronized(queueLock) {
            if (pendingPayloads.size >= config.queueCapacity) {
                pendingPayloads.removeFirst()
            }
            pendingPayloads.addLast(payload)
        }
        flushQueue()
    }

    private fun flushQueue() {
        if (flushJob?.isActive == true) {
            return
        }
        flushJob = scope.launch {
            while (isActive) {
                val config = currentConfig ?: break
                val next = synchronized(queueLock) {
                    if (pendingPayloads.isEmpty()) {
                        return@launch
                    }
                    pendingPayloads.first()
                }
                try {
                    sendPayload(next, config)
                    synchronized(queueLock) { pendingPayloads.removeFirst() }
                    val remaining = synchronized(queueLock) { pendingPayloads.size }
                    updateNotification("Enviando ubicaciones (${remaining} pendientes)", config.notificationTitle)
                } catch (error: Throwable) {
                    Logger.error(TAG, "Fallo al enviar ubicación mensaje: ${error.message} causa: ${error.localizedMessage}", error)
                    delay(config.retryDelayMillis)
                }
            }
        }
    }

    private fun sendPayload(payload: LocationPayload, config: TrackingOptions) {
        val json = payload.toJson(config.metadata)
        val request = Request.Builder()
            .url(config.endpoint)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                config.headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()

        httpClient.newCall(request).execute().use { response ->
            Logger.info(TAG, "Informacion de respuesta >  ${response.body?.string()} ${response}")
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
        }
    }

    private fun LocationPayload.toJson(metadata: Map<String, String>): String {
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            ALERT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    private fun buildNotification(content: String, title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(content: String, title: String? = null) {
        val resolvedTitle = title ?: currentConfig?.notificationTitle ?: DEFAULT_NOTIFICATION_TITLE
        val notification = buildNotification(content, resolvedTitle)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showArrivalAlert() {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Destino alcanzado")
            .setContentText("Haz llegado a tu destino")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(ARRIVAL_NOTIFICATION_ID, notification)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSerializableMap(intent: Intent, key: String): HashMap<String, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(key, HashMap::class.java) as? HashMap<String, String> ?: hashMapOf()
        } else {
            @Suppress("DEPRECATION")
            val legacy = intent.getSerializableExtra(key) as? HashMap<String, String>
            legacy ?: hashMapOf()
        }
    }

    data class LocationPayload(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val altitude: Double,
        val speed: Float,
        val bearing: Float,
        val timestamp: Long,
    )

    companion object {
        private const val TAG = "LocationFGService"
        private const val CHANNEL_ID = "securityall_location_channel"
        private const val CHANNEL_NAME = "Ubicación en segundo plano"
        private const val ALERT_CHANNEL_ID = "securityall_location_alerts"
        private const val ALERT_CHANNEL_NAME = "Alertas de ubicación"
        private const val NOTIFICATION_ID = 90421
        private const val ARRIVAL_NOTIFICATION_ID = 90422
        private const val DEFAULT_NOTIFICATION_TITLE = "Ubicación activa"
        private const val DEFAULT_NOTIFICATION_BODY = "Compartiendo tu ubicación"
        private const val JSON_MEDIA = "application/json; charset=utf-8"
        private val JSON_MEDIA_TYPE = JSON_MEDIA.toMediaType()
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val WRITE_TIMEOUT_SECONDS = 20L
        private val running = AtomicBoolean(false)

        const val ACTION_START = "com.talentonet.securityall.locationforegroundservice.START"
        const val ACTION_STOP = "com.talentonet.securityall.locationforegroundservice.STOP"
        const val EXTRA_ENDPOINT = "extra_endpoint"
        const val EXTRA_HEADERS = "extra_headers"
        const val EXTRA_METADATA = "extra_metadata"
        const val EXTRA_MIN_INTERVAL = "extra_min_interval"
        const val EXTRA_FASTEST_INTERVAL = "extra_fastest_interval"
        const val EXTRA_MIN_DISTANCE = "extra_min_distance"
        const val EXTRA_NOTIFICATION_TITLE = "extra_notification_title"
        const val EXTRA_NOTIFICATION_BODY = "extra_notification_body"
        const val EXTRA_RETRY_DELAY = "extra_retry_delay"
        const val EXTRA_QUEUE_CAPACITY = "extra_queue_capacity"
        const val EXTRA_ACCURACY = "extra_accuracy"
        const val EXTRA_TARGET_LAT = "extra_target_lat"
        const val EXTRA_TARGET_LNG = "extra_target_lng"
        const val EXTRA_TARGET_RANGE = "extra_target_range"

        const val DEFAULT_MIN_INTERVAL = 10_000L
        const val DEFAULT_FASTEST_INTERVAL = 5_000L
        const val DEFAULT_MIN_DISTANCE = 5f
        const val DEFAULT_RETRY_DELAY = 5_000L
        const val DEFAULT_QUEUE_CAPACITY = 32
        const val DEFAULT_TARGET_RANGE = 10.0

        fun isRunning(): Boolean = running.get()
    }
}

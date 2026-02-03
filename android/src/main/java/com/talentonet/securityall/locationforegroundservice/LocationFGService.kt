package com.talentonet.securityall.locationforegroundservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.talentonet.securityall.locationforegroundservice.LocationServiceConstants as Constants

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
        startForeground(NOTIFICATION_ID, buildNotification(Constants.DEFAULT_NOTIFICATION_BODY, Constants.DEFAULT_NOTIFICATION_TITLE))
        running.set(true)
        Logger.info(TAG, "Servicio creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START -> handleStartIntent(intent)
            Constants.ACTION_STOP -> stopSelf()
            Constants.ACTION_CONFIRM_ARRIVAL -> handleConfirmArrival()
            Constants.ACTION_REJECT_ARRIVAL -> handleRejectArrival()
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
        val endpoint = intent.getStringExtra(Constants.EXTRA_ENDPOINT)
        if (endpoint.isNullOrBlank()) {
            Logger.error(TAG, "No se recibió endpoint válido, deteniendo servicio", null)
            stopSelf()
            return
        }
        val alertTerminationEndpoint = intent.getStringExtra(Constants.EXTRA_ALERT_TERMINATION_ENDPOINT)

        arrivalTriggered.set(false)

        val headers = readSerializableMap(intent, Constants.EXTRA_HEADERS)
        val metadata = readSerializableMap(intent, Constants.EXTRA_METADATA)
        val minInterval = intent.getLongExtra(Constants.EXTRA_MIN_INTERVAL, Constants.DEFAULT_MIN_INTERVAL)
        val fastestInterval = intent.getLongExtra(Constants.EXTRA_FASTEST_INTERVAL, Constants.DEFAULT_FASTEST_INTERVAL)
        val minDistance = intent.getFloatExtra(Constants.EXTRA_MIN_DISTANCE, Constants.DEFAULT_MIN_DISTANCE)
        val notificationTitle = intent.getStringExtra(Constants.EXTRA_NOTIFICATION_TITLE) ?: Constants.DEFAULT_NOTIFICATION_TITLE
        val notificationBody = intent.getStringExtra(Constants.EXTRA_NOTIFICATION_BODY) ?: Constants.DEFAULT_NOTIFICATION_BODY
        val retryDelay = intent.getLongExtra(Constants.EXTRA_RETRY_DELAY, Constants.DEFAULT_RETRY_DELAY)
        val queueCapacity = max(intent.getIntExtra(Constants.EXTRA_QUEUE_CAPACITY, Constants.DEFAULT_QUEUE_CAPACITY), 1)
        val accuracy = intent.getStringExtra(Constants.EXTRA_ACCURACY)?.let { runCatching { LocationAccuracy.valueOf(it) }.getOrNull() } ?: LocationAccuracy.HIGH
        val targetLat = intent.getDoubleExtra(Constants.EXTRA_TARGET_LAT, Double.NaN)
        val targetLng = intent.getDoubleExtra(Constants.EXTRA_TARGET_LNG, Double.NaN)
        val targetRange = intent.getDoubleExtra(Constants.EXTRA_TARGET_RANGE, Constants.DEFAULT_TARGET_RANGE)
        val targetLocation = if (!targetLat.isNaN() && !targetLng.isNaN()) {
            TargetLocation(targetLat, targetLng, targetRange)
        } else {
            null
        }

        val config = TrackingOptions(
            endpoint = endpoint,
            alertTerminationEndpoint = alertTerminationEndpoint,
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
                    if (target != null) {
                        if (hasReachedTarget(location, target)) {
                            handleArrival()
                        } else {
                            // Si el usuario sale del radio, reseteamos para volver a notificar al reingresar
                            arrivalTriggered.set(false)
                        }
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
        val resolvedTitle = title ?: currentConfig?.notificationTitle ?: Constants.DEFAULT_NOTIFICATION_TITLE
        val notification = buildNotification(content, resolvedTitle)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showArrivalAlert() {
        // PendingIntent para "Sí" (Confirmar llegada)
        val confirmIntent = Intent(this, LocationFGService::class.java).apply {
            action = Constants.ACTION_CONFIRM_ARRIVAL
        }
        val confirmPendingIntent = PendingIntent.getService(
            this,
            0,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent para "No" (Rechazar llegada / continuar)
        val rejectIntent = Intent(this, LocationFGService::class.java).apply {
            action = Constants.ACTION_REJECT_ARRIVAL
        }
        val rejectPendingIntent = PendingIntent.getService(
            this,
            1,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Destino alcanzado")
            .setContentText("¿Has llegado a tu destino? Confirma para detener el seguimiento.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(0, "Sí", confirmPendingIntent)
            .addAction(0, "No", rejectPendingIntent)
            .build()
        notificationManager.notify(ARRIVAL_NOTIFICATION_ID, notification)
    }

    private fun handleRejectArrival() {
        notificationManager.cancel(ARRIVAL_NOTIFICATION_ID)
    }

    private fun handleConfirmArrival() {
        notificationManager.cancel(ARRIVAL_NOTIFICATION_ID)
        val config = currentConfig ?: return
        val terminationUrl = config.alertTerminationEndpoint
        
        if (terminationUrl.isNullOrBlank()) {
            Logger.info(TAG, "Confirmación de llegada sin endpoint de finalización.")
            // El servicio solo se detien si ya no existen  alarmas.
            // stopSelf()
            return
        }

        scope.launch {
            try {
                confirmJourneyCompletion(terminationUrl, config)
                Logger.info(TAG, "Journey completado exitosamente.")
            } catch (e: Exception) {
                Logger.error(TAG, "Error completando journey: ${e.message}", e)
            } finally {
                // El servicio solo se detien si ya no existen  alarmas.
                // stopSelf()
            }
        }
    }

    private suspend fun confirmJourneyCompletion(url: String, config: TrackingOptions) {
        val emptyJson = "{}" 
        val request = Request.Builder()
            .url(url)
            .post(emptyJson.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                config.headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()
            
        httpClient.newCall(request).execute().use { response ->
            // checar si is successful ver cuantas aletas hay, si hay 0 detener el servicio.
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} - ${response.message}")
            }
        }
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
        private const val JSON_MEDIA = "application/json; charset=utf-8"
        private val JSON_MEDIA_TYPE = JSON_MEDIA.toMediaType()
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val WRITE_TIMEOUT_SECONDS = 20L
        private val running = AtomicBoolean(false)

        fun isRunning(): Boolean = running.get()
    }
}

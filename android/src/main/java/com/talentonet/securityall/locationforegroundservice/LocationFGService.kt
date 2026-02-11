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
import kotlin.text.replace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
// import kotlinx.coroutines.invokeOnCompletion
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
    private var transmitter: LocationTransmitter? = null
    private lateinit var alertManager: AlertManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                Constants.DEFAULT_NOTIFICATION_BODY,
                Constants.DEFAULT_NOTIFICATION_TITLE
            )
        )
        running.set(true)
        alertManager = AlertManager(this)
        Logger.info(TAG, "Servicio creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Puede ocurrir cuando el sistema relanza el servicio (START_STICKY)
            // y no hay un Intent explícito asociado.
            Logger.info(TAG, "onStartCommand recibido con intent null")
            return START_STICKY
        }

        when (intent.action) {
            Constants.ACTION_START -> handleStartIntent(intent)
            Constants.ACTION_STOP -> stopSelf()
            Constants.ACTION_CONFIRM_ARRIVAL -> handleConfirmArrival(intent.getStringExtra(Constants.EXTRA_CONFIRM_ARRIVAL_ALERT_ID))
            Constants.ACTION_REJECT_ARRIVAL -> handleRejectArrival()
            else -> Logger.info(TAG, "Comando ignorado: ${intent.action}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        scope.launch {
            transmitter?.shutdown()
        }
        scope.cancel()
        flushJob?.cancel()
        synchronized(queueLock) { pendingPayloads.clear() }
        running.set(false)
        Logger.info(TAG, "Servicio destruido")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStartIntent(intent: Intent) {

        if (isAlreadyRunning()) {
            Logger.info(TAG, "Servicio ya estaba en ejecución con la misma configuración. Ignorando ACTION_START.")
            return
        }

        val endpoint = intent.getStringExtra(Constants.EXTRA_ENDPOINT)
        if (endpoint.isNullOrBlank()) {
            Logger.error(TAG, "No se recibió endpoint válido, deteniendo servicio", null)
            stopSelf()
            return
        }
        val alertTerminationEndpoint = intent.getStringExtra(Constants.EXTRA_ALERT_TERMINATION_ENDPOINT)

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
        )

        currentConfig = config

        scope.launch {
            transmitter?.shutdown()
            transmitter = WebSocketLocationTransmitter(scope)
            transmitter?.initialize(config)

            updateNotification(notificationBody, notificationTitle)
            startLocationUpdates(config)
            flushQueue()
            Logger.info(TAG, "Servicio iniciado")
        }
    }

    private fun isAlreadyRunning(): Boolean {
        return running.get() && transmitter?.isConnected?.value == true
    }

    private fun startLocationUpdates(config: TrackingOptions) {
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
                    Logger.info(TAG, "Nueva ubicación recibida: $location")
                    val alerts = alertManager.getAll()

                    if (alerts.isEmpty()) {
                        Logger.info(TAG, "No hay alertas activas, deteniendo servicio.")
                        stopSelf()
                        return@forEach
                    }

                    if (alertManager.existAlertType(AlertType.JOURNEY)) {
                        val journeyAlert = alerts.filter { it.type == AlertType.JOURNEY && it.targetLocation != null }.firstOrNull()
                        if (journeyAlert != null) {
                            val target = journeyAlert.targetLocation!!
                            if (hasReachedTarget(location, target)) {
                                Logger.info(TAG, "Usuario llegó al radio objetivo para alerta JOURNEY ${journeyAlert.id}")
                                handleArrival(journeyAlert.id)
                            } else {
                                Logger.info(TAG, "Usuario salió del radio objetivo, reseteando alerta de llegada")
                                // Si el usuario sale del radio, reseteamos para volver a notificar al reingresar
                                arrivalTriggered.set(false)
                            }
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
                    Logger.info(TAG, "Encolando nueva ubicación: $payload")
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

    private fun handleArrival(alertId: String) {
        if (!arrivalTriggered.compareAndSet(false, true)) {
            return
        }
        showArrivalAlert(alertId)
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
            val transmitter = this@LocationFGService.transmitter ?: return@launch

            // Observar el estado de la conexión para pausar/reanudar el envío
            transmitter.isConnected.collect { isConnected ->
                if (!isConnected) {
                    Logger.info(TAG, "Transmisor desconectado, pausando envíos.")
                    return@collect
                }

                Logger.info(TAG, "Transmisor conectado, procesando cola...")
                while (isActive && transmitter.isConnected.value) {
                    val config = currentConfig ?: break
                    val payload = synchronized(queueLock) { pendingPayloads.peekFirst() }
                    if (payload == null) {
                        Logger.info(TAG, "Cola vacía, esperando nuevas ubicaciones.")
                        delay(5000L)
                        continue
                    }

                    val success = transmitter.send(payload, config)
                    if (success) {
                        synchronized(queueLock) { pendingPayloads.removeFirst() }
                        val remaining = synchronized(queueLock) { pendingPayloads.size }
                        updateNotification("Enviando ubicaciones (${remaining} pendientes)", config.notificationTitle)
                        Logger.info(TAG, "Ubicación enviada exitosamente, ${remaining} pendientes en cola.")
                    } else {
                        Logger.info(TAG, "Fallo al enviar ubicación, reintentando en ${config.retryDelayMillis}ms")
                        delay(config.retryDelayMillis)
                    }
                }
            }
        }
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

    private fun showArrivalAlert(alertId: String) {
        // PendingIntent para "Sí" (Confirmar llegada)
        val confirmIntent = Intent(this, LocationFGService::class.java).apply {
            action = Constants.ACTION_CONFIRM_ARRIVAL
            putExtra(Constants.EXTRA_CONFIRM_ARRIVAL_ALERT_ID, alertId)
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

    private fun handleConfirmArrival(alertId: String?) {
        notificationManager.cancel(ARRIVAL_NOTIFICATION_ID)
        val config = currentConfig ?: return
        val terminationUrl = config.alertTerminationEndpoint
        
        if (terminationUrl.isNullOrBlank()) {
            Logger.info(TAG, "Confirmación de llegada sin endpoint de finalización.")
            // El servicio solo se detien si ya no existen  alarmas.
            // stopSelf()
            return
        }

        if (alertId.isNullOrBlank()) {
            Logger.info(TAG, "Confirmación de llegada sin ID de alerta, no se puede completar el journey.")
            return
        }

        scope.launch {
            try {
                confirmJourneyCompletion(terminationUrl.replace("#param#", alertId), config)
                Logger.info(TAG, "Journey completado exitosamente.")
            } catch (e: Exception) {
                Logger.error(TAG, "Error completando journey: ${e.message}", e)
            } finally {
                alertManager.remove(alertId)
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
            
        OkHttpClient().newCall(request).execute().use { response ->
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

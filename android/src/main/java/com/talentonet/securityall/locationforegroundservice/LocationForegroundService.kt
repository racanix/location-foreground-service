package com.talentonet.securityall.locationforegroundservice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.HashMap
import com.talentonet.securityall.locationforegroundservice.LocationServiceConstants as Constants

/**
 * Fachada utilizada por el plugin para iniciar o detener el servicio nativo.
 */
class LocationForegroundService(context: Context) {

    private val appContext = context.applicationContext

    fun startTracking(options: TrackingOptions) {
        val intent = Intent(appContext, LocationFGService::class.java).apply {
            action = Constants.ACTION_START
            putExtra(Constants.EXTRA_ENDPOINT, options.endpoint)
            putExtra(Constants.EXTRA_ALERT_TERMINATION_ENDPOINT, options.alertTerminationEndpoint)
            putExtra(Constants.EXTRA_MIN_INTERVAL, options.minUpdateIntervalMillis)
            putExtra(Constants.EXTRA_FASTEST_INTERVAL, options.fastestIntervalMillis)
            putExtra(Constants.EXTRA_MIN_DISTANCE, options.minUpdateDistanceMeters)
            putExtra(Constants.EXTRA_NOTIFICATION_TITLE, options.notificationTitle)
            putExtra(Constants.EXTRA_NOTIFICATION_BODY, options.notificationBody)
            putExtra(Constants.EXTRA_RETRY_DELAY, options.retryDelayMillis)
            putExtra(Constants.EXTRA_QUEUE_CAPACITY, options.queueCapacity)
            putExtra(Constants.EXTRA_ACCURACY, options.accuracy.name)
            putExtra(Constants.EXTRA_HEADERS, HashMap(options.headers))
            putExtra(Constants.EXTRA_METADATA, HashMap(options.metadata))
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopTracking() {
        appContext.stopService(Intent(appContext, LocationFGService::class.java))
    }

    fun isTracking(): Boolean = LocationFGService.isRunning()
}

data class TrackingOptions(
    val endpoint: String,
    val alertTerminationEndpoint: String?,
    val headers: Map<String, String>,
    val metadata: Map<String, String>,
    val minUpdateIntervalMillis: Long,
    val fastestIntervalMillis: Long,
    val minUpdateDistanceMeters: Float,
    val notificationTitle: String,
    val notificationBody: String,
    val retryDelayMillis: Long,
    val queueCapacity: Int,
    val accuracy: LocationAccuracy,
)

data class Alert(
    val id: String,
    val type: AlertType,
    val targetLocation: TargetLocation?,
)

enum class AlertType {
    JOURNEY,
    QUICK,
    REQUEST_LOCATION,
    DEFAULT
}

data class TargetLocation(
    val latitude: Double,
    val longitude: Double,
    val rangeMeters: Double,
)

enum class LocationAccuracy {
    HIGH,
    BALANCED,
}

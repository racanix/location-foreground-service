package com.talentonet.securityall.locationforegroundservice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.util.HashMap

/**
 * Fachada utilizada por el plugin para iniciar o detener el servicio nativo.
 */
class LocationForegroundService(context: Context) {

    private val appContext = context.applicationContext

    fun startTracking(options: TrackingOptions) {
        val intent = Intent(appContext, LocationFGService::class.java).apply {
            action = LocationFGService.ACTION_START
            putExtra(LocationFGService.EXTRA_ENDPOINT, options.endpoint)
            putExtra(LocationFGService.EXTRA_MIN_INTERVAL, options.minUpdateIntervalMillis)
            putExtra(LocationFGService.EXTRA_FASTEST_INTERVAL, options.fastestIntervalMillis)
            putExtra(LocationFGService.EXTRA_MIN_DISTANCE, options.minUpdateDistanceMeters)
            putExtra(LocationFGService.EXTRA_NOTIFICATION_TITLE, options.notificationTitle)
            putExtra(LocationFGService.EXTRA_NOTIFICATION_BODY, options.notificationBody)
            putExtra(LocationFGService.EXTRA_RETRY_DELAY, options.retryDelayMillis)
            putExtra(LocationFGService.EXTRA_QUEUE_CAPACITY, options.queueCapacity)
            putExtra(LocationFGService.EXTRA_ACCURACY, options.accuracy.name)
            putExtra(LocationFGService.EXTRA_HEADERS, HashMap(options.headers))
            putExtra(LocationFGService.EXTRA_METADATA, HashMap(options.metadata))
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

enum class LocationAccuracy {
    HIGH,
    BALANCED,
}

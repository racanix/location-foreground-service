package com.talentonet.securityall.locationforegroundservice

import android.Manifest
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission

@CapacitorPlugin(
    name = "LocationForegroundService",
    permissions = [
        Permission(strings = [Manifest.permission.ACCESS_FINE_LOCATION], alias = LocationForegroundServicePlugin.PERMISSION_FINE),
        Permission(strings = [Manifest.permission.ACCESS_COARSE_LOCATION], alias = LocationForegroundServicePlugin.PERMISSION_COARSE),
        Permission(strings = [Manifest.permission.ACCESS_BACKGROUND_LOCATION], alias = LocationForegroundServicePlugin.PERMISSION_BACKGROUND),
    ],
)
class LocationForegroundServicePlugin : Plugin() {

    private lateinit var implementation: LocationForegroundService

    override fun load() {
        super.load()
        implementation = LocationForegroundService(context.applicationContext)
    }

    @PluginMethod
    fun startTracking(call: PluginCall) {
        val options = runCatching { call.toTrackingOptions() }
            .getOrElse {
                call.reject(it.message ?: "Parámetros inválidos")
                return
            }

        // La app principal es responsable de solicitar permisos de ubicación
        // mediante GeolocationContext y su propio flujo de UI.
        // Aquí asumimos que los permisos ya están concedidos.
        startWithOptions(call, options)
    }

    @PluginMethod
    fun stopTracking(call: PluginCall) {
        implementation.stopTracking()
        val ret = JSObject().apply { put("running", false) }
        call.resolve(ret)
    }

    @PluginMethod
    fun isTracking(call: PluginCall) {
        val ret = JSObject().apply { put("running", implementation.isTracking()) }
        call.resolve(ret)
    }

    private fun startWithOptions(call: PluginCall, options: TrackingOptions) {
        implementation.startTracking(options)
        val ret = JSObject().apply { put("running", true) }
        call.resolve(ret)
    }

    private fun PluginCall.toTrackingOptions(): TrackingOptions {
        val endpoint = getString("endpoint")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Debes proporcionar un endpoint válido")
        val headers = getObject("headers")?.toMap() ?: emptyMap()
        val metadata = getObject("metadata")?.toMap() ?: emptyMap()
        val targetLocation = readTargetLocation()
        val minInterval = getDouble("minUpdateIntervalMillis")?.toLong() ?: LocationFGService.DEFAULT_MIN_INTERVAL
        val fastestInterval = getDouble("fastestIntervalMillis")?.toLong() ?: LocationFGService.DEFAULT_FASTEST_INTERVAL
        val minDistance = getDouble("minUpdateDistanceMeters")?.toFloat() ?: LocationFGService.DEFAULT_MIN_DISTANCE
        val notificationTitle = getString("notificationTitle") ?: DEFAULT_NOTIFICATION_TITLE
        val notificationBody = getString("notificationBody") ?: DEFAULT_NOTIFICATION_BODY
        val retryDelay = getDouble("retryDelayMillis")?.toLong() ?: LocationFGService.DEFAULT_RETRY_DELAY
        val queueCapacity = maxOf(getInt("queueCapacity") ?: LocationFGService.DEFAULT_QUEUE_CAPACITY, 1)
        val accuracy = when (getString("accuracy")?.lowercase()) {
            "balanced" -> LocationAccuracy.BALANCED
            else -> LocationAccuracy.HIGH
        }

        return TrackingOptions(
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
    }

    private fun PluginCall.readTargetLocation(): TargetLocation? {
        val target = getObject("targetLocation") ?: return null
        if (!target.has("latitude") || !target.has("longitude")) {
            throw IllegalArgumentException("Debes proporcionar latitude y longitude en targetLocation")
        }

        val latitude = target.getDouble("latitude")
        val longitude = target.getDouble("longitude")
        val providedRange = if (target.has("range")) target.getDouble("range") else LocationFGService.DEFAULT_TARGET_RANGE
        val range = if (providedRange > 0) providedRange else LocationFGService.DEFAULT_TARGET_RANGE

        return TargetLocation(
            latitude = latitude,
            longitude = longitude,
            rangeMeters = range,
        )
    }

    private fun JSObject.toMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (isNull(key)) {
                continue
            }
            val value = optString(key)
            result[key] = value
        }
        return result
    }

    companion object {
        internal const val PERMISSION_FINE = "fineLocation"
        internal const val PERMISSION_COARSE = "coarseLocation"
        internal const val PERMISSION_BACKGROUND = "backgroundLocation"
        private const val DEFAULT_NOTIFICATION_TITLE = "Ubicación activa"
        private const val DEFAULT_NOTIFICATION_BODY = "Compartiendo tu posición"
    }
}

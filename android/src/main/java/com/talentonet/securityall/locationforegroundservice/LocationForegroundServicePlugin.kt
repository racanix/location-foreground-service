package com.talentonet.securityall.locationforegroundservice

import android.Manifest
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.talentonet.securityall.locationforegroundservice.LocationServiceConstants as Constants

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
    private lateinit var alertManager: AlertManager

    override fun load() {
        super.load()
        alertManager = AlertManager(context.applicationContext)
        implementation = LocationForegroundService(context.applicationContext)
    }

    @PluginMethod
    fun addAlert(call: PluginCall) {
        try {
            val id = call.getString("id")
            if (id.isNullOrBlank()) {
                call.reject("El id de la alerta es obligatorio")
                return
            }

            val typeStr = call.getString("type") ?: "DEFAULT"
            val type = try {
                AlertType.valueOf(typeStr)
            } catch (e: Exception) {
                AlertType.DEFAULT
            }

            val targetObj = call.getObject("targetLocation")
            val target = if (targetObj != null) parseTargetLocation(targetObj) else null

            val alert = Alert(id, type, target)
            val success = alertManager.add(alert)
            val ret = JSObject().apply { put("success", success) }
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Error al agregar alerta: ${e.message}")
        }
    }

    @PluginMethod
    fun removeAlert(call: PluginCall) {
        val id = call.getString("id")
        if (id.isNullOrBlank()) {
            call.reject("El id es obligatorio")
            return
        }
        val success = alertManager.remove(id)
        val ret = JSObject().apply { put("success", success) }
        call.resolve(ret)
    }

    @PluginMethod
    fun existsAlert(call: PluginCall) {
        val id = call.getString("id")
        if (id.isNullOrBlank()) {
            call.reject("El id es obligatorio")
            return
        }
        val alert = alertManager.exists(id)
        if (alert == null) {
            val ret = JSObject().apply { put("alert", null) }
            call.resolve(ret)
        } else {
            val ret = JSObject().apply {
                val jsonAlert = JSObject()
                jsonAlert.put("id", alert.id)
                jsonAlert.put("type", alert.type.name)
                alert.targetLocation?.let { t ->
                    val tJson = JSObject()
                    tJson.put("latitude", t.latitude)
                    tJson.put("longitude", t.longitude)
                    tJson.put("range", t.rangeMeters)
                    jsonAlert.put("targetLocation", tJson)
                }
                put("alert", jsonAlert)
            }
            call.resolve(ret)
        }
    }

    @PluginMethod
    fun getAllAlerts(call: PluginCall) {
        val alerts = alertManager.getAll()
        val ret = JSObject()
        val alertsArray = alerts.map { alert ->
            val jsonAlert = JSObject()
            jsonAlert.put("id", alert.id)
            jsonAlert.put("type", alert.type.name)
            alert.targetLocation?.let { t ->
                val tJson = JSObject()
                tJson.put("latitude", t.latitude)
                tJson.put("longitude", t.longitude)
                tJson.put("range", t.rangeMeters)
                jsonAlert.put("targetLocation", tJson)
            }
            jsonAlert
        }
        ret.put("alerts", alertsArray)
        call.resolve(ret)
    }

    @PluginMethod
    fun existAlertType(call: PluginCall) {
        val typeStr = call.getString("type")
        if (typeStr.isNullOrBlank()) {
            call.reject("El tipo es obligatorio")
            return
        }
        val type = try {
            AlertType.valueOf(typeStr)
        } catch (e: Exception) {
            call.reject("Tipo inválido")
            return
        }
        val exists = alertManager.existAlertType(type)
        val ret = JSObject().apply { put("exists", exists) }
        call.resolve(ret)
    }

    @PluginMethod
    fun getAlertCount(call: PluginCall) {
        val count = alertManager.count()
        val ret = JSObject().apply { put("count", count) }
        call.resolve(ret)
    }

    @PluginMethod
    fun clearAllAlerts(call: PluginCall) {
        val success = alertManager.clearAll()
        val ret = JSObject().apply { put("success", success) }
        call.resolve(ret)
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
        val alertTerminationEndpoint = getString("alertTerminationEndpoint")
        val headers = getObject("headers")?.toMap() ?: emptyMap()
        val metadata = getObject("metadata")?.toMap() ?: emptyMap()
        val minInterval = getDouble("minUpdateIntervalMillis")?.toLong() ?: Constants.DEFAULT_MIN_INTERVAL
        val fastestInterval = getDouble("fastestIntervalMillis")?.toLong() ?: Constants.DEFAULT_FASTEST_INTERVAL
        val minDistance = getDouble("minUpdateDistanceMeters")?.toFloat() ?: Constants.DEFAULT_MIN_DISTANCE
        val notificationTitle = getString("notificationTitle") ?: DEFAULT_NOTIFICATION_TITLE
        val notificationBody = getString("notificationBody") ?: DEFAULT_NOTIFICATION_BODY
        val retryDelay = getDouble("retryDelayMillis")?.toLong() ?: Constants.DEFAULT_RETRY_DELAY
        val queueCapacity = maxOf(getInt("queueCapacity") ?: Constants.DEFAULT_QUEUE_CAPACITY, 1)
        val accuracy = when (getString("accuracy")?.lowercase()) {
            "balanced" -> LocationAccuracy.BALANCED
            else -> LocationAccuracy.HIGH
        }

        return TrackingOptions(
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
    }

    private fun parseTargetLocation(target: JSObject): TargetLocation {
        val latitude = target.getDouble("latitude")
        val longitude = target.getDouble("longitude")
        val providedRange = if (target.has("range")) target.getDouble("range") else Constants.DEFAULT_TARGET_RANGE
        val range = if (providedRange > 0) providedRange else Constants.DEFAULT_TARGET_RANGE

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

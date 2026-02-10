package com.talentonet.securityall.locationforegroundservice

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.getcapacitor.Logger

/**
 * Gestor centralizado para la persistencia y administración de alertas.
 * Permite que tanto el Plugin (JS) como el Servicio (Background) accedan a la misma fuente de verdad.
 */
class AlertManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AlertManager"
        private const val PREFS_NAME = "LocationForegroundServiceAlerts"
        private const val KEY_ALERTS = "alerts"
    }

    fun count(): Int {
        val alerts = getAlertsJson()
        return alerts.length()
    }

    /**
     * Agrega una alerta si no existe otra con el mismo ID.
     * Retorna true si se agregó exitosamente, false si ya existía.
     */
    @Synchronized
    fun add(alert: Alert): Boolean {
        if (exists(alert.id) != null) return false

        val alerts = getAlertsJson()
        alerts.put(alert.toJson())
        saveAlertsJson(alerts)
        Logger.info(TAG, "Alerta agregada, $alerts")
        return true
    }

    /**
     * Remueve una alerta por su ID.
     * Retorna true si se removió exitosamente, false si no existía.
     */
    @Synchronized
    fun remove(id: String): Boolean {
        val alerts = getAlertsJson()
        for (i in 0 until alerts.length()) {
            val item = alerts.getJSONObject(i)
            if (item.optString("id") == id) {
                val newAlerts = JSONArray()
                for (j in 0 until alerts.length()) {
                    if (j != i) {
                        newAlerts.put(alerts.getJSONObject(j))
                    }
                }
                saveAlertsJson(newAlerts)
                Logger.info(TAG, "Alerta removida, $newAlerts")
                return true
            }
        }
        return false
    }

    /**
     * Remueve todas las alertas.
     * Retorna true si se removieron exitosamente.
     */
    @Synchronized
    fun clearAll(): Boolean {
        saveAlertsJson(JSONArray())
        Logger.info(TAG, "Todas las alertas removidas")
        return true
    }

    /**
     * Retorna la alerta si existe, o null.
     */
    fun exists(id: String): Alert? {
        val alerts = getAlertsJson()
        for (i in 0 until alerts.length()) {
            val item = alerts.getJSONObject(i)
            if (item.optString("id") == id) {
                return item.toAlert()
            }
        }
        return null
    }

    /**
     * Obtiene todas las alertas activas.
     */
    fun getAll(): List<Alert> {
        val list = mutableListOf<Alert>()
        val alerts = getAlertsJson()
        for (i in 0 until alerts.length()) {
            list.add(alerts.getJSONObject(i).toAlert())
        }
        Logger.info(TAG, "Obteniendo todas las alertas, $alerts")
        return list
    }

    fun existAlertType(type: AlertType): Boolean {
        val alerts = getAlertsJson()
        for (i in 0 until alerts.length()) {
            val item = alerts.getJSONObject(i)
            val typeStr = item.optString("type")
            if (typeStr == type.name) {
                return true
            }
        }
        return false
    }
    

    private fun getAlertsJson(): JSONArray {
        val jsonString = prefs.getString(KEY_ALERTS, "[]")
        return try {
            JSONArray(jsonString)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun saveAlertsJson(jsonArray: JSONArray) {
        prefs.edit().putString(KEY_ALERTS, jsonArray.toString()).apply()
    }

    private fun Alert.toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("type", type.name)
        targetLocation?.let {
            val loc = JSONObject()
            loc.put("latitude", it.latitude)
            loc.put("longitude", it.longitude)
            loc.put("rangeMeters", it.rangeMeters)
            json.put("targetLocation", loc)
        }
        return json
    }

    private fun JSONObject.toAlert(): Alert {
        val id = getString("id")
        val typeStr = getString("type")
        val type = try {
            AlertType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            AlertType.DEFAULT
        }
        
        var target: TargetLocation? = null
        if (has("targetLocation")) {
            val loc = getJSONObject("targetLocation")
            target = TargetLocation(
                latitude = loc.getDouble("latitude"),
                longitude = loc.getDouble("longitude"),
                rangeMeters = loc.getDouble("rangeMeters")
            )
        }
        return Alert(id, type, target)
    }
}

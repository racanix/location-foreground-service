package com.talentonet.securityall.locationforegroundservice

/**
 * Contrato público para interactuar con el servicio de ubicación mediante Intents desacoplados.
 * Define las acciones y las claves de los extras aceptados por el servicio.
 */
object LocationServiceConstants {
    // Actions
    const val ACTION_START = "com.talentonet.securityall.locationforegroundservice.START"
    const val ACTION_STOP = "com.talentonet.securityall.locationforegroundservice.STOP"
    const val ACTION_CONFIRM_ARRIVAL = "com.talentonet.securityall.locationforegroundservice.CONFIRM_ARRIVAL"
    const val ACTION_REJECT_ARRIVAL = "com.talentonet.securityall.locationforegroundservice.REJECT_ARRIVAL"

    // Extras Keys
    const val EXTRA_ENDPOINT = "com.talentonet.securityall.extra.ENDPOINT"
    const val EXTRA_ALERT_TERMINATION_ENDPOINT = "com.talentonet.securityall.extra.ALERT_TERMINATION_ENDPOINT"
    const val EXTRA_HEADERS = "com.talentonet.securityall.extra.HEADERS"
    const val EXTRA_METADATA = "com.talentonet.securityall.extra.METADATA"
    const val EXTRA_MIN_INTERVAL = "com.talentonet.securityall.extra.MIN_INTERVAL"
    const val EXTRA_FASTEST_INTERVAL = "com.talentonet.securityall.extra.FASTEST_INTERVAL"
    const val EXTRA_MIN_DISTANCE = "com.talentonet.securityall.extra.MIN_DISTANCE"
    const val EXTRA_NOTIFICATION_TITLE = "com.talentonet.securityall.extra.NOTIFICATION_TITLE"
    const val EXTRA_NOTIFICATION_BODY = "com.talentonet.securityall.extra.NOTIFICATION_BODY"
    const val EXTRA_RETRY_DELAY = "com.talentonet.securityall.extra.RETRY_DELAY"
    const val EXTRA_QUEUE_CAPACITY = "com.talentonet.securityall.extra.QUEUE_CAPACITY"
    const val EXTRA_ACCURACY = "com.talentonet.securityall.extra.ACCURACY"
    const val EXTRA_TARGET_LAT = "com.talentonet.securityall.extra.TARGET_LAT"
    const val EXTRA_TARGET_LNG = "com.talentonet.securityall.extra.TARGET_LNG"
    const val EXTRA_TARGET_RANGE = "com.talentonet.securityall.extra.TARGET_RANGE"

    // Defaults
    const val DEFAULT_MIN_INTERVAL = 10_000L
    const val DEFAULT_FASTEST_INTERVAL = 5_000L
    const val DEFAULT_MIN_DISTANCE = 5f
    const val DEFAULT_RETRY_DELAY = 5_000L
    const val DEFAULT_QUEUE_CAPACITY = 32
    const val DEFAULT_TARGET_RANGE = 10.0
    const val DEFAULT_NOTIFICATION_TITLE = "Ubicación activa"
    const val DEFAULT_NOTIFICATION_BODY = "Compartiendo tu ubicación"
}

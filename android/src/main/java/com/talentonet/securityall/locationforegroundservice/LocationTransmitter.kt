package com.talentonet.securityall.locationforegroundservice

import kotlinx.coroutines.flow.StateFlow

interface LocationTransmitter {
    /**
     * Observable state of the connection.
     * `true` if connected and ready to send, `false` otherwise.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Initializes the transmitter with the provided configuration.
     * Use this to setup connections or resources.
     */
    fun initialize(config: TrackingOptions)

    /**
     * Sends the location payload to the configured endpoint.
     * This method is called from the service's flush loop.
     * @return `true` if the message was successfully sent/enqueued, `false` otherwise.
     */
    suspend fun send(payload: LocationFGService.LocationPayload, config: TrackingOptions): Boolean

    /**
     * Cleans up resources (closes sockets, cancels requests, etc.).
     */
    suspend fun shutdown()
}

package com.msa.iotofflinetoolbox.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EndpointProtocol { HTTP, HTTPS, TCP, UDP, WEBSOCKET, MQTT_WEBSOCKET, COAP }

@Serializable
data class EndpointProfile(
    val id: String,
    val name: String,
    val protocol: EndpointProtocol,
    val hostOrUrl: String,
    val port: Int? = null,
    val username: String = "",
    val defaultTopic: String = "",
    val headersText: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
data class PayloadTemplate(
    val id: String,
    val name: String,
    val content: String,
    val contentType: String = "application/json",
    val notes: String = "",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Serializable
enum class HistoryProtocol { PING, DNS, PORT_SCAN, LAN_DISCOVERY, SSDP, MDNS, TCP, UDP, HTTP, WEBSOCKET, MQTT, COAP }

@Serializable
data class HistoryEntry(
    val id: String,
    val timestampMillis: Long,
    val protocol: HistoryProtocol,
    val target: String,
    val summary: String,
    val successful: Boolean,
    val requestPreview: String = "",
    val responsePreview: String = "",
    val durationMillis: Long? = null,
)

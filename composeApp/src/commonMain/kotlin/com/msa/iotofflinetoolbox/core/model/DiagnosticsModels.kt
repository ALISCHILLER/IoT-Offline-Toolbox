package com.msa.iotofflinetoolbox.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PingResult(
    val host: String,
    val reachable: Boolean,
    val latencyMillis: Long?,
    val resolvedAddress: String?,
    val message: String,
)

@Serializable
enum class PortState { OPEN, CLOSED, FILTERED, ERROR }

@Serializable
data class PortScanResult(
    val port: Int,
    val isOpen: Boolean,
    val latencyMillis: Long?,
    val serviceHint: String,
    val error: String? = null,
    val state: PortState = if (isOpen) PortState.OPEN else PortState.CLOSED,
)

@Serializable
data class DiscoveredDevice(
    val host: String,
    val resolvedName: String? = null,
    val latencyMillis: Long? = null,
)

@Serializable
data class SsdpDevice(
    val usn: String,
    val location: String,
    val server: String? = null,
    val searchTarget: String? = null,
    val cacheControl: String? = null,
    val remoteAddress: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class MdnsService(
    val serviceType: String,
    val instanceName: String,
    val host: String? = null,
    val port: Int? = null,
    val addresses: List<String> = emptyList(),
    val txt: Map<String, String> = emptyMap(),
    val ttlSeconds: Long? = null,
)

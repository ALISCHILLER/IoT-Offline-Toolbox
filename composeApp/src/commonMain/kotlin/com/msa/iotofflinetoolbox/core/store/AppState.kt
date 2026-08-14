package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.BackupInspection
import com.msa.iotofflinetoolbox.core.model.CoapResponseResult
import com.msa.iotofflinetoolbox.core.model.DiscoveredDevice
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.core.model.HttpResponseResult
import com.msa.iotofflinetoolbox.core.model.MdnsService
import com.msa.iotofflinetoolbox.core.model.MqttResult
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.PayloadToolResult
import com.msa.iotofflinetoolbox.core.model.PingResult
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.core.model.PortScanResult
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.SocketExchangeResult
import com.msa.iotofflinetoolbox.core.model.SsdpDevice
import com.msa.iotofflinetoolbox.core.model.SubnetResult
import com.msa.iotofflinetoolbox.core.model.ToolLog
import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.core.model.WebSocketResult

/** Immutable application snapshot published by the UDF state holder. */
data class AppState(
    val currentScreen: ToolScreen = ToolScreen.DASHBOARD,
    val isBusy: Boolean = false,
    val operationTitle: String? = null,
    val settings: AppSettings = AppSettings(),
    val capabilities: PlatformCapabilities,
    val pingResult: PingResult? = null,
    val dnsResults: List<String> = emptyList(),
    val socketResult: SocketExchangeResult? = null,
    val portResults: List<PortScanResult> = emptyList(),
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val ssdpDevices: List<SsdpDevice> = emptyList(),
    val mdnsServices: List<MdnsService> = emptyList(),
    val httpResult: HttpResponseResult? = null,
    val webSocketResult: WebSocketResult? = null,
    val mqttResult: MqttResult? = null,
    val coapResult: CoapResponseResult? = null,
    val subnetResult: SubnetResult? = null,
    val payloadResult: PayloadToolResult? = null,
    val savedDevices: List<SavedDevice> = emptyList(),
    val profiles: List<EndpointProfile> = emptyList(),
    val templates: List<PayloadTemplate> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val logs: List<ToolLog> = emptyList(),
    val backupText: String = "",
    val backupInspection: BackupInspection? = null,
    val feedback: AppFeedback? = null,
)

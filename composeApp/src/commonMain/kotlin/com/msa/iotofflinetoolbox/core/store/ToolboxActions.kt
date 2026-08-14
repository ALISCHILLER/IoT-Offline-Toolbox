package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import com.msa.iotofflinetoolbox.core.model.MqttWebSocketInput
import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.model.PayloadOperation
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.SsdpDevice
import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.core.model.WebSocketRequestInput

interface NavigationActions {
    fun navigate(screen: ToolScreen)
    fun dismissError()
    fun dismissNotice()
}

/** Bounded local-network diagnostics and discovery intents. */
interface DiagnosticsActions {
    fun calculateSubnet(cidr: String)
    fun ping(host: String, timeoutMillis: Int)
    fun resolve(host: String)
    fun exchangeTcp(host: String, port: Int, payload: String, encoding: PayloadEncoding, timeoutMillis: Int)
    fun exchangeUdp(host: String, port: Int, payload: String, encoding: PayloadEncoding, timeoutMillis: Int)
    fun scanPorts(host: String, portSpec: String, timeoutMillis: Int)
    fun discover(cidr: String, timeoutMillis: Int)
    fun discoverSsdp(timeoutMillis: Int, maxResults: Int = 100)
    fun discoverMdns(timeoutMillis: Int, maxResults: Int = 100)
}

/** Explicit application intents for request/response protocols. */
interface ProtocolActions {
    fun executeHttp(input: HttpRequestInput)
    fun executeWebSocket(input: WebSocketRequestInput)
    fun executeMqtt(input: MqttWebSocketInput)
    fun executeCoap(input: CoapRequestInput)
}

interface PayloadActions {
    fun transformPayload(
        input: String,
        operation: PayloadOperation,
        variables: Map<String, String> = emptyMap(),
    )
}

interface OperationActions {
    fun cancelOperation()
}

interface DeviceInventoryActions {
    fun saveDevice(
        host: String,
        displayName: String = host,
        addresses: List<String> = listOf(host),
        services: List<String> = emptyList(),
    )

    fun saveSsdpDevice(device: SsdpDevice)
    fun updateDevice(device: SavedDevice)
    fun deleteDevice(id: String)
}

interface ProfileInventoryActions {
    fun saveProfile(profile: EndpointProfile)
    fun deleteProfile(id: String)
}

interface TemplateInventoryActions {
    fun saveTemplate(template: PayloadTemplate)
    fun deleteTemplate(id: String)
}

interface InventoryActions :
    DeviceInventoryActions,
    ProfileInventoryActions,
    TemplateInventoryActions

interface SettingsAndBackupActions {
    fun exportBackup(includeLogs: Boolean)
    fun inspectBackup(raw: String)
    fun importBackup(raw: String, mode: BackupImportMode)
    fun updateSettings(settings: AppSettings)
    fun clearLogs()
    fun clearHistory()
}

/** Public UDF intent surface; narrow feature contracts remain independently fakeable in tests. */
interface ToolboxActions :
    NavigationActions,
    DiagnosticsActions,
    ProtocolActions,
    PayloadActions,
    OperationActions,
    InventoryActions,
    SettingsAndBackupActions

interface ToolboxLifecycle {
    fun close()
}

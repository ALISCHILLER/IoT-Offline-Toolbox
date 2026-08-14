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
import com.msa.iotofflinetoolbox.core.port.NetworkProbe
import com.msa.iotofflinetoolbox.core.port.CoapOperationClient
import com.msa.iotofflinetoolbox.core.port.HttpOperationClient
import com.msa.iotofflinetoolbox.core.port.MqttOperationClient
import com.msa.iotofflinetoolbox.core.port.ToolboxPersistence
import com.msa.iotofflinetoolbox.core.port.WebSocketOperationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Small UDF facade that owns state publication, action delegation and application lifecycle.
 *
 * Ownership of [operationScope] is transferred to the store. The scope must not be shared with
 * unrelated work because [close] cancels it deterministically.
 */
class ToolboxStore(
    private val repository: ToolboxPersistence,
    private val networkProbe: NetworkProbe,
    private val httpClient: HttpOperationClient,
    private val webSocketClient: WebSocketOperationClient,
    private val mqttClient: MqttOperationClient,
    private val coapClient: CoapOperationClient,
    private val runtime: ToolboxRuntime = ToolboxRuntime(),
    private val operationScope: CoroutineScope,
) : ToolboxActions, ToolboxLifecycle {
    private val _state = MutableStateFlow(ToolboxStateLoader(repository, runtime).load(networkProbe.capabilities))
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val messages = ToolboxMessages { _state.value.settings.language }
    private val operationCoordinator = OperationCoordinator(operationScope)
    private val activityJournal = ToolboxActivityJournal(repository, { _state.value }, ::updateState, messages, runtime)
    private val navigationManager = ToolboxNavigationManager(
        persistence = repository,
        state = { _state.value },
        updateState = ::updateState,
        journal = activityJournal,
    )
    private val inventoryContext = ToolboxInventoryContext(
        state = { _state.value },
        updateState = ::updateState,
        messages = messages,
        journal = activityJournal,
        runtime = runtime,
    )
    private val deviceInventory = ToolboxDeviceInventory(repository, inventoryContext)
    private val profileInventory = ToolboxProfileInventory(repository, inventoryContext)
    private val templateInventory = ToolboxTemplateInventory(repository, inventoryContext)
    private val operationRunner = ToolboxOperationRunner(
        state = { _state.value },
        updateState = ::updateState,
        messages = messages,
        journal = activityJournal,
        coordinator = operationCoordinator,
    )
    private val diagnosticsOperations = ToolboxDiagnosticsOperations(networkProbe, operationRunner)
    private val protocolOperations = ToolboxProtocolOperations(
        httpClient = httpClient,
        webSocketClient = webSocketClient,
        mqttClient = mqttClient,
        coapClient = coapClient,
        runner = operationRunner,
    )
    private val payloadOperations = ToolboxPayloadOperations(operationRunner)
    private val settingsManager = ToolboxSettingsManager(
        persistence = repository,
        updateState = ::updateState,
        messages = messages,
        journal = activityJournal,
    )

    override fun navigate(screen: ToolScreen) = navigationManager.navigate(screen)

    override fun dismissError() {
        updateState {
            if (feedback?.kind == FeedbackKind.ERROR) copy(feedback = null) else this
        }
    }

    override fun dismissNotice() {
        updateState {
            if (feedback?.kind == FeedbackKind.NOTICE) copy(feedback = null) else this
        }
    }

    override fun calculateSubnet(cidr: String) = diagnosticsOperations.calculateSubnet(cidr)

    override fun ping(host: String, timeoutMillis: Int) = diagnosticsOperations.ping(host, timeoutMillis)

    override fun resolve(host: String) = diagnosticsOperations.resolve(host)

    override fun exchangeTcp(host: String, port: Int, payload: String, encoding: PayloadEncoding, timeoutMillis: Int) =
        diagnosticsOperations.exchangeTcp(host, port, payload, encoding, timeoutMillis)

    override fun exchangeUdp(host: String, port: Int, payload: String, encoding: PayloadEncoding, timeoutMillis: Int) =
        diagnosticsOperations.exchangeUdp(host, port, payload, encoding, timeoutMillis)

    override fun executeCoap(input: CoapRequestInput) = protocolOperations.executeCoap(input)

    override fun scanPorts(host: String, portSpec: String, timeoutMillis: Int) =
        diagnosticsOperations.scanPorts(host, portSpec, timeoutMillis)

    override fun discover(cidr: String, timeoutMillis: Int) = diagnosticsOperations.discover(cidr, timeoutMillis)

    override fun discoverSsdp(timeoutMillis: Int, maxResults: Int) =
        diagnosticsOperations.discoverSsdp(timeoutMillis, maxResults)

    override fun discoverMdns(timeoutMillis: Int, maxResults: Int) =
        diagnosticsOperations.discoverMdns(timeoutMillis, maxResults)

    override fun executeHttp(input: HttpRequestInput) =
        protocolOperations.executeHttp(input)

    override fun executeWebSocket(input: WebSocketRequestInput) = protocolOperations.executeWebSocket(input)

    override fun executeMqtt(input: MqttWebSocketInput) = protocolOperations.executeMqtt(input)

    override fun transformPayload(input: String, operation: PayloadOperation, variables: Map<String, String>) =
        payloadOperations.transformPayload(input, operation, variables)

    override fun saveDevice(
        host: String,
        displayName: String,
        addresses: List<String>,
        services: List<String>,
    ) = deviceInventory.saveDevice(host, displayName, addresses, services)

    override fun saveSsdpDevice(device: SsdpDevice) = deviceInventory.saveSsdpDevice(device)

    override fun updateDevice(device: SavedDevice) = deviceInventory.updateDevice(device)

    override fun deleteDevice(id: String) = deviceInventory.deleteDevice(id)

    override fun saveProfile(profile: EndpointProfile) = profileInventory.saveProfile(profile)

    override fun deleteProfile(id: String) = profileInventory.deleteProfile(id)

    override fun saveTemplate(template: PayloadTemplate) = templateInventory.saveTemplate(template)

    override fun deleteTemplate(id: String) = templateInventory.deleteTemplate(id)

    override fun exportBackup(includeLogs: Boolean) = settingsManager.exportBackup(includeLogs)

    override fun inspectBackup(raw: String) = settingsManager.inspectBackup(raw)

    override fun importBackup(raw: String, mode: BackupImportMode) = settingsManager.importBackup(raw, mode)

    override fun updateSettings(settings: AppSettings) = settingsManager.updateSettings(settings)

    override fun clearLogs() = settingsManager.clearLogs()

    override fun clearHistory() = settingsManager.clearHistory()

    override fun cancelOperation() = operationRunner.cancel()

    override fun close() {
        operationCoordinator.close()
        runCatching(networkProbe::close)
        runCatching(httpClient::close)
        runCatching(webSocketClient::close)
        runCatching(mqttClient::close)
        operationScope.cancel()
    }

    private fun updateState(transform: AppState.() -> AppState) {
        _state.update { current -> current.transform() }
    }
}

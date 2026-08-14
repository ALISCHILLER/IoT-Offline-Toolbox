package com.msa.iotofflinetoolbox


import com.msa.iotofflinetoolbox.core.data.ToolboxRepository
import com.msa.iotofflinetoolbox.core.network.CoapClient
import com.msa.iotofflinetoolbox.core.network.HttpToolClient
import com.msa.iotofflinetoolbox.core.network.MqttWebSocketClient
import com.msa.iotofflinetoolbox.core.network.WebSocketToolClient
import com.msa.iotofflinetoolbox.core.network.createPlatformNetworkProbe
import com.msa.iotofflinetoolbox.core.store.ToolboxRuntime
import com.msa.iotofflinetoolbox.core.store.ToolboxStore
import kotlinx.coroutines.MainScope

/** Single composition root for production adapters. */
fun createToolboxStore(
    runtime: ToolboxRuntime = ToolboxRuntime(),
): ToolboxStore {
    val operationScope = MainScope()
    val networkProbe = createPlatformNetworkProbe()
    return ToolboxStore(
        repository = ToolboxRepository(nowMillis = runtime.clock::nowMillis),
        networkProbe = networkProbe,
        httpClient = HttpToolClient(),
        webSocketClient = WebSocketToolClient(),
        mqttClient = MqttWebSocketClient(),
        coapClient = CoapClient(networkProbe),
        runtime = runtime,
        operationScope = operationScope,
    )
}

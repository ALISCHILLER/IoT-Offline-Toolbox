package com.msa.iotofflinetoolbox.core.port

import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import com.msa.iotofflinetoolbox.core.model.CoapResponseResult
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import com.msa.iotofflinetoolbox.core.model.HttpResponseResult
import com.msa.iotofflinetoolbox.core.model.MqttResult
import com.msa.iotofflinetoolbox.core.model.MqttWebSocketInput
import com.msa.iotofflinetoolbox.core.model.WebSocketRequestInput
import com.msa.iotofflinetoolbox.core.model.WebSocketResult

interface HttpOperationClient {
    suspend fun execute(
        input: HttpRequestInput,
        maxResponseBytes: Int,
        allowPublicCleartext: Boolean = false,
    ): HttpResponseResult

    fun close()
}

interface WebSocketOperationClient {
    suspend fun execute(
        input: WebSocketRequestInput,
        allowPublicCleartext: Boolean = false,
    ): WebSocketResult

    fun close()
}

interface MqttOperationClient {
    suspend fun execute(
        input: MqttWebSocketInput,
        allowPublicCleartext: Boolean = false,
    ): MqttResult

    fun close()
}

interface CoapOperationClient {
    suspend fun execute(input: CoapRequestInput, maxResponseBytes: Int): CoapResponseResult
}

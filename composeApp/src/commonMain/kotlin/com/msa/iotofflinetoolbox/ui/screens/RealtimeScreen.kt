package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.CoapMessageType
import com.msa.iotofflinetoolbox.core.model.CoapMethod
import com.msa.iotofflinetoolbox.core.model.CoapRequestInput
import com.msa.iotofflinetoolbox.core.model.EndpointProtocol
import com.msa.iotofflinetoolbox.core.model.MessageDirection
import com.msa.iotofflinetoolbox.core.model.MqttWebSocketInput
import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.model.WebSocketRequestInput
import com.msa.iotofflinetoolbox.core.policy.TransportSecurity
import com.msa.iotofflinetoolbox.ui.components.CapabilityBadge
import com.msa.iotofflinetoolbox.ui.components.CodeSurface
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.KeyValueRow
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.ResponsiveFields
import com.msa.iotofflinetoolbox.ui.components.ResponsivePanes
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.ToolTabRow
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.components.responsiveTextAreaMinLines
import com.msa.iotofflinetoolbox.ui.localization.localizedNotes

@Composable
fun RealtimeScreen(
    state: AppState,
    onWebSocket: (WebSocketRequestInput) -> Unit,
    onMqtt: (MqttWebSocketInput) -> Unit,
    onCoap: (CoapRequestInput) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ToolTabRow(
            selectedIndex = tab,
            labels = listOf("WebSocket", "MQTT / WS", "CoAP / UDP"),
            onSelect = { tab = it },
        )
        when (tab) {
            0 -> WebSocketPanel(state, onWebSocket)
            1 -> MqttPanel(state, onMqtt)
            else -> CoapPanel(state, onCoap)
        }
    }
}

@Composable
private fun WebSocketPanel(
    state: AppState,
    onExecute: (WebSocketRequestInput) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Hello from MSA IoT Offline Toolbox") }
    var encoding by remember { mutableStateOf(PayloadEncoding.TEXT) }
    var timeout by remember { mutableStateOf("5000") }
    val timeoutValue = timeout.toIntOrNull()
    val valid = isWebSocketUrl(url) && timeoutValue != null && timeoutValue in 100..60_000
    val result = state.webSocketResult

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.websocket_workbench_6b2b460a),
                subtitle = stringResource(Res.string.open_a_bounded_session_send_text_or_binary_6f63709b),
                eyebrow = stringResource(Res.string.real_time_transport_2849c21a),
            )
        }
        item {
            RealtimeAvailability(
                available = state.capabilities.webSocketClient,
                label = "WebSocket",
                notes = state.capabilities.localizedNotes(),
            )
        }
        val profiles = state.profiles.filter { it.protocol == EndpointProtocol.WEBSOCKET }
        if (profiles.isNotEmpty()) {
            item {
                QuickProfiles(
                    profiles = profiles.map { it.name to {
                        url = it.hostOrUrl
                        headers = if (state.capabilities.customWebSocketHeaders) it.headersText else ""
                    } },
                )
            }
        }
        item {
            ResponsivePanes(
                minimumPaneWidth = 460.dp,
                primary = { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.connection_and_frame_e5b97bbe),
                        modifier = modifier,
                        action = { CapabilityBadge("WebSocket", state.capabilities.webSocketClient) },
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(Res.string.ws_or_wss_url_cb22c20d)) },
                            placeholder = { Text("ws://192.168.1.50/socket") },
                            supportingText = { Text(stringResource(Res.string.prefer_wss_outside_trusted_local_networks_e61653a1)) },
                            isError = url.isNotBlank() && !isWebSocketUrl(url),
                            leadingIcon = { Icon(Icons.Outlined.Cable, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = headers,
                            onValueChange = { headers = it },
                            label = { Text(stringResource(Res.string.optional_headers_975852d4)) },
                            supportingText = {
                                Text(
                                    if (state.capabilities.customWebSocketHeaders) {
                                        stringResource(Res.string.one_header_value_per_line_sensitive_values_are_5f3764b0)
                                    } else {
                                        stringResource(Res.string.websocket_headers_unsupported)
                                    },
                                )
                            },
                            enabled = state.capabilities.customWebSocketHeaders,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = responsiveTextAreaMinLines(3, 2),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        )
                        EncodingRow(selected = encoding, onSelect = { encoding = it })
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text(stringResource(Res.string.frame_1_s_5ad3ed1e, encoding.name)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = responsiveTextAreaMinLines(6, 3),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        )
                        OutlinedTextField(
                            value = timeout,
                            onValueChange = { timeout = it.filter(Char::isDigit) },
                            label = { Text(stringResource(Res.string.receive_timeout_ms_c92b863a)) },
                            supportingText = { Text(stringResource(Res.string.s_100_60_000_ms_5108191b)) },
                            isError = timeoutValue == null || timeoutValue !in 100..60_000,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                onExecute(
                                    WebSocketRequestInput(
                                        url = url,
                                        headersText = headers,
                                        message = message,
                                        messageEncoding = encoding,
                                        receiveTimeoutMillis = timeoutValue ?: 5_000,
                                        maxMessageBytes = state.settings.maxResponseBytes,
                                    ),
                                )
                            },
                            enabled = !state.isBusy && state.capabilities.webSocketClient && valid,
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = null)
                            Text(stringResource(Res.string.connect_and_send_b1548f89))
                        }
                    }
                },
                secondary = { modifier ->
                    SectionCard(stringResource(Res.string.session_summary_250af1df), modifier = modifier) {
                        if (result == null) {
                            EmptyState(
                                title = stringResource(Res.string.no_session_transcript_5aaf48bc),
                                message = stringResource(Res.string.run_an_exchange_to_capture_connection_timing_and_de6341dd),
                                icon = Icons.Outlined.Cable,
                            )
                        } else {
                            StatusBanner(
                                title = if (result.connected) stringResource(Res.string.websocket_session_completed_31b55e21) else stringResource(Res.string.websocket_session_failed_482e27ff),
                                message = result.error ?: result.closeReason ?: stringResource(Res.string.the_bounded_receive_window_completed_129c643f),
                                tone = if (result.connected && result.error == null) StatusTone.SUCCESS else StatusTone.ERROR,
                            )
                            KeyValueRow(stringResource(Res.string.endpoint_e51e5f22), result.endpoint, monospaced = true)
                            KeyValueRow(stringResource(Res.string.elapsed_b38c4647), "${result.elapsedMillis} ms")
                            KeyValueRow(stringResource(Res.string.frames_b9e32b50), result.messages.size.toString())
                            result.securityWarning?.let {
                                StatusBanner(stringResource(Res.string.transport_warning_60f1b743), it, tone = StatusTone.WARNING)
                            }
                        }
                    }
                },
            )
        }
        if (result != null && result.messages.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.frame_transcript_f4dcac3e),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(result.messages, key = { "${it.timestampMillis}-${it.direction}-${it.payload.hashCode()}" }) { frame ->
                SectionCard(
                    title = when (frame.direction) {
                        MessageDirection.SENT -> stringResource(Res.string.sent_frame_5785588e)
                        MessageDirection.RECEIVED -> stringResource(Res.string.received_frame_4d5354f2)
                        MessageDirection.SYSTEM -> stringResource(Res.string.system_event_a1fa7ae2)
                    },
                    action = {
                        Text(
                            text = listOfNotNull(
                                if (frame.binary) {
                                    stringResource(Res.string.frame_binary_short)
                                } else {
                                    stringResource(Res.string.text_461919df)
                                },
                                if (frame.truncated) stringResource(Res.string.truncated_a542fc55) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    CodeSurface(frame.payload, maxHeight = 240.dp)
                }
            }
        }
    }
}

@Composable
private fun MqttPanel(
    state: AppState,
    onExecute: (MqttWebSocketInput) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("msa-iot-toolbox") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var subscribeTopic by remember { mutableStateOf("msa/toolbox/#") }
    var subscribeQos by remember { mutableIntStateOf(0) }
    var publishTopic by remember { mutableStateOf("msa/toolbox/test") }
    var publishQos by remember { mutableIntStateOf(0) }
    var payload by remember { mutableStateOf("{\"source\":\"MSA IoT Offline Toolbox\"}") }
    var retain by remember { mutableStateOf(false) }
    var cleanSession by remember { mutableStateOf(true) }
    var timeout by remember { mutableStateOf("5000") }
    val timeoutValue = timeout.toIntOrNull()
    val valid = isWebSocketUrl(url) && clientId.isNotBlank() &&
        timeoutValue != null && timeoutValue in 100..60_000 && (password.isBlank() || username.isNotBlank())
    val result = state.mqttResult

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.mqtt_over_websocket_dab73c3f),
                subtitle = stringResource(Res.string.connect_subscribe_and_publish_with_explicit_qos_acknowledgement_c4fc4d1b),
                eyebrow = stringResource(Res.string.broker_session_f0748cbd),
            )
        }
        item {
            RealtimeAvailability(
                available = state.capabilities.mqttWebSocketClient,
                label = "MQTT / WebSocket",
                notes = state.capabilities.localizedNotes(),
            )
        }
        val profiles = state.profiles.filter { it.protocol == EndpointProtocol.MQTT_WEBSOCKET }
        if (profiles.isNotEmpty()) {
            item {
                QuickProfiles(
                    profiles = profiles.map { profile ->
                        profile.name to {
                            url = profile.hostOrUrl
                            username = profile.username
                            subscribeTopic = profile.defaultTopic.ifBlank { subscribeTopic }
                        }
                    },
                )
            }
        }
        item {
            ResponsivePanes(
                minimumPaneWidth = 470.dp,
                primary = { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.broker_connection_0bac07fb),
                        modifier = modifier,
                        action = { CapabilityBadge("MQTT", state.capabilities.mqttWebSocketClient) },
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(Res.string.broker_websocket_url_f94fdf76)) },
                            placeholder = { Text("ws://192.168.1.50:9001/mqtt") },
                            leadingIcon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
                            supportingText = { Text(stringResource(Res.string.use_the_broker_s_websocket_endpoint_often_ending_49249a15)) },
                            isError = url.isNotBlank() && !isWebSocketUrl(url),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        ResponsiveFields(
                            fields = *arrayOf(
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = clientId,
                                        onValueChange = { clientId = it },
                                        label = { Text(stringResource(Res.string.client_id_1285e5f8)) },
                                        isError = clientId.isBlank(),
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = timeout,
                                        onValueChange = { timeout = it.filter(Char::isDigit) },
                                        label = { Text(stringResource(Res.string.timeout_ms_3813d458)) },
                                        isError = timeoutValue == null || timeoutValue !in 100..60_000,
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                            ),
                        )
                        ResponsiveFields(
                            fields = *arrayOf(
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text(stringResource(Res.string.username_a29c85bc)) },
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text(stringResource(Res.string.password_memory_only_e47c16b1)) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        isError = password.isNotBlank() && username.isBlank(),
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                            ),
                        )
                        StatusBanner(
                            title = stringResource(Res.string.credential_handling_dc4fa87c),
                            message = stringResource(Res.string.the_password_is_held_only_for_this_composition_29ee68bb),
                            tone = StatusTone.INFO,
                        )
                    }
                },
                secondary = { modifier ->
                    SectionCard(stringResource(Res.string.subscription_and_publish_5b6e2079), modifier = modifier) {
                        OutlinedTextField(
                            value = subscribeTopic,
                            onValueChange = { subscribeTopic = it },
                            label = { Text(stringResource(Res.string.subscribe_filter_1ed683b1)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        QosRow(stringResource(Res.string.subscribe_qos_6e459266), subscribeQos, 1) { subscribeQos = it }
                        OutlinedTextField(
                            value = publishTopic,
                            onValueChange = { publishTopic = it },
                            label = { Text(stringResource(Res.string.publish_topic_5d6a0891)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        QosRow(stringResource(Res.string.publish_qos_18e56652), publishQos, 1) { publishQos = it }
                        OutlinedTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            label = { Text(stringResource(Res.string.publish_payload_f8f1dded)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = responsiveTextAreaMinLines(5, 3),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        )
                        ResponsiveActions {
                            FilterChip(selected = retain, onClick = { retain = !retain }, label = { Text(stringResource(Res.string.retain_cb280d2a)) })
                            FilterChip(selected = cleanSession, onClick = { cleanSession = !cleanSession }, label = { Text(stringResource(Res.string.clean_session_d6a83ec7)) })
                        }
                        Button(
                            onClick = {
                                onExecute(
                                    MqttWebSocketInput(
                                        url = url,
                                        clientId = clientId,
                                        username = username,
                                        password = password,
                                        subscribeTopic = subscribeTopic,
                                        subscribeQos = subscribeQos,
                                        publishTopic = publishTopic,
                                        publishQos = publishQos,
                                        payload = payload,
                                        retain = retain,
                                        cleanSession = cleanSession,
                                        receiveTimeoutMillis = timeoutValue ?: 5_000,
                                        maxMessageBytes = state.settings.maxResponseBytes,
                                    ),
                                )
                            },
                            enabled = !state.isBusy && state.capabilities.mqttWebSocketClient && valid,
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                            Text(stringResource(Res.string.run_mqtt_session_580d440e))
                        }
                    }
                },
            )
        }
        if (result == null) {
            item {
                EmptyState(
                    title = stringResource(Res.string.no_mqtt_session_evidence_b68223d7),
                    message = stringResource(Res.string.run_a_session_to_inspect_connack_suback_puback_919b9d1d),
                    icon = Icons.Outlined.CloudSync,
                )
            }
        } else {
            item {
                SectionCard(stringResource(Res.string.session_evidence_8f639071)) {
                    StatusBanner(
                        title = if (result.operationSuccessful) stringResource(Res.string.mqtt_operation_succeeded_4e33afb1) else stringResource(Res.string.mqtt_operation_incomplete_a5c868bc),
                        message = result.error ?: stringResource(Res.string.connection_and_requested_acknowledgements_were_evaluated_4fc2a33e),
                        tone = if (result.operationSuccessful) StatusTone.SUCCESS else StatusTone.ERROR,
                    )
                    KeyValueRow(stringResource(Res.string.endpoint_e51e5f22), result.endpoint, monospaced = true)
                    KeyValueRow(stringResource(Res.string.connected_8049f307), yesNo(result.connected))
                    KeyValueRow(stringResource(Res.string.session_present_fa9af6cd), yesNo(result.sessionPresent))
                    KeyValueRow(stringResource(Res.string.subscribed_8836796e), yesNo(result.subscribed))
                    KeyValueRow(stringResource(Res.string.publish_acknowledged_85425a28), yesNo(result.publishAcknowledged))
                    KeyValueRow(stringResource(Res.string.return_code_7d33d582), result.returnCode.toString())
                    KeyValueRow(stringResource(Res.string.elapsed_b38c4647), "${result.elapsedMillis} ms")
                    result.securityWarning?.let { StatusBanner(stringResource(Res.string.transport_warning_60f1b743), it, tone = StatusTone.WARNING) }
                    if (result.events.isNotEmpty()) {
                        Text(stringResource(Res.string.protocol_events_85542c5f), style = MaterialTheme.typography.labelLarge)
                        CodeSurface(result.events.joinToString("\n") { "• $it" }, maxHeight = 320.dp)
                    }
                }
            }
            if (result.messages.isNotEmpty()) {
                item { Text(stringResource(Res.string.received_mqtt_messages_c894aec3), style = MaterialTheme.typography.titleLarge) }
                items(result.messages, key = { "${it.timestampMillis}-${it.topic}-${it.payload.hashCode()}" }) { mqttMessage ->
                    SectionCard(
                        title = mqttMessage.topic,
                        action = {
                            Text(
                                buildString {
                                    append("QoS ${mqttMessage.qos}")
                                    if (mqttMessage.retained) append(" · RETAIN")
                                    if (mqttMessage.duplicate) append(" · DUP")
                                    if (mqttMessage.binary) append(" · ${stringResource(Res.string.frame_binary_short)}")
                                    if (mqttMessage.truncated) append(" · ${stringResource(Res.string.truncated_a542fc55)}")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    ) {
                        CodeSurface(mqttMessage.payload, maxHeight = 240.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoapPanel(
    state: AppState,
    onExecute: (CoapRequestInput) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5683") }
    var path by remember { mutableStateOf("/.well-known/core") }
    var query by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(CoapMethod.GET) }
    var type by remember { mutableStateOf(CoapMessageType.CONFIRMABLE) }
    var contentFormat by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("3000") }
    var methodMenu by remember { mutableStateOf(false) }
    val portValue = port.toIntOrNull()
    val timeoutValue = timeout.toIntOrNull()
    val valid = host.isNotBlank() && portValue != null && portValue in 1..65_535 && timeoutValue != null && timeoutValue in 100..60_000
    val result = state.coapResult

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.coap_workbench_2eec23a8),
                subtitle = stringResource(Res.string.send_a_bounded_rfc_7252_request_over_udp_b034977a),
                eyebrow = stringResource(Res.string.constrained_devices_c59c2422),
            )
        }
        item {
            RealtimeAvailability(
                available = state.capabilities.coapClient,
                label = "CoAP / UDP",
                notes = state.capabilities.localizedNotes(),
            )
        }
        val profiles = state.profiles.filter { it.protocol == EndpointProtocol.COAP }
        if (profiles.isNotEmpty()) {
            item {
                QuickProfiles(
                    profiles = profiles.map { profile ->
                        profile.name to {
                            host = profile.hostOrUrl
                            port = (profile.port ?: 5683).toString()
                            path = profile.defaultTopic.ifBlank { path }
                        }
                    },
                )
            }
        }
        item {
            ResponsivePanes(
                minimumPaneWidth = 460.dp,
                primary = { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.coap_request_09493f42),
                        modifier = modifier,
                        action = { CapabilityBadge("CoAP", state.capabilities.coapClient) },
                    ) {
                        ResponsiveFields(
                            fields = *arrayOf(
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = host,
                                        onValueChange = { host = it.trim() },
                                        label = { Text(stringResource(Res.string.host_40f11ffc)) },
                                        isError = host.isBlank(),
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = port,
                                        onValueChange = { port = it.filter(Char::isDigit) },
                                        label = { Text(stringResource(Res.string.port_866d53af)) },
                                        isError = portValue == null || portValue !in 1..65_535,
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                            ),
                        )
                        ResponsiveActions {
                            Box {
                                OutlinedButton(onClick = { methodMenu = true }) { Text(method.name) }
                                DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                                    CoapMethod.entries.forEach { item ->
                                        DropdownMenuItem(
                                            text = { Text(item.name) },
                                            onClick = {
                                                method = item
                                                methodMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            CoapMessageType.entries.forEach { item ->
                                FilterChip(
                                    selected = type == item,
                                    onClick = { type = item },
                                    label = { Text(if (item == CoapMessageType.CONFIRMABLE) "CON" else "NON") },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = { Text(stringResource(Res.string.uri_path_a7b9e629)) },
                            supportingText = { Text(stringResource(Res.string.example_well_known_core_28d4de8c)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(Res.string.uri_query_f7ffca0d)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        ResponsiveFields(
                            fields = *arrayOf(
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = contentFormat,
                                        onValueChange = { contentFormat = it.filter(Char::isDigit) },
                                        label = { Text(stringResource(Res.string.content_format_8e532ff4)) },
                                        supportingText = { Text(stringResource(Res.string.optional_numeric_code_30a8e26a)) },
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                                { fieldModifier ->
                                    OutlinedTextField(
                                        value = timeout,
                                        onValueChange = { timeout = it.filter(Char::isDigit) },
                                        label = { Text(stringResource(Res.string.timeout_ms_3813d458)) },
                                        isError = timeoutValue == null || timeoutValue !in 100..60_000,
                                        modifier = fieldModifier,
                                        singleLine = true,
                                    )
                                },
                            ),
                        )
                        OutlinedTextField(
                            value = payload,
                            onValueChange = { payload = it },
                            label = { Text(stringResource(Res.string.payload_489cdded)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = responsiveTextAreaMinLines(5, 3),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        )
                        Button(
                            onClick = {
                                onExecute(
                                    CoapRequestInput(
                                        host = host,
                                        port = portValue ?: 5683,
                                        method = method,
                                        messageType = type,
                                        path = path,
                                        query = query,
                                        payload = payload,
                                        contentFormat = contentFormat.toIntOrNull(),
                                        timeoutMillis = timeoutValue ?: 3_000,
                                    ),
                                )
                            },
                            enabled = !state.isBusy && state.capabilities.coapClient && valid,
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = null)
                            Text(stringResource(Res.string.send_coap_request_142de8ce))
                        }
                    }
                },
                secondary = { modifier ->
                    SectionCard(stringResource(Res.string.correlated_response_3fbbd3b3), modifier = modifier) {
                        if (result == null) {
                            EmptyState(
                                title = stringResource(Res.string.no_coap_response_28aab000),
                                message = stringResource(Res.string.send_a_request_to_inspect_code_token_message_8219c055),
                                icon = Icons.Outlined.Router,
                            )
                        } else {
                            StatusBanner(
                                title = result.responseCodeText,
                                message = result.error ?: stringResource(Res.string.a_correlated_response_was_received_inside_the_bounded_09b66db4),
                                tone = if (result.error == null) StatusTone.SUCCESS else StatusTone.ERROR,
                            )
                            KeyValueRow(stringResource(Res.string.endpoint_e51e5f22), result.endpoint, monospaced = true)
                            KeyValueRow(stringResource(Res.string.message_id_7ce83039), result.messageId.toString())
                            KeyValueRow(stringResource(Res.string.token_65e35b7c), result.tokenHex, monospaced = true)
                            KeyValueRow(stringResource(Res.string.elapsed_b38c4647), "${result.elapsedMillis} ms")
                            KeyValueRow(stringResource(Res.string.content_format_8e532ff4), result.contentFormat?.toString() ?: "—")
                            KeyValueRow(stringResource(Res.string.truncated_a6cfb88e), yesNo(result.truncated))
                            if (result.payloadText.isNotBlank()) {
                                Text(stringResource(Res.string.text_payload_d4c24252), style = MaterialTheme.typography.labelLarge)
                                CodeSurface(result.payloadText, maxHeight = 260.dp)
                            }
                            if (result.payloadHex.isNotBlank()) {
                                Text("HEX", style = MaterialTheme.typography.labelLarge)
                                CodeSurface(result.payloadHex, maxHeight = 220.dp)
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun EncodingRow(
    selected: PayloadEncoding,
    onSelect: (PayloadEncoding) -> Unit,
) {
    ResponsiveActions {
        PayloadEncoding.entries.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.name) },
            )
        }
    }
}

@Composable
private fun QosRow(
    label: String,
    selected: Int,
    max: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ResponsiveActions {
            (0..max).forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(value.toString()) },
                )
            }
        }
    }
}

@Composable
private fun RealtimeAvailability(
    available: Boolean,
    label: String,
    notes: String,
) {
    StatusBanner(
        title = if (available) stringResource(Res.string.s_1_s_is_available_e84eb1fc, label) else stringResource(Res.string.s_1_s_is_unavailable_here_573cda53, label),
        message = if (available) {
            stringResource(Res.string.frame_message_payload_and_timeout_limits_remain_enforced_a6edf6fa)
        } else {
            notes
        },
        tone = if (available) StatusTone.INFO else StatusTone.WARNING,
    )
}

@Composable
private fun QuickProfiles(
    profiles: List<Pair<String, () -> Unit>>,
) {
    SectionCard(stringResource(Res.string.quick_profiles_fa2200c1)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            profiles.forEach { (name, apply) ->
                AssistChip(
                    onClick = apply,
                    label = { Text(name) },
                    leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun yesNo(value: Boolean): String = if (value) stringResource(Res.string.yes_e309612b) else stringResource(Res.string.no_c30d5b7b)
private fun isWebSocketUrl(value: String): Boolean =
    TransportSecurity.hasScheme(value, "ws", "wss") &&
        !TransportSecurity.hasEmbeddedCredentials(value) &&
        TransportSecurity.extractHost(value).isNotBlank()


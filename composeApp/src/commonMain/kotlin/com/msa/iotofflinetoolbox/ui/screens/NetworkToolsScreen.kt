package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.AppState
import com.msa.iotofflinetoolbox.core.model.EndpointProtocol
import com.msa.iotofflinetoolbox.core.model.PayloadEncoding
import com.msa.iotofflinetoolbox.core.model.PortState
import com.msa.iotofflinetoolbox.core.network.PortPreset
import com.msa.iotofflinetoolbox.core.network.PortPresets
import com.msa.iotofflinetoolbox.core.network.PortSpecParser
import com.msa.iotofflinetoolbox.core.network.serviceHint
import com.msa.iotofflinetoolbox.ui.components.CapabilityBadge
import com.msa.iotofflinetoolbox.ui.components.CodeSurface
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.KeyValueRow
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.ResponsiveCardGrid
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

private enum class NetworkTab {
    PING,
    PORTS,
    TCP,
    UDP,
    SUBNET,
    DNS,
}

private enum class PortResultFilter { ALL, OPEN, CLOSED, FILTERED, ERROR }

@Composable
fun NetworkToolsScreen(
    state: AppState,
    onPing: (String, Int) -> Unit,
    onScanPorts: (String, String, Int) -> Unit,
    onCalculateSubnet: (String) -> Unit,
    onResolve: (String) -> Unit,
    onTcpExchange: (String, Int, String, PayloadEncoding, Int) -> Unit,
    onUdpExchange: (String, Int, String, PayloadEncoding, Int) -> Unit,
) {
    var tab by remember { mutableStateOf(NetworkTab.PING) }
    Column(Modifier.fillMaxSize()) {
        ToolTabRow(
            selectedIndex = tab.ordinal,
            labels = NetworkTab.entries.map { it.localizedLabel() },
            onSelect = { tab = NetworkTab.entries[it] },
        )
        when (tab) {
            NetworkTab.PING -> PingPanel(state, onPing)
            NetworkTab.PORTS -> PortPanel(state, onScanPorts)
            NetworkTab.TCP -> SocketPanel("TCP", state, state.capabilities.tcpClient, onTcpExchange)
            NetworkTab.UDP -> SocketPanel("UDP", state, state.capabilities.udpClient, onUdpExchange)
            NetworkTab.SUBNET -> SubnetPanel(state, onCalculateSubnet)
            NetworkTab.DNS -> DnsPanel(state, onResolve)
        }
    }
}

@Composable
private fun NetworkTab.localizedLabel(): String = when (this) {
    NetworkTab.PING -> stringResource(Res.string.reachability_f6117393)
    NetworkTab.PORTS -> stringResource(Res.string.ports_00a52b2a)
    NetworkTab.TCP -> "TCP"
    NetworkTab.UDP -> "UDP"
    NetworkTab.SUBNET -> stringResource(Res.string.subnet_e18b3ff9)
    NetworkTab.DNS -> "DNS"
}

@Composable
private fun PingPanel(
    state: AppState,
    onPing: (String, Int) -> Unit,
) {
    var host by remember { mutableStateOf("192.168.1.1") }
    var timeout by remember { mutableStateOf(state.settings.defaultTimeoutMillis.toString()) }
    val timeoutValue = timeout.toIntOrNull()
    val valid = host.isNotBlank() && timeoutValue != null && timeoutValue in 100..60_000

    ToolPage(
        title = stringResource(Res.string.reachability_probe_09c6aa44),
        subtitle = stringResource(Res.string.resolve_a_host_and_run_a_bounded_reachability_27e8ecdd),
        eyebrow = stringResource(Res.string.diagnostics_78bd74b5),
    ) {
        ToolAvailability(
            available = state.capabilities.ping,
            label = stringResource(Res.string.reachability_probe_ec20bd09),
            notes = state.capabilities.localizedNotes(),
        )
        SectionCard(
            title = stringResource(Res.string.probe_request_09816ae0),
            action = { CapabilityBadge(stringResource(Res.string.probe_e2848028), state.capabilities.ping) },
        ) {
            EndpointFields(
                host = host,
                onHost = { host = it.trim() },
                port = "",
                onPort = {},
                timeout = timeout,
                onTimeout = { timeout = it },
                showPort = false,
            )
            Button(
                onClick = { onPing(host, timeoutValue ?: state.settings.defaultTimeoutMillis) },
                enabled = !state.isBusy && state.capabilities.ping && valid,
            ) {
                Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                Text(stringResource(Res.string.run_probe_c1d0ff15))
            }
        }
        state.pingResult?.let { result ->
            StatusBanner(
                title = if (result.reachable) stringResource(Res.string.host_is_reachable_5f63c20e) else stringResource(Res.string.host_is_not_reachable_240f5507),
                message = result.message,
                tone = if (result.reachable) StatusTone.SUCCESS else StatusTone.ERROR,
            )
            SectionCard(stringResource(Res.string.probe_evidence_c6f3f750)) {
                KeyValueRow(stringResource(Res.string.requested_host_4b865122), result.host, monospaced = true)
                KeyValueRow(
                    stringResource(Res.string.resolved_address_9ef47f59),
                    result.resolvedAddress ?: stringResource(Res.string.unavailable_167bcc46),
                    monospaced = true,
                )
                KeyValueRow(
                    stringResource(Res.string.latency_fb2c7d2e),
                    result.latencyMillis?.let { "$it ms" } ?: stringResource(Res.string.not_measured_c2c2debb),
                )
            }
        } ?: EmptyState(
            title = stringResource(Res.string.no_probe_evidence_f19fb86e),
            message = stringResource(Res.string.enter_a_host_and_run_the_probe_to_d7526bc3),
            icon = Icons.Outlined.NetworkCheck,
        )
    }
}

@Composable
private fun PortPanel(
    state: AppState,
    onScan: (String, String, Int) -> Unit,
) {
    var host by remember { mutableStateOf("192.168.1.1") }
    var ports by remember { mutableStateOf(PortPresets.recommended.specification) }
    var timeout by remember { mutableStateOf(state.settings.defaultTimeoutMillis.toString()) }
    var filter by remember { mutableStateOf(PortResultFilter.ALL) }
    val timeoutValue = timeout.toIntOrNull()
    val parsedPorts = remember(ports, state.settings.maxPortScanItems) {
        runCatching { PortSpecParser.parse(ports, state.settings.maxPortScanItems) }
    }
    val valid = host.isNotBlank() && timeoutValue != null && timeoutValue in 100..60_000 && parsedPorts.isSuccess
    val portSupporting = if (parsedPorts.isSuccess) {
        stringResource(Res.string.port_count_selected, parsedPorts.getOrNull()?.size ?: 0)
    } else {
        parsedPorts.exceptionOrNull()?.message ?: stringResource(Res.string.port_spec_help)
    }
    val counts = PortState.entries.associateWith { stateValue -> state.portResults.count { it.state == stateValue } }
    val filteredResults = state.portResults.filter { result ->
        when (filter) {
            PortResultFilter.ALL -> true
            PortResultFilter.OPEN -> result.state == PortState.OPEN
            PortResultFilter.CLOSED -> result.state == PortState.CLOSED
            PortResultFilter.FILTERED -> result.state == PortState.FILTERED
            PortResultFilter.ERROR -> result.state == PortState.ERROR
        }
    }

    ToolPage(
        title = stringResource(Res.string.tcp_port_scanner_3cafcd99),
        subtitle = stringResource(Res.string.scan_an_explicit_bounded_set_of_ports_and_6a7ecd65),
        eyebrow = stringResource(Res.string.bounded_scan_49e63641),
    ) {
        ToolAvailability(
            available = state.capabilities.tcpPortScan,
            label = stringResource(Res.string.tcp_port_scan_49f4d709),
            notes = state.capabilities.localizedNotes(),
        )
        SectionCard(
            title = stringResource(Res.string.scan_configuration_06182486),
            action = { CapabilityBadge(stringResource(Res.string.port_scan_9de67102), state.capabilities.tcpPortScan) },
        ) {
            ResponsiveFields(
                fields = *arrayOf(
                    { modifier ->
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it.trim() },
                            label = { Text(stringResource(Res.string.host_40f11ffc)) },
                            supportingText = { Text(stringResource(Res.string.ip_address_or_resolvable_host_349565b4)) },
                            isError = host.isBlank(),
                            modifier = modifier,
                            singleLine = true,
                        )
                    },
                    { modifier ->
                        OutlinedTextField(
                            value = timeout,
                            onValueChange = { timeout = it.filter(Char::isDigit) },
                            label = { Text(stringResource(Res.string.per_port_timeout_ms_1507407a)) },
                            supportingText = { Text(stringResource(Res.string.s_100_60_000_ms_5108191b)) },
                            isError = timeoutValue == null || timeoutValue !in 100..60_000,
                            modifier = modifier,
                            singleLine = true,
                        )
                    },
                ),
            )
            Text(stringResource(Res.string.port_presets), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PortPresets.all.forEach { preset ->
                    AssistChip(
                        onClick = { ports = preset.specification },
                        label = { Text(preset.localizedLabel()) },
                        leadingIcon = { Icon(Icons.Outlined.Radar, contentDescription = null) },
                    )
                }
            }
            OutlinedTextField(
                value = ports,
                onValueChange = { ports = it },
                label = { Text(stringResource(Res.string.ports_and_ranges_79bf3396)) },
                supportingText = { Text(portSupporting) },
                isError = parsedPorts.isFailure,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                stringResource(Res.string.port_spec_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.port_aliases),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onScan(host, ports, timeoutValue ?: state.settings.defaultTimeoutMillis) },
                enabled = !state.isBusy && state.capabilities.tcpPortScan && valid,
            ) {
                Icon(Icons.Outlined.Radar, contentDescription = null)
                Text(stringResource(Res.string.scan_explicit_ports_29f789c2))
            }
        }
        if (state.portResults.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.no_port_evidence_3fa6d9f0),
                message = stringResource(Res.string.run_a_scan_to_classify_open_closed_and_3adefede),
                icon = Icons.Outlined.Radar,
            )
        } else {
            val openLabel = stringResource(Res.string.port_state_open)
            val closedLabel = stringResource(Res.string.port_state_closed)
            val filteredLabel = stringResource(Res.string.port_state_filtered)
            val errorLabel = stringResource(Res.string.port_state_error)
            val summary = "$openLabel: ${counts[PortState.OPEN] ?: 0} · " +
                "$closedLabel: ${counts[PortState.CLOSED] ?: 0} · " +
                "$filteredLabel: ${counts[PortState.FILTERED] ?: 0} · " +
                "$errorLabel: ${counts[PortState.ERROR] ?: 0}"
            StatusBanner(
                title = stringResource(Res.string.port_summary),
                message = summary,
                tone = if ((counts[PortState.OPEN] ?: 0) > 0) StatusTone.SUCCESS else StatusTone.NEUTRAL,
            )
            SectionCard(stringResource(Res.string.port_results)) {
                ResponsiveActions {
                    PortResultFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.localizedLabel()) },
                        )
                    }
                }
                if (filteredResults.isEmpty()) {
                    EmptyState(
                        title = stringResource(Res.string.port_no_filtered_results),
                        message = stringResource(Res.string.each_card_includes_the_detected_service_hint_and_4c3d79b3),
                        icon = Icons.Outlined.Radar,
                    )
                } else {
                    ResponsiveCardGrid(
                        minimumCardWidth = 280.dp,
                        cards = *filteredResults.map { result ->
                            val card: @Composable (Modifier) -> Unit = { modifier ->
                                SectionCard(
                                    title = "${result.port} · ${result.serviceHint}",
                                    modifier = modifier,
                                    action = {
                                        Icon(
                                            imageVector = if (result.state == PortState.OPEN) {
                                                Icons.Outlined.CheckCircle
                                            } else {
                                                Icons.Outlined.ErrorOutline
                                            },
                                            contentDescription = null,
                                            tint = when (result.state) {
                                                PortState.OPEN -> MaterialTheme.colorScheme.primary
                                                PortState.FILTERED -> MaterialTheme.colorScheme.tertiary
                                                PortState.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
                                                PortState.ERROR -> MaterialTheme.colorScheme.error
                                            },
                                        )
                                    },
                                ) {
                                    KeyValueRow(stringResource(Res.string.state_e47deea5), result.state.localizedLabel())
                                    KeyValueRow(
                                        stringResource(Res.string.latency_fb2c7d2e),
                                        result.latencyMillis?.let { "$it ms" } ?: "—",
                                    )
                                    result.error?.takeIf(String::isNotBlank)?.let {
                                        KeyValueRow(stringResource(Res.string.evidence_10aecbc8), it)
                                    }
                                }
                            }
                            card
                        }.toTypedArray(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortPreset.localizedLabel(): String = when (id) {
    "recommended" -> stringResource(Res.string.port_preset_recommended)
    "web" -> stringResource(Res.string.port_preset_web)
    "iot" -> stringResource(Res.string.port_preset_iot)
    "remote" -> stringResource(Res.string.port_preset_remote)
    "databases" -> stringResource(Res.string.port_preset_databases)
    "messaging" -> stringResource(Res.string.port_preset_messaging)
    "mail" -> stringResource(Res.string.port_preset_mail)
    "well_known" -> stringResource(Res.string.port_preset_well_known)
    else -> id
}

@Composable
private fun PortResultFilter.localizedLabel(): String = when (this) {
    PortResultFilter.ALL -> stringResource(Res.string.port_filter_all)
    PortResultFilter.OPEN -> stringResource(Res.string.port_filter_open)
    PortResultFilter.CLOSED -> stringResource(Res.string.port_filter_closed)
    PortResultFilter.FILTERED -> stringResource(Res.string.port_filter_filtered)
    PortResultFilter.ERROR -> stringResource(Res.string.port_filter_error)
}

@Composable
private fun PortState.localizedLabel(): String = when (this) {
    PortState.OPEN -> stringResource(Res.string.port_state_open)
    PortState.CLOSED -> stringResource(Res.string.port_state_closed)
    PortState.FILTERED -> stringResource(Res.string.port_state_filtered)
    PortState.ERROR -> stringResource(Res.string.port_state_error)
}

@Composable
private fun SocketPanel(
    protocol: String,
    state: AppState,
    available: Boolean,
    onExchange: (String, Int, String, PayloadEncoding, Int) -> Unit,
) {
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf(if (protocol == "TCP") "1883" else "5683") }
    var timeout by remember { mutableStateOf(state.settings.defaultTimeoutMillis.toString()) }
    var payload by remember { mutableStateOf("hello") }
    var encoding by remember { mutableStateOf(PayloadEncoding.TEXT) }
    val portValue = port.toIntOrNull()
    val timeoutValue = timeout.toIntOrNull()
    val valid = host.isNotBlank() && portValue != null && portValue in 1..65_535 &&
        timeoutValue != null && timeoutValue in 100..60_000

    ToolPage(
        title = stringResource(Res.string.s_1_s_workbench_64581839, protocol),
        subtitle = stringResource(Res.string.send_text_hex_or_base64_bytes_and_inspect_288caffc),
        eyebrow = stringResource(Res.string.raw_transport_96188d30),
    ) {
        ToolAvailability(
            available = available,
            label = stringResource(Res.string.protocol_client_label, protocol),
            notes = state.capabilities.localizedNotes(),
        )
        val profileProtocol = if (protocol == "TCP") EndpointProtocol.TCP else EndpointProtocol.UDP
        val profiles = state.profiles.filter { it.protocol == profileProtocol }
        if (profiles.isNotEmpty()) {
            SectionCard(stringResource(Res.string.quick_profiles_fa2200c1)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.forEach { profile ->
                        AssistChip(
                            onClick = {
                                host = profile.hostOrUrl
                                profile.port?.let { port = it.toString() }
                            },
                            label = { Text(profile.name) },
                            leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                        )
                    }
                }
            }
        }
        ResponsivePanes(
            minimumPaneWidth = 430.dp,
            primary = { modifier ->
                SectionCard(
                    title = stringResource(Res.string.request_payload_7a3f481f),
                    modifier = modifier,
                    action = { CapabilityBadge("$protocol client", available) },
                ) {
                    EndpointFields(
                        host = host,
                        onHost = { host = it.trim() },
                        port = port,
                        onPort = { port = it },
                        timeout = timeout,
                        onTimeout = { timeout = it },
                    )
                    Text(stringResource(Res.string.common_service_ports), style = MaterialTheme.typography.labelLarge)
                    val commonPorts = if (protocol == "TCP") {
                        listOf(22, 80, 443, 502, 1883, 8883)
                    } else {
                        listOf(53, 123, 161, 5353, 5683, 5684)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        commonPorts.forEach { commonPort ->
                            AssistChip(
                                onClick = { port = commonPort.toString() },
                                label = { Text("$commonPort · ${serviceHint(commonPort)}") },
                                leadingIcon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                            )
                        }
                    }
                    EncodingSelector(selected = encoding, onSelect = { encoding = it })
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text(stringResource(Res.string.payload_with_encoding, encoding.name)) },
                        supportingText = { Text(stringResource(Res.string.the_decoded_byte_sequence_is_sent_as_entered_e1b50f10)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = responsiveTextAreaMinLines(7, 3),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                    if (state.templates.isNotEmpty()) {
                        Text(stringResource(Res.string.payload_templates), style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.templates.take(12).forEach { template ->
                                AssistChip(
                                    onClick = {
                                        payload = template.content
                                        encoding = PayloadEncoding.TEXT
                                    },
                                    label = { Text(template.name) },
                                )
                            }
                        }
                    }
                    ResponsiveActions {
                        OutlinedButton(onClick = { payload = "" }) {
                            Text(stringResource(Res.string.clear_payload))
                        }
                    }
                    Button(
                        onClick = { onExchange(host, portValue ?: 0, payload, encoding, timeoutValue ?: state.settings.defaultTimeoutMillis) },
                        enabled = !state.isBusy && available && valid,
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null)
                        Text(stringResource(Res.string.send_1_s_payload_8cc9785d, protocol))
                    }
                }
            },
            secondary = { modifier ->
                val result = state.socketResult?.takeIf { it.protocol.equals(protocol, ignoreCase = true) }
                if (result == null) {
                    SectionCard(stringResource(Res.string.response_c5fa179a), modifier = modifier) {
                        EmptyState(
                            title = stringResource(Res.string.no_response_captured_1e4c10ff),
                            message = stringResource(Res.string.send_a_request_to_inspect_text_hex_byte_cf35ce29),
                            icon = Icons.Outlined.Terminal,
                        )
                    }
                } else {
                    SectionCard(stringResource(Res.string.response_evidence_34a83ee3), modifier = modifier) {
                        StatusBanner(
                            title = if (result.error == null) stringResource(Res.string.exchange_completed_8851b058) else stringResource(Res.string.exchange_failed_f0b46c31),
                            message = result.error ?: stringResource(Res.string.the_bounded_transport_operation_returned_normally_c7dfdaf7),
                            tone = if (result.error == null) StatusTone.SUCCESS else StatusTone.ERROR,
                        )
                        KeyValueRow(stringResource(Res.string.endpoint_e51e5f22), result.endpoint, monospaced = true)
                        KeyValueRow(stringResource(Res.string.received_10a806ac), "${result.bytesReceived} bytes")
                        KeyValueRow(stringResource(Res.string.elapsed_b38c4647), "${result.elapsedMillis} ms")
                        KeyValueRow(stringResource(Res.string.truncated_a6cfb88e), if (result.truncated) stringResource(Res.string.yes_e309612b) else stringResource(Res.string.no_c30d5b7b))
                        if (result.responseText.isNotBlank()) {
                            Text(stringResource(Res.string.text_461919df), style = MaterialTheme.typography.labelLarge)
                            CodeSurface(result.responseText, maxHeight = 260.dp)
                        }
                        if (result.responseHex.isNotBlank()) {
                            Text("HEX", style = MaterialTheme.typography.labelLarge)
                            CodeSurface(result.responseHex, maxHeight = 220.dp)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SubnetPanel(
    state: AppState,
    onCalculate: (String) -> Unit,
) {
    var cidr by remember { mutableStateOf("192.168.1.0/24") }
    ToolPage(
        title = stringResource(Res.string.ipv4_subnet_calculator_2655b584),
        subtitle = stringResource(Res.string.calculate_network_boundaries_mask_and_usable_range_entirely_c649e2c6),
        eyebrow = stringResource(Res.string.offline_calculator_815b43bb),
    ) {
        StatusBanner(
            title = stringResource(Res.string.no_network_traffic_61054382),
            message = stringResource(Res.string.this_calculation_runs_locally_and_never_contacts_the_d20d8585),
            tone = StatusTone.INFO,
        )
        SectionCard(stringResource(Res.string.cidr_input_2c4ccf48)) {
            OutlinedTextField(
                value = cidr,
                onValueChange = { cidr = it.trim() },
                label = { Text(stringResource(Res.string.ipv4_cidr_label)) },
                supportingText = { Text(stringResource(Res.string.example_10_20_0_0_16_1ca0c4aa)) },
                isError = cidr.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(onClick = { onCalculate(cidr) }, enabled = cidr.isNotBlank()) {
                Icon(Icons.Outlined.Calculate, contentDescription = null)
                Text(stringResource(Res.string.calculate_subnet_9901734b))
            }
        }
        state.subnetResult?.let { result ->
            ResponsiveCardGrid(
                minimumCardWidth = 320.dp,
                cards = *arrayOf(
                    { modifier ->
                        SectionCard(stringResource(Res.string.network_boundaries_9cbbc54f), modifier) {
                            KeyValueRow(stringResource(Res.string.network_e3a9ad8d), result.networkAddress, monospaced = true)
                            KeyValueRow(stringResource(Res.string.broadcast_f6251cfc), result.broadcastAddress, monospaced = true)
                            KeyValueRow(stringResource(Res.string.mask_d5fe0af0), result.subnetMask, monospaced = true)
                            KeyValueRow(stringResource(Res.string.prefix_7994e5af), "/${result.prefixLength}")
                        }
                    },
                    { modifier ->
                        SectionCard(stringResource(Res.string.usable_range_e2c13389), modifier) {
                            KeyValueRow(stringResource(Res.string.first_host_cdb5cf09), result.firstHost, monospaced = true)
                            KeyValueRow(stringResource(Res.string.last_host_0a4648ab), result.lastHost, monospaced = true)
                            KeyValueRow(stringResource(Res.string.total_addresses_a351ef1c), result.totalAddresses.toString())
                            KeyValueRow(stringResource(Res.string.usable_addresses_43055309), result.usableAddresses.toString())
                        }
                    },
                ),
            )
        } ?: EmptyState(
            title = stringResource(Res.string.no_subnet_result_ec37b26a),
            message = stringResource(Res.string.enter_a_valid_ipv4_cidr_to_generate_network_61ac227b),
            icon = Icons.Outlined.Calculate,
        )
    }
}

@Composable
private fun DnsPanel(
    state: AppState,
    onResolve: (String) -> Unit,
) {
    var host by remember { mutableStateOf("example.com") }
    ToolPage(
        title = stringResource(Res.string.dns_lookup_8108d8f7),
        subtitle = stringResource(Res.string.resolve_a_host_through_the_platform_resolver_and_2f13a3a9),
        eyebrow = stringResource(Res.string.name_resolution_7f2d3348),
    ) {
        ToolAvailability(
            available = state.capabilities.dnsLookup,
            label = stringResource(Res.string.dns_lookup_8108d8f7),
            notes = state.capabilities.localizedNotes(),
        )
        SectionCard(
            title = stringResource(Res.string.dns_query_88294b1c),
            action = { CapabilityBadge("DNS", state.capabilities.dnsLookup) },
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text(stringResource(Res.string.host_name_216bab63)) },
                supportingText = { Text(stringResource(Res.string.example_gateway_local_or_example_com_f5e8c47c)) },
                isError = host.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { onResolve(host) },
                enabled = !state.isBusy && state.capabilities.dnsLookup && host.isNotBlank(),
            ) {
                Icon(Icons.Outlined.Dns, contentDescription = null)
                Text(stringResource(Res.string.resolve_host_a4f682bf))
            }
        }
        if (state.dnsResults.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.no_resolved_addresses_5b807185),
                message = stringResource(Res.string.run_a_dns_query_to_list_ipv4_and_ab388b21),
                icon = Icons.Outlined.Dns,
            )
        } else {
            StatusBanner(
                title = stringResource(Res.string.s_1_s_addresses_resolved_3e01f72a, state.dnsResults.size),
                message = stringResource(Res.string.the_order_is_preserved_from_the_platform_resolver_f9fb5eb9),
                tone = StatusTone.SUCCESS,
            )
            SectionCard(stringResource(Res.string.resolved_addresses_2cc939de)) {
                CodeSurface(state.dnsResults.joinToString("\n"), maxHeight = 300.dp)
            }
        }
    }
}

@Composable
private fun ToolPage(
    title: String,
    subtitle: String,
    eyebrow: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item { PageHeader(title = title, subtitle = subtitle, eyebrow = eyebrow) }
        item { Column(verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()), content = content) }
    }
}

@Composable
private fun EndpointFields(
    host: String,
    onHost: (String) -> Unit,
    port: String,
    onPort: (String) -> Unit,
    timeout: String,
    onTimeout: (String) -> Unit,
    showPort: Boolean = true,
) {
    val portValue = port.toIntOrNull()
    val timeoutValue = timeout.toIntOrNull()
    val hostField: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = host,
            onValueChange = onHost,
            label = { Text(stringResource(Res.string.host_40f11ffc)) },
            supportingText = { Text(stringResource(Res.string.ip_or_resolvable_name_b97810cb)) },
            isError = host.isBlank(),
            modifier = fieldModifier,
            singleLine = true,
        )
    }
    val portField: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = port,
            onValueChange = { onPort(it.filter(Char::isDigit)) },
            label = { Text(stringResource(Res.string.port_866d53af)) },
            supportingText = { Text("1–65535") },
            isError = portValue == null || portValue !in 1..65_535,
            modifier = fieldModifier,
            singleLine = true,
        )
    }
    val timeoutField: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            value = timeout,
            onValueChange = { onTimeout(it.filter(Char::isDigit)) },
            label = { Text(stringResource(Res.string.timeout_ms_3813d458)) },
            supportingText = { Text(stringResource(Res.string.s_100_60_000_ms_5108191b)) },
            isError = timeoutValue == null || timeoutValue !in 100..60_000,
            modifier = fieldModifier,
            singleLine = true,
        )
    }
    if (showPort) {
        ResponsiveFields(fields = *arrayOf(hostField, portField, timeoutField))
    } else {
        ResponsiveFields(fields = *arrayOf(hostField, timeoutField))
    }
}

@Composable
private fun EncodingSelector(
    selected: PayloadEncoding,
    onSelect: (PayloadEncoding) -> Unit,
) {
    ResponsiveActions {
        PayloadEncoding.entries.forEach { encoding ->
            FilterChip(
                selected = selected == encoding,
                onClick = { onSelect(encoding) },
                label = { Text(encoding.name) },
            )
        }
    }
}

@Composable
private fun ToolAvailability(
    available: Boolean,
    label: String,
    notes: String,
) {
    StatusBanner(
        title = if (available) stringResource(Res.string.s_1_s_is_available_e84eb1fc, label) else stringResource(Res.string.s_1_s_is_unavailable_here_573cda53, label),
        message = if (available) {
            stringResource(Res.string.timeout_response_size_and_concurrency_limits_remain_enforced_be3d5ccf)
        } else {
            notes
        },
        tone = if (available) StatusTone.INFO else StatusTone.WARNING,
    )
}

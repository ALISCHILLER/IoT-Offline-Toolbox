package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.ui.components.CapabilityBadge
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.MetricCard
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveCardGrid
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.ToolActionCard
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.layout.responsiveLayout
import com.msa.iotofflinetoolbox.ui.theme.toolboxUiTokens
import com.msa.iotofflinetoolbox.ui.theme.icon
import com.msa.iotofflinetoolbox.ui.localization.localizedNotes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(state: AppState, onNavigate: (ToolScreen) -> Unit) {
    val layout = responsiveLayout()
    val tokens = toolboxUiTokens
    val availableCapabilities = listOf(
        state.capabilities.ping,
        state.capabilities.tcpPortScan,
        state.capabilities.hostDiscovery,
        state.capabilities.ssdpDiscovery,
        state.capabilities.mdnsDiscovery,
        state.capabilities.dnsLookup,
        state.capabilities.tcpClient,
        state.capabilities.udpClient,
        state.capabilities.httpClient,
        state.capabilities.webSocketClient,
        state.capabilities.mqttWebSocketClient,
        state.capabilities.coapClient,
    ).count { it }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            DashboardHero(
                platformName = state.capabilities.platformName,
                availableCapabilities = availableCapabilities,
                onStart = { onNavigate(ToolScreen.DISCOVERY) },
            )
        }

        item {
            PageHeader(
                title = stringResource(Res.string.workspace_overview_968061ed),
                subtitle = stringResource(Res.string.everything_required_to_inspect_test_and_document_local_6eeb71a5),
                eyebrow = stringResource(Res.string.local_first_toolkit_77935fbe),
            )
        }

        item {
            ResponsiveCardGrid(
                minimumCardWidth = if (layout.isLowHeight) 190.dp else 220.dp,
                cards = *arrayOf(
                    { modifier ->
                        MetricCard(
                            label = stringResource(Res.string.platform_3f26cfed),
                            value = state.capabilities.platformName,
                            supporting = stringResource(Res.string.current_runtime_7c1119a3),
                            icon = Icons.Outlined.Laptop,
                            modifier = modifier,
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    },
                    { modifier ->
                        MetricCard(
                            label = stringResource(Res.string.saved_devices_2e67e824),
                            value = state.savedDevices.size.toString(),
                            supporting = stringResource(Res.string.offline_inventory_862d0ebf),
                            icon = Icons.Outlined.DevicesOther,
                            modifier = modifier,
                        )
                    },
                    { modifier ->
                        MetricCard(
                            label = stringResource(Res.string.profiles_802cd16c),
                            value = state.profiles.size.toString(),
                            supporting = stringResource(Res.string.reusable_endpoints_3c072172),
                            icon = Icons.Outlined.Backup,
                            modifier = modifier,
                            accent = MaterialTheme.colorScheme.tertiary,
                        )
                    },
                    { modifier ->
                        MetricCard(
                            label = stringResource(Res.string.history_07205a76),
                            value = state.history.size.toString(),
                            supporting = stringResource(Res.string.bounded_local_evidence_c5d12fd2),
                            icon = Icons.Outlined.History,
                            modifier = modifier,
                            accent = tokens.info,
                        )
                    },
                ),
            )
        }

        item {
            SectionCard(stringResource(Res.string.start_a_workflow_2431cc32)) {
                ResponsiveCardGrid(
                    minimumCardWidth = if (layout.isLowHeight) 250.dp else 280.dp,
                    cards = *arrayOf(
                        { modifier ->
                            ToolActionCard(
                                title = stringResource(Res.string.discover_devices_349cb025),
                                description = stringResource(Res.string.find_hosts_and_services_with_lan_ssdp_and_5f072ad0),
                                icon = ToolScreen.DISCOVERY.icon,
                                onClick = { onNavigate(ToolScreen.DISCOVERY) },
                                modifier = modifier,
                                emphasized = true,
                            )
                        },
                        { modifier ->
                            ToolActionCard(
                                title = stringResource(Res.string.network_diagnostics_c8962120),
                                description = stringResource(Res.string.check_reachability_ports_dns_sockets_and_cidr_details_656e58fb),
                                icon = ToolScreen.NETWORK_TOOLS.icon,
                                onClick = { onNavigate(ToolScreen.NETWORK_TOOLS) },
                                modifier = modifier,
                            )
                        },
                        { modifier ->
                            ToolActionCard(
                                title = stringResource(Res.string.protocol_workbench_f9751753),
                                description = stringResource(Res.string.use_http_websocket_mqtt_and_coap_with_bounded_bbc5585a),
                                icon = ToolScreen.REALTIME.icon,
                                onClick = { onNavigate(ToolScreen.REALTIME) },
                                modifier = modifier,
                            )
                        },
                        { modifier ->
                            ToolActionCard(
                                title = stringResource(Res.string.payload_laboratory_fe41667a),
                                description = stringResource(Res.string.transform_validate_encode_and_reuse_payload_templates_c0434498),
                                icon = ToolScreen.PAYLOAD_TOOLS.icon,
                                onClick = { onNavigate(ToolScreen.PAYLOAD_TOOLS) },
                                modifier = modifier,
                            )
                        },
                    ),
                )
            }
        }

        item {
            SectionCard(stringResource(Res.string.runtime_capabilities_13c7e227)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 7.dp else 9.dp),
                    verticalArrangement = Arrangement.spacedBy(if (layout.isLowHeight) 7.dp else 9.dp),
                ) {
                    CapabilityBadge(stringResource(Res.string.ping_d4921a89), state.capabilities.ping)
                    CapabilityBadge(stringResource(Res.string.tcp_scan_d7a7938f), state.capabilities.tcpPortScan)
                    CapabilityBadge(stringResource(Res.string.lan_96c42beb), state.capabilities.hostDiscovery)
                    CapabilityBadge("SSDP", state.capabilities.ssdpDiscovery)
                    CapabilityBadge("mDNS", state.capabilities.mdnsDiscovery)
                    CapabilityBadge("DNS", state.capabilities.dnsLookup)
                    CapabilityBadge("TCP", state.capabilities.tcpClient)
                    CapabilityBadge("UDP", state.capabilities.udpClient)
                    CapabilityBadge("HTTP", state.capabilities.httpClient)
                    CapabilityBadge("WebSocket", state.capabilities.webSocketClient)
                    CapabilityBadge("MQTT WS", state.capabilities.mqttWebSocketClient)
                    CapabilityBadge("CoAP", state.capabilities.coapClient)
                }
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_of_12_capabilities_available_dd3be761, availableCapabilities),
                    message = state.capabilities.localizedNotes(),
                    tone = if (availableCapabilities >= 10) StatusTone.SUCCESS else StatusTone.INFO,
                )
            }
        }

        item {
            SectionCard(
                title = stringResource(Res.string.recent_activity_73479fd6),
                action = if (state.history.isNotEmpty()) {
                    { ActivityCount(state.history.size) }
                } else {
                    null
                },
            ) {
                if (state.history.isEmpty()) {
                    EmptyState(
                        title = stringResource(Res.string.no_activity_yet_9b360fa6),
                        message = stringResource(Res.string.run_a_discovery_or_protocol_request_and_the_5d1a26f6),
                        icon = Icons.Outlined.ReceiptLong,
                    )
                } else {
                    state.history.take(5).forEachIndexed { index, entry ->
                        ActivityRow(
                            protocol = entry.protocol.name,
                            target = entry.target,
                            summary = entry.summary,
                            successful = entry.successful,
                        )
                        if (index < state.history.take(5).lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }

        item {
            StatusBanner(
                title = stringResource(Res.string.private_by_design_c644383b),
                message = stringResource(Res.string.profiles_templates_history_and_logs_stay_on_this_c5f52ef9),
                tone = StatusTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun DashboardHero(
    platformName: String,
    availableCapabilities: Int,
    onStart: () -> Unit,
) {
    val layout = responsiveLayout()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints {
            val stack = maxWidth < 720.dp
            if (stack) {
                Column(
                    modifier = Modifier.padding(if (layout.isLowHeight) 18.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    HeroCopy(platformName, availableCapabilities)
                    HeroStartButton(onStart, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.padding(if (layout.isLowHeight) 20.dp else 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { HeroCopy(platformName, availableCapabilities) }
                    HeroStartButton(onStart, Modifier)
                }
            }
        }
    }
}

@Composable
private fun HeroCopy(platformName: String, availableCapabilities: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.your_local_iot_command_center_1af647d8),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(Res.string.inspect_the_network_keep_the_evidence_5e81ecec),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(Res.string.s_1_s_runtime_2_s_12_network_capabilities_ce58bd09, platformName, availableCapabilities),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun HeroStartButton(onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ToolScreen.DISCOVERY.icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(stringResource(Res.string.start_discovery_7eb65cf9), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActivityCount(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActivityRow(
    protocol: String,
    target: String,
    summary: String,
    successful: Boolean,
) {
    val tokens = toolboxUiTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (successful) tokens.successContainer else MaterialTheme.colorScheme.errorContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (successful) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (successful) tokens.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(protocol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    target,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

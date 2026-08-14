package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.msa.iotofflinetoolbox.core.model.MdnsService
import com.msa.iotofflinetoolbox.core.model.SsdpDevice
import com.msa.iotofflinetoolbox.ui.components.CapabilityBadge
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.InfoPill
import com.msa.iotofflinetoolbox.ui.components.KeyValueRow
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveCardGrid
import com.msa.iotofflinetoolbox.ui.components.ResponsiveFields
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.ToolTabRow
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.localization.localizedNotes

@Composable
fun DiscoveryScreen(
    state: AppState,
    onDiscover: (String, Int) -> Unit,
    onDiscoverSsdp: (Int, Int) -> Unit,
    onDiscoverMdns: (Int, Int) -> Unit,
    onSave: (String, String) -> Unit,
    onSaveSsdp: (SsdpDevice) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ToolTabRow(
            selectedIndex = tab,
            labels = listOf(stringResource(Res.string.lan_probe_63ebbdd3), "UPnP / SSDP", "Bonjour / mDNS"),
            onSelect = { tab = it },
        )
        when (tab) {
            0 -> LanDiscovery(state, onDiscover, onSave)
            1 -> SsdpDiscovery(state, onDiscoverSsdp, onSaveSsdp)
            else -> MdnsDiscovery(state, onDiscoverMdns, onSave)
        }
    }
}

@Composable
private fun LanDiscovery(
    state: AppState,
    onDiscover: (String, Int) -> Unit,
    onSave: (String, String) -> Unit,
) {
    var cidr by remember { mutableStateOf("192.168.1.0/24") }
    var timeout by remember(state.settings.defaultTimeoutMillis) {
        mutableStateOf(state.settings.defaultTimeoutMillis.toString())
    }
    val timeoutValue = timeout.toIntOrNull()
    val valid = cidr.isNotBlank() && timeoutValue != null && timeoutValue in 100..60_000

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.lan_discovery_973962e3),
                subtitle = stringResource(Res.string.probe_a_bounded_ipv4_subnet_review_reachable_hosts_4da67f34),
                eyebrow = stringResource(Res.string.local_network_ac9f0a5d),
            )
        }
        item {
            DiscoveryCapability(
                available = state.capabilities.hostDiscovery,
                label = stringResource(Res.string.raw_lan_probing_6025ed2b),
                unavailableMessage = state.capabilities.localizedNotes(),
            )
        }
        item {
            SectionCard(
                title = stringResource(Res.string.scan_configuration_06182486),
                action = { CapabilityBadge(stringResource(Res.string.host_discovery_b939d3e6), state.capabilities.hostDiscovery) },
            ) {
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = cidr,
                                onValueChange = { cidr = it.trim() },
                                label = { Text(stringResource(Res.string.cidr_range_1ce2b54e)) },
                                supportingText = { Text(stringResource(Res.string.example_192_168_1_0_24_c74aebec)) },
                                isError = cidr.isBlank(),
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            OutlinedTextField(
                                value = timeout,
                                onValueChange = { timeout = it.filter(Char::isDigit) },
                                label = { Text(stringResource(Res.string.timeout_ms_3813d458)) },
                                supportingText = { Text(stringResource(Res.string.s_100_60_000_ms_5108191b)) },
                                isError = timeoutValue == null || timeoutValue !in 100..60_000,
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                    ),
                )
                Button(
                    onClick = { onDiscover(cidr, timeoutValue ?: state.settings.defaultTimeoutMillis) },
                    enabled = !state.isBusy && state.capabilities.hostDiscovery && valid,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Text(stringResource(Res.string.start_bounded_scan_2053cf3e))
                }
            }
        }
        if (state.discoveredDevices.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(Res.string.no_discovered_devices_yet_0142c3d3),
                    message = stringResource(Res.string.choose_the_subnet_used_by_your_current_lan_a05d97e5),
                    icon = Icons.Outlined.NetworkCheck,
                )
            }
        } else {
            item {
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_reachable_hosts_f560ad45, state.discoveredDevices.size),
                    message = stringResource(Res.string.results_are_local_and_bounded_by_the_configured_92a27908),
                    tone = StatusTone.SUCCESS,
                )
            }
            item {
                ResponsiveCardGrid(
                    minimumCardWidth = 320.dp,
                    cards = *state.discoveredDevices.map { device ->
                        val card: @Composable (Modifier) -> Unit = { modifier ->
                            SectionCard(
                                title = device.resolvedName ?: device.host,
                                modifier = modifier,
                                action = {
                                    OutlinedButton(onClick = { onSave(device.host, device.resolvedName ?: device.host) }) {
                                        Icon(Icons.Outlined.Save, contentDescription = null)
                                        Text(stringResource(Res.string.save_605f39dc))
                                    }
                                },
                            ) {
                                KeyValueRow(stringResource(Res.string.host_40f11ffc), device.host, monospaced = true)
                                KeyValueRow(
                                    stringResource(Res.string.latency_fb2c7d2e),
                                    device.latencyMillis?.let { "$it ms" } ?: stringResource(Res.string.not_reported_1e9039d3),
                                )
                            }
                        }
                        card
                    }.toTypedArray(),
                )
            }
        }
    }
}

@Composable
private fun SsdpDiscovery(
    state: AppState,
    onDiscover: (Int, Int) -> Unit,
    onSave: (SsdpDevice) -> Unit,
) {
    var timeout by remember { mutableStateOf("3000") }
    var maxResults by remember { mutableStateOf("100") }
    val timeoutValue = timeout.toIntOrNull()
    val maxResultsValue = maxResults.toIntOrNull()
    val valid = timeoutValue != null && timeoutValue in 100..60_000 &&
        maxResultsValue != null && maxResultsValue in 1..500

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.upnp_ssdp_discovery_a02f8500),
                subtitle = stringResource(Res.string.send_a_bounded_m_search_request_and_inspect_bf20d5f3),
                eyebrow = stringResource(Res.string.multicast_discovery_08cdf832),
            )
        }
        item {
            DiscoveryCapability(
                available = state.capabilities.ssdpDiscovery,
                label = "SSDP / UPnP",
                unavailableMessage = state.capabilities.localizedNotes(),
            )
        }
        item {
            SectionCard(
                title = stringResource(Res.string.discovery_window_3f39680f),
                action = { CapabilityBadge("SSDP", state.capabilities.ssdpDiscovery) },
            ) {
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = timeout,
                                onValueChange = { timeout = it.filter(Char::isDigit) },
                                label = { Text(stringResource(Res.string.timeout_ms_3813d458)) },
                                supportingText = { Text(stringResource(Res.string.s_100_60_000_ms_5108191b)) },
                                isError = timeoutValue == null || timeoutValue !in 100..60_000,
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            OutlinedTextField(
                                value = maxResults,
                                onValueChange = { maxResults = it.filter(Char::isDigit) },
                                label = { Text(stringResource(Res.string.maximum_results_4d43a143)) },
                                supportingText = { Text(stringResource(Res.string.s_1_500_responses_3f5d6a8a)) },
                                isError = maxResultsValue == null || maxResultsValue !in 1..500,
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                    ),
                )
                Button(
                    onClick = { onDiscover(timeoutValue ?: 3_000, maxResultsValue ?: 100) },
                    enabled = !state.isBusy && state.capabilities.ssdpDiscovery && valid,
                ) {
                    Icon(Icons.Outlined.WifiTethering, contentDescription = null)
                    Text(stringResource(Res.string.start_ssdp_discovery_2c0ee2ee))
                }
            }
        }
        if (state.ssdpDevices.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(Res.string.no_ssdp_advertisements_ae95f947),
                    message = stringResource(Res.string.keep_this_device_on_the_same_lan_as_2cb7779f),
                    icon = Icons.Outlined.Router,
                )
            }
        } else {
            item {
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_ssdp_responses_8f4c21c7, state.ssdpDevices.size),
                    message = stringResource(Res.string.review_the_advertised_location_before_saving_a_device_bdeba41e),
                    tone = StatusTone.SUCCESS,
                )
            }
            item {
                ResponsiveCardGrid(
                    minimumCardWidth = 360.dp,
                    cards = *state.ssdpDevices.map { device ->
                        val card: @Composable (Modifier) -> Unit = { modifier ->
                            SsdpResultCard(device, modifier, onSave)
                        }
                        card
                    }.toTypedArray(),
                )
            }
        }
    }
}

@Composable
private fun SsdpResultCard(
    device: SsdpDevice,
    modifier: Modifier,
    onSave: (SsdpDevice) -> Unit,
) {
    SectionCard(
        title = device.server ?: device.searchTarget ?: stringResource(Res.string.upnp_device_56211c3d),
        modifier = modifier,
        action = {
            OutlinedButton(onClick = { onSave(device) }) {
                Icon(Icons.Outlined.AddCircleOutline, contentDescription = null)
                Text(stringResource(Res.string.save_605f39dc))
            }
        },
    ) {
        KeyValueRow(
            label = stringResource(Res.string.location_1865c7df),
            value = device.location.ifBlank { device.remoteAddress.orEmpty().ifBlank { "—" } },
            monospaced = true,
        )
        KeyValueRow(stringResource(Res.string.usn_43710abc), device.usn.ifBlank { "—" }, monospaced = true)
        device.remoteAddress?.takeIf(String::isNotBlank)?.let {
            KeyValueRow(stringResource(Res.string.responder_30ac9907), it, monospaced = true)
        }
        device.searchTarget?.takeIf(String::isNotBlank)?.let {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoPill(
                    label = it,
                    icon = Icons.Outlined.DevicesOther,
                )
            }
        }
    }
}

@Composable
private fun MdnsDiscovery(
    state: AppState,
    onDiscover: (Int, Int) -> Unit,
    onSave: (String, String) -> Unit,
) {
    var timeout by remember { mutableStateOf("6000") }
    var maxResults by remember { mutableStateOf("100") }
    val timeoutValue = timeout.toIntOrNull()
    val maxResultsValue = maxResults.toIntOrNull()
    val valid = timeoutValue != null && timeoutValue in 100..60_000 &&
        maxResultsValue != null && maxResultsValue in 1..500

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.bonjour_mdns_discovery_7a2422d2),
                subtitle = stringResource(Res.string.resolve_dns_sd_ptr_srv_txt_a_and_7c4757b7),
                eyebrow = stringResource(Res.string.service_discovery_48ad4869),
            )
        }
        item {
            DiscoveryCapability(
                available = state.capabilities.mdnsDiscovery,
                label = "Bonjour / DNS-SD",
                unavailableMessage = state.capabilities.localizedNotes(),
            )
        }
        item {
            SectionCard(
                title = stringResource(Res.string.discovery_window_3f39680f),
                action = { CapabilityBadge("mDNS", state.capabilities.mdnsDiscovery) },
            ) {
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = timeout,
                                onValueChange = { timeout = it.filter(Char::isDigit) },
                                label = { Text(stringResource(Res.string.timeout_ms_3813d458)) },
                                supportingText = { Text(stringResource(Res.string.s_100_60_000_ms_5108191b)) },
                                isError = timeoutValue == null || timeoutValue !in 100..60_000,
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            OutlinedTextField(
                                value = maxResults,
                                onValueChange = { maxResults = it.filter(Char::isDigit) },
                                label = { Text(stringResource(Res.string.maximum_services_8fd44111)) },
                                supportingText = { Text(stringResource(Res.string.s_1_500_services_291830c3)) },
                                isError = maxResultsValue == null || maxResultsValue !in 1..500,
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                    ),
                )
                Button(
                    onClick = { onDiscover(timeoutValue ?: 6_000, maxResultsValue ?: 100) },
                    enabled = !state.isBusy && state.capabilities.mdnsDiscovery && valid,
                ) {
                    Icon(Icons.Outlined.Dns, contentDescription = null)
                    Text(stringResource(Res.string.start_service_discovery_a346fd47))
                }
            }
        }
        if (state.mdnsServices.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(Res.string.no_bonjour_services_a194e969),
                    message = stringResource(Res.string.run_discovery_on_the_same_lan_as_printers_b81c57a1),
                    icon = Icons.Outlined.Dns,
                )
            }
        } else {
            item {
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_services_resolved_9e3bcd8f, state.mdnsServices.size),
                    message = stringResource(Res.string.host_port_addresses_and_txt_metadata_are_grouped_9d4c0d54),
                    tone = StatusTone.SUCCESS,
                )
            }
            item {
                ResponsiveCardGrid(
                    minimumCardWidth = 360.dp,
                    cards = *state.mdnsServices.map { service ->
                        val card: @Composable (Modifier) -> Unit = { modifier ->
                            MdnsResultCard(service, modifier, onSave)
                        }
                        card
                    }.toTypedArray(),
                )
            }
        }
    }
}

@Composable
private fun MdnsResultCard(
    service: MdnsService,
    modifier: Modifier,
    onSave: (String, String) -> Unit,
) {
    val host = service.host
    SectionCard(
        title = service.instanceName,
        modifier = modifier,
        action = if (host != null) {
            {
                OutlinedButton(onClick = { onSave(host, service.instanceName) }) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text(stringResource(Res.string.save_605f39dc))
                }
            }
        } else {
            null
        },
    ) {
        KeyValueRow(stringResource(Res.string.service_387cf887), service.serviceType, monospaced = true)
        service.host?.let { host ->
            KeyValueRow(
                label = stringResource(Res.string.endpoint_e51e5f22),
                value = "$host${service.port?.let { ":$it" }.orEmpty()}",
                monospaced = true,
            )
        }
        if (service.addresses.isNotEmpty()) {
            KeyValueRow(stringResource(Res.string.addresses_82881866), service.addresses.joinToString(), monospaced = true)
        }
        if (service.txt.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.txt_metadata_0bb3273e),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = service.txt.entries.joinToString("\n") { "${it.key}=${it.value}" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DiscoveryCapability(
    available: Boolean,
    label: String,
    unavailableMessage: String,
) {
    StatusBanner(
        title = if (available) {
            stringResource(Res.string.s_1_s_is_available_e84eb1fc, label)
        } else {
            stringResource(Res.string.s_1_s_is_limited_on_this_platform_16b859e9, label)
        },
        message = if (available) {
            stringResource(Res.string.the_operation_stays_bounded_by_timeout_and_result_28c121e0)
        } else {
            unavailableMessage
        },
        tone = if (available) StatusTone.INFO else StatusTone.WARNING,
    )
}

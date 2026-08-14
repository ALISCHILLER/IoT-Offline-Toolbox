package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.ui.components.CapabilityBadge
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.ResponsiveCardGrid
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.localization.localizedNotes

@Composable
fun GuideScreen(capabilities: PlatformCapabilities) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.operator_guide_71f42ee7),
                subtitle = stringResource(Res.string.a_practical_runbook_for_safe_discovery_bounded_diagnostics_31b3b8a2),
                eyebrow = stringResource(Res.string.safe_operations_16b2d88a),
            )
        }
        item {
            StatusBanner(
                title = stringResource(Res.string.work_only_on_authorized_networks_a6dd55c8),
                message = stringResource(Res.string.start_with_the_least_invasive_tool_keep_ranges_b829e605),
                tone = StatusTone.WARNING,
            )
        }
        item {
            SectionCard(stringResource(Res.string.recommended_workflow_64ecc6e1)) {
                ResponsiveCardGrid(
                    minimumCardWidth = 260.dp,
                    cards = *arrayOf(
                        { modifier -> GuideStep("1", Icons.Outlined.NetworkCheck, stringResource(Res.string.join_the_trusted_lan_2981d486), stringResource(Res.string.use_the_same_authorized_local_network_as_the_6c2cc3f4), modifier) },
                        { modifier -> GuideStep("2", Icons.Outlined.Search, stringResource(Res.string.discover_first_7a92f079), stringResource(Res.string.identify_stable_hosts_and_services_before_opening_protocol_36edacd4), modifier) },
                        { modifier -> GuideStep("3", Icons.Outlined.DevicesOther, stringResource(Res.string.save_reusable_context_dfb052d6), stringResource(Res.string.store_non_secret_endpoints_as_inventory_items_or_5e0b1381), modifier) },
                        { modifier -> GuideStep("4", Icons.Outlined.Rule, stringResource(Res.string.keep_bounds_explicit_7d794c60), stringResource(Res.string.review_timeout_host_port_message_and_response_limits_e7cd432d), modifier) },
                        { modifier -> GuideStep("5", Icons.Outlined.Backup, stringResource(Res.string.preserve_evidence_e3d55ba5), stringResource(Res.string.create_a_redacted_backup_before_clearing_or_moving_246400a8), modifier) },
                    ),
                )
            }
        }
        item {
            SectionCard(
                title = stringResource(Res.string.runtime_capability_map_13ea6258),
                action = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(capabilities.platformName, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                    }
                },
            ) {
                ResponsiveActions {
                    CapabilityBadge(stringResource(Res.string.reachability_7029873d), capabilities.ping)
                    CapabilityBadge(stringResource(Res.string.tcp_scan_6485d99d), capabilities.tcpPortScan)
                    CapabilityBadge(stringResource(Res.string.lan_discovery_32af3cf5), capabilities.hostDiscovery)
                    CapabilityBadge("SSDP / UPnP", capabilities.ssdpDiscovery)
                    CapabilityBadge("Bonjour / mDNS", capabilities.mdnsDiscovery)
                    CapabilityBadge("DNS", capabilities.dnsLookup)
                    CapabilityBadge("TCP", capabilities.tcpClient)
                    CapabilityBadge("UDP", capabilities.udpClient)
                    CapabilityBadge("HTTP", capabilities.httpClient)
                    CapabilityBadge("WebSocket", capabilities.webSocketClient)
                    CapabilityBadge("MQTT / WS", capabilities.mqttWebSocketClient)
                    CapabilityBadge("CoAP", capabilities.coapClient)
                }
                StatusBanner(
                    title = stringResource(Res.string.platform_note_00f39153),
                    message = capabilities.localizedNotes(),
                    tone = StatusTone.NEUTRAL,
                )
            }
        }
        item {
            Text(
                text = stringResource(Res.string.operational_playbooks_5ee5757b),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            ResponsiveCardGrid(
                minimumCardWidth = 360.dp,
                cards = *arrayOf(
                    { modifier ->
                        GuideTopicCard(
                            title = stringResource(Res.string.discovery_strategy_4ddf61e7),
                            icon = Icons.Outlined.Search,
                            points = listOf(
                                stringResource(Res.string.start_with_a_small_cidr_such_as_192_8b76301a),
                                stringResource(Res.string.use_ssdp_for_upnp_equipment_and_keep_location_486011d6),
                                stringResource(Res.string.treat_mdns_txt_values_as_untrusted_metadata_5e522e85),
                            ),
                            modifier = modifier,
                        )
                    },
                    { modifier ->
                        GuideTopicCard(
                            title = stringResource(Res.string.protocol_sessions_65b274cd),
                            icon = Icons.Outlined.CloudSync,
                            points = listOf(
                                stringResource(Res.string.prefer_https_and_wss_for_any_non_local_e0291130),
                                stringResource(Res.string.mqtt_qos_1_succeeds_only_after_acknowledgement_evidence_47bb9e1d),
                                stringResource(Res.string.tcp_udp_and_coap_should_target_authorized_endpoints_63d46f0c),
                            ),
                            modifier = modifier,
                        )
                    },
                    { modifier ->
                        GuideTopicCard(
                            title = stringResource(Res.string.payload_handling_1ffe71f1),
                            icon = Icons.Outlined.Code,
                            points = listOf(
                                stringResource(Res.string.validate_hex_base64_before_sending_decoded_bytes_34a5c5ed),
                                stringResource(Res.string.use_variables_and_templates_for_repeatable_tests_not_39a8314b),
                                stringResource(Res.string.response_and_history_previews_are_intentionally_bounded_10fd65a5),
                            ),
                            modifier = modifier,
                        )
                    },
                    { modifier ->
                        GuideTopicCard(
                            title = stringResource(Res.string.privacy_and_backups_013ff9b6),
                            icon = Icons.Outlined.Lock,
                            points = listOf(
                                stringResource(Res.string.passwords_remain_memory_only_where_supported_5b489589),
                                stringResource(Res.string.backups_are_schema_versioned_and_inspected_before_import_0944f0fb),
                                stringResource(Res.string.use_merge_by_default_replace_is_deliberately_destructive_685d2350),
                            ),
                            modifier = modifier,
                        )
                    },
                ),
            )
        }
        item {
            SectionCard(stringResource(Res.string.release_verification_5575fac4)) {
                StatusBanner(
                    title = stringResource(Res.string.a_source_audit_is_not_a_runtime_test_e214e8b9),
                    message = stringResource(Res.string.a_signed_release_still_requires_the_full_gradle_1296c004),
                    tone = StatusTone.INFO,
                )
                GuideBullet(Icons.Outlined.VerifiedUser, stringResource(Res.string.verify_local_network_permissions_and_multicast_on_physical_5fed09fd))
                GuideBullet(Icons.Outlined.Security, stringResource(Res.string.review_cleartext_policy_signing_r8_and_release_configuration_b613f16e))
            }
        }
    }
}

@Composable
private fun GuideStep(
    number: String,
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(number, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuideTopicCard(
    title: String,
    icon: ImageVector,
    points: List<String>,
    modifier: Modifier,
) {
    SectionCard(
        title = title,
        modifier = modifier,
        action = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    ) {
        points.forEach { GuideBullet(icon = null, text = it) }
    }
}

@Composable
private fun GuideBullet(
    icon: ImageVector?,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(text, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

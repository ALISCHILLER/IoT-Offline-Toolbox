package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.EndpointProtocol
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.ui.components.CodeSurface
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.KeyValueRow
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.PageHeaderWithAction
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
import com.msa.iotofflinetoolbox.ui.layout.responsiveLayout

@Composable
fun ProfilesScreen(
    state: AppState,
    onSaveProfile: (EndpointProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onExport: (Boolean) -> Unit,
    onInspect: (String) -> Unit,
    onImport: (String, BackupImportMode) -> Unit,
    onClearHistory: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ToolTabRow(
            selectedIndex = tab,
            labels = listOf(stringResource(Res.string.profiles_802cd16c), stringResource(Res.string.backup_95cad364), stringResource(Res.string.history_07205a76)),
            onSelect = { tab = it },
        )
        when (tab) {
            0 -> ProfileList(state.profiles, onSaveProfile, onDeleteProfile)
            1 -> BackupPanel(state, onExport, onInspect, onImport)
            else -> HistoryPanel(state.history, onClearHistory)
        }
    }
}

@Composable
private fun ProfileList(
    profiles: List<EndpointProfile>,
    onSave: (EndpointProfile) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(EndpointProtocol.HTTP) }
    var menuOpen by remember { mutableStateOf(false) }
    val portValue = port.toIntOrNull()
    val portValid = port.isBlank() || (portValue != null && portValue in 1..65_535)
    val formValid = name.isNotBlank() && endpoint.isNotBlank() && portValid
    val filtered = remember(profiles, query) {
        profiles.filter { profile ->
            query.isBlank() ||
                profile.name.contains(query, ignoreCase = true) ||
                profile.hostOrUrl.contains(query, ignoreCase = true) ||
                profile.protocol.name.contains(query, ignoreCase = true) ||
                profile.tags.any { it.contains(query, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.endpoint_profiles_1b72a7e6),
                subtitle = stringResource(Res.string.create_reusable_non_secret_endpoint_presets_for_every_41a0056e),
                eyebrow = stringResource(Res.string.workspace_library_fdfe9534),
            )
        }
        item {
            StatusBanner(
                title = stringResource(Res.string.secrets_are_excluded_6d1b6ad1),
                message = stringResource(Res.string.passwords_are_not_part_of_endpoint_profiles_sensitive_b91e92ec),
                tone = StatusTone.INFO,
            )
        }
        item {
            SectionCard(
                title = stringResource(Res.string.create_profile_9a6f5d78),
                action = { Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            ) {
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(Res.string.profile_name_6da22800)) },
                                supportingText = { Text(stringResource(Res.string.a_recognizable_local_label_05774930)) },
                                isError = name.isBlank(),
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            Box(modifier) {
                                OutlinedButton(
                                    onClick = { menuOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(protocol.displayName())
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    EndpointProtocol.entries.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value.displayName()) },
                                            onClick = {
                                                protocol = value
                                                menuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    ),
                )
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = endpoint,
                                onValueChange = { endpoint = it.trim() },
                                label = { Text(stringResource(Res.string.host_or_url_242f5d06)) },
                                supportingText = { Text(stringResource(Res.string.no_credentials_in_the_url_c9023f94)) },
                                isError = endpoint.isBlank(),
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it.filter(Char::isDigit) },
                                label = { Text(stringResource(Res.string.port_optional_d310424c)) },
                                supportingText = { Text("1–65535") },
                                isError = !portValid,
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                    ),
                )
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(Res.string.username_no_password_3efafa3c)) },
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            OutlinedTextField(
                                value = topic,
                                onValueChange = { topic = it },
                                label = { Text(stringResource(Res.string.default_topic_or_path_300b5a28)) },
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                    ),
                )
                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text(stringResource(Res.string.reusable_non_secret_headers_571c716c)) },
                    supportingText = { Text(stringResource(Res.string.authorization_cookie_and_api_key_style_headers_are_5fbc1b6a)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = responsiveTextAreaMinLines(3, 2),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                )
                ResponsiveFields(
                    fields = *arrayOf(
                        { modifier ->
                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                label = { Text(stringResource(Res.string.tags_22af85d6)) },
                                supportingText = { Text(stringResource(Res.string.comma_separated_e60f05bd)) },
                                modifier = modifier,
                                singleLine = true,
                            )
                        },
                        { modifier ->
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text(stringResource(Res.string.notes_339731c9)) },
                                modifier = modifier,
                                minLines = responsiveTextAreaMinLines(2, 1),
                            )
                        },
                    ),
                )
                Button(
                    onClick = {
                        onSave(
                            EndpointProfile(
                                id = "",
                                name = name.trim(),
                                protocol = protocol,
                                hostOrUrl = endpoint.trim(),
                                port = portValue,
                                username = username.trim(),
                                defaultTopic = topic.trim(),
                                headersText = headers,
                                notes = notes.trim(),
                                tags = tags.split(',').map(String::trim).filter(String::isNotBlank).distinct(),
                                createdAtMillis = 0,
                                updatedAtMillis = 0,
                            ),
                        )
                        name = ""
                        endpoint = ""
                        port = ""
                        username = ""
                        topic = ""
                        headers = ""
                        notes = ""
                        tags = ""
                    },
                    enabled = formValid,
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text(stringResource(Res.string.save_profile_0dae5cd9))
                }
            }
        }
        if (profiles.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.search_profiles_ae008b12)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    supportingText = { Text(stringResource(Res.string.name_protocol_endpoint_or_tag_4c542757)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (profiles.isEmpty()) stringResource(Res.string.no_endpoint_profiles_2313d7d1) else stringResource(Res.string.no_matching_profile_7cd65450),
                    message = if (profiles.isEmpty()) stringResource(Res.string.create_a_safe_reusable_preset_above_d5e435cc) else stringResource(Res.string.adjust_the_search_phrase_to_reveal_more_profiles_93f780a3),
                    icon = Icons.Outlined.Inventory2,
                )
            }
        } else {
            item {
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_profiles_available_a10c2f72, filtered.size),
                    message = stringResource(Res.string.profiles_remain_local_and_are_included_in_redacted_f1849e10),
                    tone = StatusTone.NEUTRAL,
                )
            }
            item {
                ResponsiveCardGrid(
                    minimumCardWidth = 350.dp,
                    cards = *filtered.map { profile ->
                        val card: @Composable (Modifier) -> Unit = { modifier ->
                            ProfileCard(profile, modifier, onDelete)
                        }
                        card
                    }.toTypedArray(),
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: EndpointProfile,
    modifier: Modifier,
    onDelete: (String) -> Unit,
) {
    SectionCard(
        title = profile.name,
        modifier = modifier,
        action = {
            TextButton(
                onClick = { onDelete(profile.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(stringResource(Res.string.delete_33bb6216))
            }
        },
    ) {
        KeyValueRow(stringResource(Res.string.protocol_bcb44c2c), profile.protocol.displayName())
        KeyValueRow(
            stringResource(Res.string.endpoint_e51e5f22),
            profile.hostOrUrl + profile.port?.let { ":$it" }.orEmpty(),
            monospaced = true,
        )
        profile.username.takeIf(String::isNotBlank)?.let { KeyValueRow(stringResource(Res.string.username_a29c85bc), it) }
        profile.defaultTopic.takeIf(String::isNotBlank)?.let { KeyValueRow(stringResource(Res.string.topic_or_path_43d03a5e), it, monospaced = true) }
        if (profile.headersText.isNotBlank()) {
            Text(stringResource(Res.string.reusable_headers_e440a617), style = MaterialTheme.typography.labelLarge)
            CodeSurface(profile.headersText, maxHeight = 180.dp)
        }
        if (profile.tags.isNotEmpty()) {
            KeyValueRow(stringResource(Res.string.tags_22af85d6), profile.tags.joinToString())
        }
        profile.notes.takeIf(String::isNotBlank)?.let { KeyValueRow(stringResource(Res.string.notes_339731c9), it) }
    }
}

@Composable
private fun BackupPanel(
    state: AppState,
    onExport: (Boolean) -> Unit,
    onInspect: (String) -> Unit,
    onImport: (String, BackupImportMode) -> Unit,
) {
    var includeLogs by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(BackupImportMode.MERGE) }
    val layout = responsiveLayout()
    val inspection = state.backupInspection

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.backup_and_restore_27d23f0d),
                subtitle = stringResource(Res.string.generate_versioned_redacted_json_inspect_compatibility_and_choos_182977d0),
                eyebrow = stringResource(Res.string.portable_workspace_8cdd1cce),
            )
        }
        item {
            StatusBanner(
                title = stringResource(Res.string.redaction_is_mandatory_3d9fc66c),
                message = stringResource(Res.string.passwords_authorization_values_and_other_recognized_secrets_are_aad42827),
                tone = StatusTone.INFO,
            )
        }
        item {
            ResponsivePanes(
                minimumPaneWidth = if (layout.isLowHeight) 360.dp else 430.dp,
                primary = { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.export_backup_a5a0a9a0),
                        modifier = modifier,
                        action = { Icon(Icons.Outlined.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = includeLogs, onCheckedChange = { includeLogs = it })
                            Column {
                                Text(stringResource(Res.string.include_redacted_activity_logs_e2001ec6), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(Res.string.logs_can_increase_backup_size_f0190b11), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick = { onExport(includeLogs) }) {
                            Icon(Icons.Outlined.Backup, contentDescription = null)
                            Text(stringResource(Res.string.generate_backup_json_2f8eb335))
                        }
                        if (state.backupText.isBlank()) {
                            EmptyState(
                                title = stringResource(Res.string.no_export_generated_ec12e282),
                                message = stringResource(Res.string.generate_a_redacted_backup_to_review_and_store_a4db94e2),
                                icon = Icons.Outlined.Backup,
                            )
                        } else {
                            StatusBanner(
                                title = stringResource(Res.string.backup_generated_56cca27a),
                                message = stringResource(Res.string.review_the_json_before_copying_it_to_external_c5b07f49),
                                tone = StatusTone.SUCCESS,
                            )
                            CodeSurface(state.backupText, maxHeight = if (layout.isLowHeight) 260.dp else 520.dp)
                        }
                    }
                },
                secondary = { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.inspect_and_import_d3c45f14),
                        modifier = modifier,
                        action = { Icon(Icons.Outlined.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    ) {
                        OutlinedTextField(
                            value = importText,
                            onValueChange = {
                                importText = it
                                onInspect(it)
                            },
                            label = { Text(stringResource(Res.string.backup_json_06765aa3)) },
                            supportingText = { Text(stringResource(Res.string.inspection_runs_before_import_is_enabled_61386935)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = responsiveTextAreaMinLines(10, 4),
                            maxLines = if (layout.isLowHeight) 8 else 18,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        )
                        inspection?.let {
                            StatusBanner(
                                title = if (it.compatible) stringResource(Res.string.compatible_backup_7df0e354) else stringResource(Res.string.backup_is_not_compatible_9eb127ad),
                                message = it.message,
                                tone = if (it.compatible) StatusTone.SUCCESS else StatusTone.ERROR,
                            )
                            if (it.compatible) {
                                KeyValueRow(stringResource(Res.string.schema_e9fc2d01), it.schemaVersion.toString())
                                KeyValueRow(stringResource(Res.string.application_4760706d), it.applicationVersion ?: stringResource(Res.string.unknown_2eb79774))
                                KeyValueRow(stringResource(Res.string.devices_e366fa7f), it.devices.toString())
                                KeyValueRow(stringResource(Res.string.profiles_802cd16c), it.profiles.toString())
                                KeyValueRow(stringResource(Res.string.templates_7d0571d3), it.templates.toString())
                                KeyValueRow(stringResource(Res.string.history_07205a76), it.history.toString())
                                KeyValueRow(stringResource(Res.string.logs_19a3b0f9), it.logs.toString())
                            }
                        }
                        ResponsiveActions {
                            BackupImportMode.entries.forEach { item ->
                                FilterChip(
                                    selected = mode == item,
                                    onClick = { mode = item },
                                    label = { Text(if (item == BackupImportMode.MERGE) stringResource(Res.string.merge_ba6483e7) else stringResource(Res.string.replace_e261b522)) },
                                )
                            }
                        }
                        if (mode == BackupImportMode.REPLACE) {
                            StatusBanner(
                                title = stringResource(Res.string.destructive_import_mode_8217c917),
                                message = stringResource(Res.string.replace_removes_local_collections_that_are_absent_from_536147b1),
                                tone = StatusTone.WARNING,
                            )
                        }
                        Button(
                            onClick = { onImport(importText, mode) },
                            enabled = importText.isNotBlank() && inspection?.compatible == true,
                            colors = if (mode == BackupImportMode.REPLACE) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Icon(if (mode == BackupImportMode.MERGE) Icons.Outlined.Upload else Icons.Outlined.Security, contentDescription = null)
                            Text(if (mode == BackupImportMode.MERGE) stringResource(Res.string.merge_compatible_backup_d7d14a9c) else stringResource(Res.string.replace_local_workspace_40edc3ef))
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<HistoryEntry>,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(history, query) {
        history.filter { item ->
            query.isBlank() ||
                item.protocol.name.contains(query, ignoreCase = true) ||
                item.target.contains(query, ignoreCase = true) ||
                item.summary.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeaderWithAction(
                title = stringResource(Res.string.request_history_cbd6f265),
                subtitle = stringResource(Res.string.redacted_local_evidence_for_completed_protocol_operations_93600b92),
                action = {
                    OutlinedButton(onClick = onClear, enabled = history.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                        Text(stringResource(Res.string.clear_history_fd2590e0), modifier = Modifier.padding(start = 7.dp))
                    }
                },
            )
        }
        if (history.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.search_history_58627ebb)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    supportingText = { Text(stringResource(Res.string.protocol_target_or_summary_4ce7d7d1)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (history.isEmpty()) stringResource(Res.string.no_request_history_98218f0a) else stringResource(Res.string.no_matching_history_71955008),
                    message = if (history.isEmpty()) stringResource(Res.string.run_a_network_or_protocol_operation_to_create_9d6debca) else stringResource(Res.string.change_the_search_phrase_to_reveal_other_operations_3b5f9eda),
                    icon = Icons.Outlined.History,
                )
            }
        } else {
            item {
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_history_items_d45a117b, filtered.size),
                    message = stringResource(Res.string.previews_are_bounded_and_redacted_before_persistence_8a877f73),
                    tone = StatusTone.NEUTRAL,
                )
            }
            items(filtered, key = { it.id }) { item ->
                HistoryCard(item)
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryEntry) {
    SectionCard(
        title = item.protocol.name.replace('_', ' '),
        action = {
            Text(
                text = if (item.successful) stringResource(Res.string.success_0064afd3) else stringResource(Res.string.failed_7bbeabee),
                style = MaterialTheme.typography.labelLarge,
                color = if (item.successful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
    ) {
        StatusBanner(
            title = item.summary,
            message = item.target,
            tone = if (item.successful) StatusTone.SUCCESS else StatusTone.ERROR,
        )
        item.durationMillis?.let { KeyValueRow(stringResource(Res.string.duration_2407a7ae), "$it ms") }
        if (item.requestPreview.isNotBlank() || item.responsePreview.isNotBlank()) {
            ResponsivePanes(
                minimumPaneWidth = 360.dp,
                primary = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(Res.string.request_preview_a0a06ad7), style = MaterialTheme.typography.labelLarge)
                        CodeSurface(item.requestPreview, maxHeight = 220.dp)
                    }
                },
                secondary = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(Res.string.response_preview_9a858210), style = MaterialTheme.typography.labelLarge)
                        CodeSurface(item.responsePreview, maxHeight = 220.dp)
                    }
                },
            )
        }
    }
}

@Composable
private fun EndpointProtocol.displayName(): String = when (this) {
    EndpointProtocol.HTTP -> "HTTP"
    EndpointProtocol.HTTPS -> "HTTPS"
    EndpointProtocol.TCP -> "TCP"
    EndpointProtocol.UDP -> "UDP"
    EndpointProtocol.WEBSOCKET -> "WebSocket"
    EndpointProtocol.MQTT_WEBSOCKET -> "MQTT / WebSocket"
    EndpointProtocol.COAP -> "CoAP"
}

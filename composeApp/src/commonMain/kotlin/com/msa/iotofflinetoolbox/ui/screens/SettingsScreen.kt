package com.msa.iotofflinetoolbox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.APP_VERSION
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.resources.*
import com.msa.iotofflinetoolbox.ui.components.KeyValueRow
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.ResponsiveCardGrid
import com.msa.iotofflinetoolbox.ui.components.ResponsiveFields
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.layout.responsiveLayout
import org.jetbrains.compose.resources.stringResource
import com.msa.iotofflinetoolbox.ui.localization.localizedNotes

@Composable
fun SettingsScreen(
    settings: AppSettings,
    capabilities: PlatformCapabilities,
    onSave: (AppSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    val layout = responsiveLayout()
    val hasChanges = draft != settings
    val validation = validateSettings(draft)

    fun applyImmediately(updated: AppSettings) {
        draft = updated
        onSave(updated)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(responsiveContentPadding()),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        PageHeader(
            title = stringResource(Res.string.settings_7522320f),
            subtitle = stringResource(Res.string.tune_bounded_operations_privacy_controls_retention_language_and_95cf5394),
            eyebrow = stringResource(Res.string.application_policy_cbab643e),
        )

        StatusBanner(
            title = if (hasChanges) {
                stringResource(Res.string.unsaved_changes_4fbf4758)
            } else {
                stringResource(Res.string.settings_are_up_to_date_83ecc3c6)
            },
            message = if (hasChanges) {
                stringResource(Res.string.review_the_values_below_and_save_when_ready_1e4dc207)
            } else {
                stringResource(Res.string.settings_apply_immediately)
            },
            tone = if (hasChanges) StatusTone.WARNING else StatusTone.SUCCESS,
        )

        validation?.let {
            StatusBanner(
                title = stringResource(Res.string.check_the_limits_c73f0276),
                message = it,
                tone = StatusTone.ERROR,
            )
        }

        ResponsiveCardGrid(
            cards = *arrayOf(
                { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.language_and_appearance_761baa6d),
                        modifier = modifier,
                    ) {
                        LanguagePicker(
                            selected = draft.language,
                            onSelected = { language ->
                                applyImmediately(
                                    draft.copy(
                                        language = language,
                                        rtl = language == AppLanguage.PERSIAN,
                                    ),
                                )
                            },
                        )
                        SettingSwitch(
                            title = stringResource(Res.string.settings_system_theme),
                            subtitle = stringResource(Res.string.settings_system_theme_desc),
                            checked = draft.useSystemTheme,
                            icon = Icons.Outlined.Tune,
                        ) { applyImmediately(draft.copy(useSystemTheme = it)) }
                        SettingSwitch(
                            title = stringResource(Res.string.dark_mode_9aaeab9b),
                            subtitle = stringResource(Res.string.use_the_high_contrast_dark_color_system_101850ae),
                            checked = draft.darkMode,
                            enabled = !draft.useSystemTheme,
                            icon = if (draft.darkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        ) { applyImmediately(draft.copy(darkMode = it)) }
                        SettingSwitch(
                            title = stringResource(Res.string.right_to_left_layout_b29a819b),
                            subtitle = stringResource(Res.string.mirror_layout_direction_for_persian_and_arabic_workflows_647d1ecb),
                            checked = draft.rtl,
                            icon = Icons.Outlined.Language,
                        ) { applyImmediately(draft.copy(rtl = it)) }
                        SettingSwitch(
                            title = stringResource(Res.string.settings_compact_mode),
                            subtitle = stringResource(Res.string.settings_compact_mode_desc),
                            checked = draft.compactMode,
                            icon = Icons.Outlined.Tune,
                        ) { applyImmediately(draft.copy(compactMode = it)) }
                        SettingSwitch(
                            title = stringResource(Res.string.settings_advanced_options),
                            subtitle = stringResource(Res.string.settings_advanced_options_desc),
                            checked = draft.showAdvancedOptions,
                            icon = Icons.Outlined.Tune,
                        ) { applyImmediately(draft.copy(showAdvancedOptions = it)) }
                    }
                },
                { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.settings_http_defaults),
                        modifier = modifier,
                    ) {
                        NumberField(
                            label = stringResource(Res.string.settings_http_request_timeout),
                            value = draft.defaultHttpTimeoutMillis,
                            supporting = "100 – 120000",
                        ) { draft = draft.copy(defaultHttpTimeoutMillis = it) }
                        NumberField(
                            label = stringResource(Res.string.settings_http_connect_timeout),
                            value = draft.defaultHttpConnectTimeoutMillis,
                            supporting = if (capabilities.httpConnectTimeout) "100 – 120000"
                            else stringResource(Res.string.http_timeout_unsupported_by_engine),
                            enabled = capabilities.httpConnectTimeout,
                        ) { draft = draft.copy(defaultHttpConnectTimeoutMillis = it) }
                        NumberField(
                            label = stringResource(Res.string.settings_http_socket_timeout),
                            value = draft.defaultHttpSocketTimeoutMillis,
                            supporting = if (capabilities.httpSocketTimeout) "100 – 120000"
                            else stringResource(Res.string.http_timeout_unsupported_by_engine),
                            enabled = capabilities.httpSocketTimeout,
                        ) { draft = draft.copy(defaultHttpSocketTimeoutMillis = it) }
                        SettingSwitch(
                            title = stringResource(Res.string.settings_http_redirects),
                            subtitle = if (capabilities.manualHttpRedirects) {
                                stringResource(Res.string.settings_http_redirects_desc)
                            } else {
                                stringResource(Res.string.http_redirect_unsupported)
                            },
                            checked = draft.defaultFollowRedirects && capabilities.manualHttpRedirects,
                            enabled = capabilities.manualHttpRedirects,
                            icon = Icons.Outlined.Tune,
                        ) { draft = draft.copy(defaultFollowRedirects = it) }
                        StatusBanner(
                            title = stringResource(Res.string.secret_redaction_is_mandatory_f98c9b12),
                            message = stringResource(Res.string.authorization_headers_passwords_tokens_and_keys_are_redacted_1e0ef6de),
                            tone = StatusTone.SUCCESS,
                        )
                    }
                },
            ),
        )

        SectionCard(stringResource(Res.string.settings_network_limits)) {
            ResponsiveFields(
                fields = *arrayOf(
                    { fieldModifier ->
                        NumberField(
                            label = stringResource(Res.string.default_timeout_ms_5b031ffc),
                            value = draft.defaultTimeoutMillis,
                            supporting = "100 – 60000",
                            modifier = fieldModifier,
                        ) { draft = draft.copy(defaultTimeoutMillis = it) }
                    },
                    { fieldModifier ->
                        NumberField(
                            label = stringResource(Res.string.concurrent_probes_95414124),
                            value = draft.scanConcurrency,
                            supporting = "1 – 256",
                            modifier = fieldModifier,
                        ) { draft = draft.copy(scanConcurrency = it) }
                    },
                    { fieldModifier ->
                        NumberField(
                            label = stringResource(Res.string.settings_port_limit),
                            value = draft.maxPortScanItems,
                            supporting = stringResource(Res.string.settings_port_limit_hint),
                            modifier = fieldModifier,
                        ) { draft = draft.copy(maxPortScanItems = it) }
                    },
                    { fieldModifier ->
                        NumberField(
                            label = stringResource(Res.string.maximum_discovery_hosts_3e187225),
                            value = draft.maxDiscoveryHosts,
                            supporting = "1 – 4096",
                            modifier = fieldModifier,
                        ) { draft = draft.copy(maxDiscoveryHosts = it) }
                    },
                    { fieldModifier ->
                        NumberField(
                            label = stringResource(Res.string.maximum_response_bytes_a2852106),
                            value = draft.maxResponseBytes,
                            supporting = "1024 – 1048576",
                            modifier = fieldModifier,
                        ) { draft = draft.copy(maxResponseBytes = it) }
                    },
                ),
            )
        }

        ResponsiveCardGrid(
            cards = *arrayOf(
                { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.local_retention_and_privacy_f4a0b45f),
                        modifier = modifier,
                    ) {
                        ResponsiveFields(
                            fields = *arrayOf(
                                { fieldModifier ->
                                    NumberField(
                                        label = stringResource(Res.string.retained_log_entries_514b4e64),
                                        value = draft.retainLogs,
                                        supporting = "20 – 2000",
                                        modifier = fieldModifier,
                                    ) { draft = draft.copy(retainLogs = it) }
                                },
                                { fieldModifier ->
                                    NumberField(
                                        label = stringResource(Res.string.retained_history_entries_93b4b2fe),
                                        value = draft.retainHistory,
                                        supporting = "20 – 1000",
                                        modifier = fieldModifier,
                                    ) { draft = draft.copy(retainHistory = it) }
                                },
                            ),
                        )
                        SettingSwitch(
                            title = stringResource(Res.string.allow_public_cleartext_endpoints_1ac5aac7),
                            subtitle = if (capabilities.publicCleartextOverride) {
                                stringResource(Res.string.unsafe_permits_http_ws_to_non_local_hosts_fb9a11f9)
                            } else {
                                capabilities.localizedNotes()
                            },
                            checked = draft.allowPublicCleartext && capabilities.publicCleartextOverride,
                            enabled = capabilities.publicCleartextOverride,
                            icon = Icons.Outlined.Security,
                        ) { draft = draft.copy(allowPublicCleartext = it) }
                        if (draft.allowPublicCleartext && capabilities.publicCleartextOverride) {
                            StatusBanner(
                                title = stringResource(Res.string.public_cleartext_is_enabled_3705039d),
                                message = stringResource(Res.string.traffic_and_headers_can_be_observed_or_modified_b99034d9),
                                tone = StatusTone.WARNING,
                            )
                        }
                    }
                },
                { modifier ->
                    SectionCard(
                        title = stringResource(Res.string.runtime_and_identity_95bae63b),
                        modifier = modifier,
                    ) {
                        KeyValueRow(stringResource(Res.string.platform_3f26cfed), capabilities.platformName)
                        KeyValueRow(stringResource(Res.string.version_ded4f805), APP_VERSION)
                        KeyValueRow(stringResource(Res.string.brand_1bbdf799), "MSA")
                        KeyValueRow(stringResource(Res.string.developer_900bdf5e), "ALISCHILLER")
                        KeyValueRow(stringResource(Res.string.email_d0515dcf), "solimaniali90@gmail.com", monospaced = true)
                        StatusBanner(
                            title = stringResource(Res.string.platform_notes_941d4e3d),
                            message = capabilities.localizedNotes(),
                            tone = StatusTone.INFO,
                        )
                    }
                },
            ),
        )

        val saveSettingsDescription = stringResource(Res.string.save_application_settings_6e076661)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            ResponsiveActions {
                OutlinedButton(
                    onClick = { draft = settings },
                    enabled = hasChanges,
                ) {
                    Text(stringResource(Res.string.settings_reset))
                }
                Button(
                    onClick = { onSave(draft) },
                    enabled = hasChanges && validation == null,
                    modifier = Modifier
                        .then(if (layout.isCompactWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = 260.dp))
                        .semantics { contentDescription = saveSettingsDescription },
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text(stringResource(Res.string.settings_save_restart), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.application_language_60721315), style = MaterialTheme.typography.labelLarge)
        ResponsiveActions {
            FilterChip(
                selected = selected == AppLanguage.ENGLISH,
                onClick = { onSelected(AppLanguage.ENGLISH) },
                label = { Text("English") },
                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            )
            FilterChip(
                selected = selected == AppLanguage.PERSIAN,
                onClick = { onSelected(AppLanguage.PERSIAN) },
                label = { Text("فارسی") },
                leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f),
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onChecked else null,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    supporting: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValue: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit)
            if (digits.isEmpty()) onValue(0) else digits.toIntOrNull()?.let(onValue)
        },
        label = { Text(label) },
        supportingText = { Text(supporting) },
        enabled = enabled,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun validateSettings(settings: AppSettings): String? = when {
    settings.defaultTimeoutMillis !in 100..60_000 -> stringResource(Res.string.default_timeout_must_be_between_100_and_60000_052e4884)
    settings.defaultHttpTimeoutMillis !in 100..120_000 -> stringResource(Res.string.http_invalid_timeout)
    settings.defaultHttpConnectTimeoutMillis !in 100..120_000 -> stringResource(Res.string.http_invalid_timeout)
    settings.defaultHttpSocketTimeoutMillis !in 100..120_000 -> stringResource(Res.string.http_invalid_timeout)
    settings.scanConcurrency !in 1..256 -> stringResource(Res.string.concurrent_probes_must_be_between_1_and_256_68c44773)
    settings.maxPortScanItems !in 1..4_096 -> stringResource(Res.string.settings_port_limit_hint)
    settings.maxDiscoveryHosts !in 1..4_096 -> stringResource(Res.string.maximum_discovery_hosts_must_be_between_1_and_c5bc46b5)
    settings.maxResponseBytes !in 1_024..1_048_576 -> stringResource(Res.string.maximum_response_bytes_must_be_between_1024_and_e94022dd)
    settings.retainLogs !in 20..2_000 -> stringResource(Res.string.retained_logs_must_be_between_20_and_2000_6db1acd7)
    settings.retainHistory !in 20..1_000 -> stringResource(Res.string.retained_history_must_be_between_20_and_1000_a1e6371a)
    else -> null
}

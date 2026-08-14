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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.ToolLog
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.LogLevelBadge
import com.msa.iotofflinetoolbox.ui.components.PageHeaderWithAction
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing

@Composable
fun LogsScreen(
    logs: List<ToolLog>,
    onClear: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filtered = remember(logs, query, selectedLevel) {
        logs.filter { log ->
            val matchesQuery = query.isBlank() ||
                log.source.contains(query, ignoreCase = true) ||
                log.message.contains(query, ignoreCase = true)
            val matchesLevel = selectedLevel == null || log.level == selectedLevel
            matchesQuery && matchesLevel
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeaderWithAction(
                title = stringResource(Res.string.activity_logs_90d2d031),
                subtitle = stringResource(Res.string.search_local_diagnostic_evidence_logs_are_never_uploaded_024bf0b6),
                action = {
                    OutlinedButton(onClick = onClear, enabled = logs.isNotEmpty()) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                        Text(stringResource(Res.string.clear_all_af28fcba), modifier = Modifier.padding(start = 7.dp))
                    }
                },
            )
        }

        item {
            SectionCard(
                title = stringResource(Res.string.filter_evidence_804bc720),
                action = {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "${filtered.size}/${logs.size}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.search_source_or_message_fa7374c2)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ResponsiveActions {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text(stringResource(Res.string.all_eabc8ac3)) },
                        leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                    )
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = { Text(level.localizedLabel()) },
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (logs.isEmpty()) {
                        stringResource(Res.string.no_activity_yet_9b360fa6)
                    } else {
                        stringResource(Res.string.no_matching_logs_466c72fd)
                    },
                    message = if (logs.isEmpty()) {
                        stringResource(Res.string.run_a_diagnostic_tool_and_its_outcome_will_5a01843f)
                    } else {
                        stringResource(Res.string.change_the_search_text_or_selected_severity_32238279)
                    },
                    icon = Icons.Outlined.ReceiptLong,
                )
            }
        }

        items(filtered, key = { it.id }) { log ->
            SectionCard(title = log.source) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LogLevelBadge(log.level)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                log.level.localizedLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                log.timestampMillis.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        Text(
                            log.message,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLevel.localizedLabel(): String = when (this) {
    LogLevel.INFO -> stringResource(Res.string.info_3bec4fc3)
    LogLevel.SUCCESS -> stringResource(Res.string.success_c6411466)
    LogLevel.WARNING -> stringResource(Res.string.warning_1ad6c35c)
    LogLevel.ERROR -> stringResource(Res.string.error_047350e1)
}

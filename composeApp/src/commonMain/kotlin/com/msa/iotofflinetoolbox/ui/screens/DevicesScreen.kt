package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.InfoPill
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

@Composable
fun DevicesScreen(
    devices: List<SavedDevice>,
    onUpdate: (SavedDevice) -> Unit,
    onDelete: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(devices, query) {
        devices.filter { device ->
            query.isBlank() ||
                device.displayName.contains(query, ignoreCase = true) ||
                device.host.contains(query, ignoreCase = true) ||
                device.note.contains(query, ignoreCase = true) ||
                device.tags.any { it.contains(query, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.saved_devices_2e67e824),
                subtitle = stringResource(Res.string.a_private_searchable_inventory_of_endpoints_worth_returning_3d62a38e),
                eyebrow = stringResource(Res.string.offline_inventory_e030dee7),
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(Res.string.search_devices_af7e3371)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                supportingText = { Text(stringResource(Res.string.name_host_note_or_tag_4b0d4c70)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (devices.isNotEmpty()) {
            item {
                StatusBanner(
                    title = stringResource(Res.string.s_1_s_devices_shown_45bbb7c9, filtered.size),
                    message = stringResource(Res.string.this_inventory_stays_on_the_current_device_and_36a1a042),
                    tone = StatusTone.NEUTRAL,
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (devices.isEmpty()) {
                        stringResource(Res.string.no_saved_devices_2e1cffc7)
                    } else {
                        stringResource(Res.string.no_matching_device_8f701d6d)
                    },
                    message = if (devices.isEmpty()) {
                        stringResource(Res.string.save_a_discovered_host_to_make_it_available_e305492c)
                    } else {
                        stringResource(Res.string.try_another_name_address_or_tag_8340b682)
                    },
                    icon = Icons.Outlined.DevicesOther,
                )
            }
        }

        item {
            if (filtered.isNotEmpty()) {
                ResponsiveCardGrid(
                    minimumCardWidth = 360.dp,
                    cards = filtered.map { device ->
                        val card: @Composable (Modifier) -> Unit = { modifier ->
                            DeviceCard(
                                device = device,
                                onUpdate = onUpdate,
                                onDelete = onDelete,
                                modifier = modifier,
                            )
                        }
                        card
                    },
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: SavedDevice,
    onUpdate: (SavedDevice) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier,
) {
    var editing by remember(device.id) { mutableStateOf(false) }
    var name by remember(device.id, device.displayName) { mutableStateOf(device.displayName) }
    var note by remember(device.id, device.note) { mutableStateOf(device.note) }

    SectionCard(
        title = if (editing) stringResource(Res.string.edit_device_e31ac600) else device.displayName,
        modifier = modifier,
        action = if (editing) {
            null
        } else {
            {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        device.host,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        if (editing) {
            ResponsiveFields(
                fields = *arrayOf(
                    { fieldModifier ->
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(Res.string.display_name_96322d2c)) },
                            modifier = fieldModifier,
                            singleLine = true,
                        )
                    },
                    { fieldModifier ->
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(stringResource(Res.string.notes_339731c9)) },
                            modifier = fieldModifier,
                            minLines = 2,
                        )
                    },
                ),
            )
        } else {
            KeyValueRow(stringResource(Res.string.host_40f11ffc), device.host, monospaced = true)
            KeyValueRow(
                stringResource(Res.string.last_seen_b6370cb9),
                device.lastSeenMillis.toString(),
                monospaced = true,
            )
            if (device.note.isNotBlank()) {
                KeyValueRow(stringResource(Res.string.notes_339731c9), device.note)
            }

            val chips = buildList {
                addAll(device.addresses.map { "IP · $it" })
                addAll(device.openPorts.map { "TCP · $it" })
                addAll(device.services.map { "Service · $it" })
                addAll(device.tags.map { "# $it" })
            }
            if (chips.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    chips.forEach { label ->
                        InfoPill(label = label)
                    }
                }
            }
        }

        ResponsiveActions {
            if (editing) {
                Button(
                    onClick = {
                        onUpdate(
                            device.copy(
                                displayName = name.ifBlank { device.host },
                                note = note,
                            ),
                        )
                        editing = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text(stringResource(Res.string.save_changes_4d1ef3e4), modifier = Modifier.padding(start = 7.dp))
                }
                TextButton(onClick = { editing = false }) {
                    Text(stringResource(Res.string.cancel_430b60e6))
                }
            } else {
                OutlinedButton(onClick = { editing = true }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Text(stringResource(Res.string.edit_f4739270), modifier = Modifier.padding(start = 7.dp))
                }
                TextButton(
                    onClick = { onDelete(device.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(stringResource(Res.string.delete_33bb6216), modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
    }
}

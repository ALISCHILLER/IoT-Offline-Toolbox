package com.msa.iotofflinetoolbox.ui.screens

import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.PayloadOperation
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.ui.components.CodeSurface
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.ResponsiveCardGrid
import com.msa.iotofflinetoolbox.ui.components.ResponsivePanes
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.components.responsiveTextAreaMinLines

@Composable
fun PayloadScreen(
    state: AppState,
    onTransform: (String, PayloadOperation, Map<String, String>) -> Unit,
    onSaveTemplate: (PayloadTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
) {
    var input by remember {
        mutableStateOf("{\"deviceId\":\"{{uuid}}\",\"timestamp\":{{timestamp}},\"value\":42}")
    }
    var variableText by remember { mutableStateOf("site=lab\nline=1") }
    var templateName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = responsiveContentPadding(),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        item {
            PageHeader(
                title = stringResource(Res.string.payload_laboratory_fe41667a),
                subtitle = stringResource(Res.string.format_validate_encode_and_parameterize_payloads_without_sending_1dab6057),
                eyebrow = stringResource(Res.string.offline_transformations_e8c1de4d),
            )
        }

        item {
            StatusBanner(
                title = stringResource(Res.string.runs_entirely_on_this_device_51b3ce10),
                message = stringResource(Res.string.input_variables_and_transformed_output_stay_in_memory_b782c19c),
                tone = StatusTone.NEUTRAL,
            )
        }

        item {
            val inputPane: @Composable (Modifier) -> Unit = { modifier ->
                SectionCard(stringResource(Res.string.input_and_variables_aa1cc89e), modifier = modifier) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(stringResource(Res.string.payload_489cdded)) },
                        supportingText = { Text(stringResource(Res.string.text_json_hex_or_base64_depending_on_the_346603ee)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = responsiveTextAreaMinLines(normal = 9, lowHeight = 4),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedTextField(
                        value = variableText,
                        onValueChange = { variableText = it },
                        label = { Text(stringResource(Res.string.custom_variables_a1af6463)) },
                        supportingText = {
                            Text(
                                stringResource(Res.string.one_key_value_per_line_reference_as_key_18bbd6ad),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                    ResponsiveActions {
                        PayloadOperation.entries.forEach { operation ->
                            FilledTonalButton(
                                onClick = { onTransform(input, operation, parseVariables(variableText)) },
                                enabled = input.isNotBlank(),
                            ) {
                                Text(operation.localizedTitle())
                            }
                        }
                    }
                }
            }

            val resultPane: @Composable (Modifier) -> Unit = { modifier ->
                state.payloadResult?.let { result ->
                    SectionCard(
                        title = stringResource(Res.string.transformation_result_2d2d8b3f),
                        modifier = modifier,
                    ) {
                        StatusBanner(
                            title = result.operation.localizedTitle(),
                            message = result.message,
                            tone = if (result.valid) StatusTone.SUCCESS else StatusTone.ERROR,
                        )
                        CodeSurface(
                            text = result.output.ifBlank { stringResource(Res.string.empty_d63306f0) },
                            maxHeight = 520.dp,
                        )
                        if (result.output.isNotBlank()) {
                            OutlinedButton(onClick = { input = result.output }) {
                                Icon(Icons.Outlined.Input, contentDescription = null)
                                Text(stringResource(Res.string.use_as_input_b95aca39), modifier = Modifier.padding(start = 7.dp))
                            }
                        }
                    }
                } ?: SectionCard(stringResource(Res.string.transformation_result_2d2d8b3f), modifier = modifier) {
                    EmptyState(
                        title = stringResource(Res.string.choose_an_operation_1f27208c),
                        message = stringResource(Res.string.the_transformed_output_and_validation_status_will_appear_6ad368a3),
                        icon = Icons.Outlined.Code,
                    )
                }
            }

            ResponsivePanes(
                minimumPaneWidth = 420.dp,
                primary = inputPane,
                secondary = resultPane,
            )
        }

        item {
            SectionCard(stringResource(Res.string.save_reusable_template_ced880a2)) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text(stringResource(Res.string.template_name_58a87f76)) },
                    supportingText = { Text(stringResource(Res.string.use_a_name_that_describes_the_device_or_00e87d38)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        onSaveTemplate(
                            PayloadTemplate(
                                id = "",
                                name = templateName,
                                content = input,
                                createdAtMillis = 0,
                                updatedAtMillis = 0,
                            ),
                        )
                        templateName = ""
                    },
                    enabled = templateName.isNotBlank() && input.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text(stringResource(Res.string.save_locally_5eebdd76), modifier = Modifier.padding(start = 7.dp))
                }
            }
        }

        item {
            SectionCard(
                title = stringResource(Res.string.saved_templates_7b06a62d),
                action = if (state.templates.isNotEmpty()) {
                    { Text("${state.templates.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
            ) {
                if (state.templates.isEmpty()) {
                    EmptyState(
                        title = stringResource(Res.string.no_saved_templates_a2faabd7),
                        message = stringResource(Res.string.save_the_current_input_to_reuse_it_across_4af470d2),
                        icon = Icons.Outlined.Code,
                    )
                } else {
                    ResponsiveCardGrid(
                        minimumCardWidth = 320.dp,
                        cards = state.templates.map { template ->
                            val card: @Composable (Modifier) -> Unit = { modifier ->
                                SectionCard(template.name, modifier = modifier) {
                                    CodeSurface(text = template.content.take(1_000), maxHeight = 220.dp)
                                    ResponsiveActions {
                                        OutlinedButton(onClick = { input = template.content }) {
                                            Icon(Icons.Outlined.Input, contentDescription = null)
                                            Text(stringResource(Res.string.load_6a01546f), modifier = Modifier.padding(start = 7.dp))
                                        }
                                        TextButton(
                                            onClick = { onDeleteTemplate(template.id) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        ) {
                                            Icon(Icons.Outlined.Delete, contentDescription = null)
                                            Text(stringResource(Res.string.delete_33bb6216), modifier = Modifier.padding(start = 7.dp))
                                        }
                                    }
                                }
                            }
                            card
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PayloadOperation.localizedTitle(): String = when (this) {
    PayloadOperation.FORMAT_JSON -> stringResource(Res.string.format_json_3dae0a90)
    PayloadOperation.MINIFY_JSON -> stringResource(Res.string.minify_json_a8afad43)
    PayloadOperation.VALIDATE_JSON -> stringResource(Res.string.validate_json_e1c2ed8f)
    PayloadOperation.TEXT_TO_HEX -> stringResource(Res.string.text_hex_65cc7cbc)
    PayloadOperation.HEX_TO_TEXT -> stringResource(Res.string.hex_text_bd30712c)
    PayloadOperation.TEXT_TO_BASE64 -> stringResource(Res.string.text_base64_965a2c1f)
    PayloadOperation.BASE64_TO_TEXT -> stringResource(Res.string.base64_text_326caac3)
    PayloadOperation.EXPAND_VARIABLES -> stringResource(Res.string.expand_variables_183fff1f)
    PayloadOperation.CRC32 -> "CRC32"
}

private fun parseVariables(raw: String): Map<String, String> = raw.lines().mapNotNull { line ->
    val index = line.indexOf('=')
    if (index <= 0) {
        null
    } else {
        line.substring(0, index).trim().takeIf(String::isNotEmpty)?.let {
            it to line.substring(index + 1).trim()
        }
    }
}.toMap()

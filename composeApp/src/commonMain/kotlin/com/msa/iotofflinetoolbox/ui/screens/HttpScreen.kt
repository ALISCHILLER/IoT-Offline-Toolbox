package com.msa.iotofflinetoolbox.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.msa.iotofflinetoolbox.core.model.AppState
import com.msa.iotofflinetoolbox.core.model.EndpointProtocol
import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.HttpAuthType
import com.msa.iotofflinetoolbox.core.model.HttpBodyType
import com.msa.iotofflinetoolbox.core.model.HttpRequestInput
import com.msa.iotofflinetoolbox.core.model.HttpResponseResult
import com.msa.iotofflinetoolbox.core.network.CurlShell
import com.msa.iotofflinetoolbox.core.network.HttpRequestPreview
import com.msa.iotofflinetoolbox.core.network.HttpResponseBodyTools
import com.msa.iotofflinetoolbox.core.network.HttpRequestValidationIssue
import com.msa.iotofflinetoolbox.core.network.HttpRequestValidationPolicy
import com.msa.iotofflinetoolbox.core.network.HttpRequestValidator
import com.msa.iotofflinetoolbox.core.network.TransportSecurity
import com.msa.iotofflinetoolbox.resources.*
import com.msa.iotofflinetoolbox.ui.components.CodeSurface
import com.msa.iotofflinetoolbox.ui.components.EmptyState
import com.msa.iotofflinetoolbox.ui.components.InfoPill
import com.msa.iotofflinetoolbox.ui.components.KeyValueRow
import com.msa.iotofflinetoolbox.ui.components.PageHeader
import com.msa.iotofflinetoolbox.ui.components.ResponsiveActions
import com.msa.iotofflinetoolbox.ui.components.ResponsiveFields
import com.msa.iotofflinetoolbox.ui.components.ResponsivePanes
import com.msa.iotofflinetoolbox.ui.components.SectionCard
import com.msa.iotofflinetoolbox.ui.components.SectionDivider
import com.msa.iotofflinetoolbox.ui.components.StatusBanner
import com.msa.iotofflinetoolbox.ui.components.StatusTone
import com.msa.iotofflinetoolbox.ui.components.ToolTabRow
import com.msa.iotofflinetoolbox.ui.components.responsiveContentPadding
import com.msa.iotofflinetoolbox.ui.components.responsivePageSpacing
import com.msa.iotofflinetoolbox.ui.layout.responsiveLayout
import org.jetbrains.compose.resources.stringResource

private enum class HttpEditorTab {
    QUERY,
    HEADERS,
    AUTH,
    BODY,
    ADVANCED,
    PREVIEW,
}

private enum class HttpResponseView {
    PRETTY,
    TEXT,
    HEX,
    BASE64,
}

@Composable
fun HttpScreen(
    state: AppState,
    onExecute: (HttpRequestInput) -> Unit,
) {
    var input by remember {
        mutableStateOf(
            HttpRequestInput(
                timeoutMillis = state.settings.defaultHttpTimeoutMillis,
                connectTimeoutMillis = state.settings.defaultHttpConnectTimeoutMillis,
                socketTimeoutMillis = state.settings.defaultHttpSocketTimeoutMillis,
                followRedirects = state.settings.defaultFollowRedirects && state.capabilities.manualHttpRedirects,
            ),
        )
    }
    var selectedTab by remember { mutableStateOf(HttpEditorTab.QUERY) }
    var methodMenu by remember { mutableStateOf(false) }
    val visibleTabs = remember(state.settings.showAdvancedOptions) {
        HttpEditorTab.entries.filter { state.settings.showAdvancedOptions || it != HttpEditorTab.ADVANCED }
    }
    val activeTab = selectedTab.takeIf { it in visibleTabs } ?: HttpEditorTab.QUERY
    val validation = validateHttpInput(state, input)
    val layout = responsiveLayout()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(responsiveContentPadding()),
        verticalArrangement = Arrangement.spacedBy(responsivePageSpacing()),
    ) {
        PageHeader(
            title = stringResource(Res.string.http_workbench_title),
            subtitle = stringResource(Res.string.http_workbench_subtitle),
            eyebrow = "HTTP / HTTPS",
        )

        StatusBanner(
            title = stringResource(Res.string.http_local_security),
            message = stringResource(Res.string.http_local_security_desc),
            tone = StatusTone.INFO,
            action = {
                InfoPill(
                    label = if (input.followRedirects) {
                        stringResource(Res.string.http_follow_redirects)
                    } else {
                        stringResource(Res.string.http_redirects_disabled)
                    },
                    icon = Icons.Outlined.Security,
                )
            },
        )

        SavedHttpInputs(
            state = state,
            onLoadProfile = { profile ->
                input = input.copy(
                    url = profile.hostOrUrl,
                    headersText = profile.headersText.ifBlank { input.headersText },
                )
            },
            onLoadTemplate = { template ->
                input = input.copy(
                    body = template.content,
                    bodyType = if (template.contentType.contains("json", ignoreCase = true)) {
                        HttpBodyType.JSON
                    } else {
                        HttpBodyType.RAW
                    },
                    contentType = template.contentType,
                )
                selectedTab = HttpEditorTab.BODY
            },
            onLoadHistory = { target -> input = input.copy(url = target) },
        )

        val requestPane: @Composable (Modifier) -> Unit = { modifier ->
            HttpRequestBuilder(
                modifier = modifier,
                state = state,
                input = input,
                validation = validation,
                selectedTab = activeTab,
                visibleTabs = visibleTabs,
                methodMenu = methodMenu,
                onMethodMenu = { methodMenu = it },
                onTab = { selectedTab = it },
                onInput = { input = it },
                onClear = {
                    input = HttpRequestInput(
                        timeoutMillis = state.settings.defaultHttpTimeoutMillis,
                        connectTimeoutMillis = state.settings.defaultHttpConnectTimeoutMillis,
                        socketTimeoutMillis = state.settings.defaultHttpSocketTimeoutMillis,
                        followRedirects = state.settings.defaultFollowRedirects && state.capabilities.manualHttpRedirects,
                    )
                    selectedTab = HttpEditorTab.QUERY
                },
                onExecute = onExecute,
            )
        }

        state.httpResult?.let { result ->
            ResponsivePanes(
                minimumPaneWidth = if (layout.isLowHeight) 350.dp else 430.dp,
                primary = requestPane,
                secondary = { modifier -> HttpResponseCard(result, modifier) },
            )
        } ?: ResponsivePanes(
            minimumPaneWidth = if (layout.isLowHeight) 350.dp else 430.dp,
            primary = requestPane,
            secondary = { modifier ->
                SectionCard(
                    title = stringResource(Res.string.http_response_overview),
                    modifier = modifier,
                ) {
                    EmptyState(
                        title = stringResource(Res.string.http_no_response),
                        message = stringResource(Res.string.http_no_response_desc),
                        icon = Icons.Outlined.Http,
                    )
                }
            },
        )
    }
}

@Composable
private fun SavedHttpInputs(
    state: AppState,
    onLoadProfile: (com.msa.iotofflinetoolbox.core.model.EndpointProfile) -> Unit,
    onLoadTemplate: (com.msa.iotofflinetoolbox.core.model.PayloadTemplate) -> Unit,
    onLoadHistory: (String) -> Unit,
) {
    val profiles = state.profiles.filter {
        it.protocol == EndpointProtocol.HTTP || it.protocol == EndpointProtocol.HTTPS
    }
    val history = state.history.filter { it.protocol == HistoryProtocol.HTTP }.take(8)
    if (profiles.isEmpty() && state.templates.isEmpty() && history.isEmpty()) return

    SectionCard(stringResource(Res.string.http_request_configuration)) {
        if (profiles.isNotEmpty()) {
            Text(stringResource(Res.string.saved_profiles_6ee18817), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    AssistChip(
                        onClick = { onLoadProfile(profile) },
                        label = { Text(profile.name) },
                        leadingIcon = { Icon(Icons.Outlined.Http, contentDescription = null) },
                    )
                }
            }
        }
        if (state.templates.isNotEmpty()) {
            Text(stringResource(Res.string.http_saved_templates), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.templates.take(12).forEach { template ->
                    AssistChip(
                        onClick = { onLoadTemplate(template) },
                        label = { Text(template.name) },
                        leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            Text(stringResource(Res.string.http_recent_history), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { entry ->
                    AssistChip(
                        onClick = { onLoadHistory(entry.target) },
                        label = { Text(entry.target, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HttpRequestBuilder(
    modifier: Modifier,
    state: AppState,
    input: HttpRequestInput,
    validation: String?,
    selectedTab: HttpEditorTab,
    visibleTabs: List<HttpEditorTab>,
    methodMenu: Boolean,
    onMethodMenu: (Boolean) -> Unit,
    onTab: (HttpEditorTab) -> Unit,
    onInput: (HttpRequestInput) -> Unit,
    onClear: () -> Unit,
    onExecute: (HttpRequestInput) -> Unit,
) {
    SectionCard(
        title = stringResource(Res.string.http_request_builder),
        modifier = modifier,
        action = {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    input.method,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        ResponsiveFields(
            fields = *arrayOf(
                { fieldModifier ->
                    Box(fieldModifier) {
                        OutlinedButton(
                            onClick = { onMethodMenu(true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(input.method)
                        }
                        DropdownMenu(
                            expanded = methodMenu,
                            onDismissRequest = { onMethodMenu(false) },
                        ) {
                            listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        onInput(
                                            input.copy(
                                                method = method,
                                                bodyType = if (method == "HEAD") HttpBodyType.NONE else input.bodyType,
                                            ),
                                        )
                                        onMethodMenu(false)
                                    },
                                )
                            }
                        }
                    }
                },
                { fieldModifier ->
                    OutlinedTextField(
                        value = input.url,
                        onValueChange = { onInput(input.copy(url = it)) },
                        label = { Text(stringResource(Res.string.http_url)) },
                        placeholder = { Text("https://api.example.com/v1/devices") },
                        isError = input.url.isNotBlank() && !isHttpUrlValid(input.url),
                        modifier = fieldModifier,
                        singleLine = true,
                    )
                },
            ),
        )

        ToolTabRow(
            selectedIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0),
            labels = visibleTabs.map { it.localizedLabel() },
            onSelect = { onTab(visibleTabs[it]) },
        )

        when (selectedTab) {
            HttpEditorTab.QUERY -> QueryEditor(input, onInput)
            HttpEditorTab.HEADERS -> HeaderEditor(state, input, onInput)
            HttpEditorTab.AUTH -> AuthenticationEditor(input, onInput)
            HttpEditorTab.BODY -> BodyEditor(state, input, onInput)
            HttpEditorTab.ADVANCED -> AdvancedHttpEditor(state, input, onInput)
            HttpEditorTab.PREVIEW -> PreviewEditor(input)
        }

        validation?.let {
            StatusBanner(
                title = stringResource(Res.string.http_validation_fix),
                message = it,
                tone = StatusTone.ERROR,
            )
        } ?: StatusBanner(
            title = stringResource(Res.string.http_validation_ready),
            message = stringResource(Res.string.http_secret_memory_notice),
            tone = StatusTone.SUCCESS,
        )

        ResponsiveActions {
            Button(
                onClick = { onExecute(input) },
                enabled = !state.isBusy && validation == null,
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Text(stringResource(Res.string.http_send), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onClear, enabled = !state.isBusy) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                Text(stringResource(Res.string.http_clear_request), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun QueryEditor(input: HttpRequestInput, onInput: (HttpRequestInput) -> Unit) {
    OutlinedTextField(
        value = input.queryText,
        onValueChange = { onInput(input.copy(queryText = it)) },
        label = { Text(stringResource(Res.string.http_query_params)) },
        supportingText = { Text(stringResource(Res.string.http_query_hint)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 5,
        maxLines = 14,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun HeaderEditor(state: AppState, input: HttpRequestInput, onInput: (HttpRequestInput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = input.headersText,
            onValueChange = { onInput(input.copy(headersText = it)) },
            label = { Text(stringResource(Res.string.headers_4b48800b)) },
            supportingText = { Text(stringResource(Res.string.http_headers_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            maxLines = 14,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        )
        SecretTextField(
            value = input.cookieText,
            onValueChange = { onInput(input.copy(cookieText = it)) },
            label = stringResource(Res.string.http_cookies),
            supporting = if (state.capabilities.manualCookieHeader) {
                stringResource(Res.string.http_cookies_hint)
            } else {
                stringResource(Res.string.http_cookie_unsupported)
            },
            enabled = state.capabilities.manualCookieHeader,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AuthenticationEditor(input: HttpRequestInput, onInput: (HttpRequestInput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.http_authentication_type), style = MaterialTheme.typography.labelLarge)
        ResponsiveActions {
            HttpAuthType.entries.forEach { type ->
                FilterChip(
                    selected = input.authType == type,
                    onClick = { onInput(input.copy(authType = type)) },
                    label = { Text(type.localizedLabel()) },
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                )
            }
        }
        Text(
            input.authType.localizedDescription(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (input.authType) {
            HttpAuthType.NONE -> Unit
            HttpAuthType.BASIC -> ResponsiveFields(
                fields = *arrayOf(
                    { modifier ->
                        OutlinedTextField(
                            value = input.authUsername,
                            onValueChange = { onInput(input.copy(authUsername = it)) },
                            label = { Text(stringResource(Res.string.http_username)) },
                            modifier = modifier,
                            singleLine = true,
                        )
                    },
                    { modifier ->
                        SecretTextField(
                            value = input.authPassword,
                            onValueChange = { onInput(input.copy(authPassword = it)) },
                            label = stringResource(Res.string.http_password),
                            modifier = modifier,
                        )
                    },
                ),
            )
            HttpAuthType.BEARER -> SecretTextField(
                value = input.bearerToken,
                onValueChange = { onInput(input.copy(bearerToken = it)) },
                label = stringResource(Res.string.http_bearer_token),
                modifier = Modifier.fillMaxWidth(),
            )
            HttpAuthType.API_KEY -> ResponsiveFields(
                fields = *arrayOf(
                    { modifier ->
                        OutlinedTextField(
                            value = input.apiKeyName,
                            onValueChange = { onInput(input.copy(apiKeyName = it)) },
                            label = { Text(stringResource(Res.string.http_api_key_name)) },
                            modifier = modifier,
                            singleLine = true,
                        )
                    },
                    { modifier ->
                        SecretTextField(
                            value = input.apiKeyValue,
                            onValueChange = { onInput(input.copy(apiKeyValue = it)) },
                            label = stringResource(Res.string.http_api_key_value),
                            modifier = modifier,
                        )
                    },
                ),
            )
        }
        StatusBanner(
            title = stringResource(Res.string.secret_redaction_is_mandatory_f98c9b12),
            message = stringResource(Res.string.http_secret_memory_notice),
            tone = StatusTone.INFO,
        )
    }
}

@Composable
private fun BodyEditor(
    state: AppState,
    input: HttpRequestInput,
    onInput: (HttpRequestInput) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.http_request_body_mode), style = MaterialTheme.typography.labelLarge)
        ResponsiveActions {
            HttpBodyType.entries.forEach { type ->
                FilterChip(
                    selected = input.bodyType == type,
                    onClick = {
                        val defaultContentType = when (type) {
                            HttpBodyType.JSON -> "application/json"
                            HttpBodyType.RAW -> "text/plain"
                            HttpBodyType.FORM_URLENCODED -> "application/x-www-form-urlencoded"
                            HttpBodyType.NONE -> input.contentType
                        }
                        onInput(input.copy(bodyType = type, contentType = defaultContentType))
                    },
                    label = { Text(type.localizedLabel()) },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                )
            }
        }
        if (input.bodyType != HttpBodyType.NONE) {
            if (input.bodyType != HttpBodyType.FORM_URLENCODED) {
                OutlinedTextField(
                    value = input.contentType,
                    onValueChange = { onInput(input.copy(contentType = it)) },
                    label = { Text(stringResource(Res.string.http_content_type)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = input.body,
                onValueChange = { onInput(input.copy(body = it)) },
                label = { Text(stringResource(Res.string.request_body_bfc00e0e)) },
                supportingText = {
                    Text(
                        if (input.bodyType == HttpBodyType.FORM_URLENCODED) {
                            stringResource(Res.string.http_body_form_hint)
                        } else {
                            stringResource(Res.string.http_body_raw_hint)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 22,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            )
            ResponsiveActions {
                when (input.bodyType) {
                    HttpBodyType.JSON -> AssistChip(
                        onClick = { onInput(input.copy(body = "{\n  \"deviceId\": \"sensor-01\",\n  \"enabled\": true\n}")) },
                        label = { Text(stringResource(Res.string.http_json_template)) },
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    )
                    HttpBodyType.FORM_URLENCODED -> AssistChip(
                        onClick = { onInput(input.copy(body = "deviceId=sensor-01\nenabled=true")) },
                        label = { Text(stringResource(Res.string.http_form_template)) },
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    )
                    HttpBodyType.RAW -> AssistChip(
                        onClick = { onInput(input.copy(body = "MSA IoT Offline Toolbox")) },
                        label = { Text(stringResource(Res.string.http_text_template)) },
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    )
                    HttpBodyType.NONE -> Unit
                }
                state.templates.take(8).forEach { template ->
                    AssistChip(
                        onClick = {
                            onInput(
                                input.copy(
                                    body = template.content,
                                    contentType = template.contentType,
                                    bodyType = if (template.contentType.contains("json", ignoreCase = true)) {
                                        HttpBodyType.JSON
                                    } else {
                                        HttpBodyType.RAW
                                    },
                                ),
                            )
                        },
                        label = { Text(template.name) },
                    )
                }
            }
        } else {
            StatusBanner(
                title = stringResource(Res.string.http_body_none),
                message = stringResource(Res.string.http_body_none_desc),
                tone = StatusTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun AdvancedHttpEditor(
    state: AppState,
    input: HttpRequestInput,
    onInput: (HttpRequestInput) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResponsiveFields(
            fields = *arrayOf(
                { modifier ->
                    HttpTimeoutField(
                        value = input.timeoutMillis,
                        label = stringResource(Res.string.http_request_timeout),
                        modifier = modifier,
                    ) { onInput(input.copy(timeoutMillis = it)) }
                },
                { modifier ->
                    HttpTimeoutField(
                        value = input.connectTimeoutMillis,
                        label = stringResource(Res.string.http_connect_timeout),
                        modifier = modifier,
                        enabled = state.capabilities.httpConnectTimeout,
                        unsupported = !state.capabilities.httpConnectTimeout,
                    ) { onInput(input.copy(connectTimeoutMillis = it)) }
                },
                { modifier ->
                    HttpTimeoutField(
                        value = input.socketTimeoutMillis,
                        label = stringResource(Res.string.http_socket_timeout),
                        modifier = modifier,
                        enabled = state.capabilities.httpSocketTimeout,
                        unsupported = !state.capabilities.httpSocketTimeout,
                    ) { onInput(input.copy(socketTimeoutMillis = it)) }
                },
            ),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.http_follow_redirects), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.capabilities.manualHttpRedirects) {
                            stringResource(Res.string.http_follow_redirects_desc)
                        } else {
                            stringResource(Res.string.http_redirect_unsupported)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = input.followRedirects && state.capabilities.manualHttpRedirects,
                    onCheckedChange = if (state.capabilities.manualHttpRedirects) {
                        { onInput(input.copy(followRedirects = it)) }
                    } else {
                        null
                    },
                )
            }
        }
        if (input.followRedirects && state.capabilities.manualHttpRedirects) {
            StatusBanner(
                title = stringResource(Res.string.http_follow_redirects),
                message = stringResource(Res.string.http_follow_warning),
                tone = StatusTone.WARNING,
            )
        }
    }
}

@Composable
private fun PreviewEditor(input: HttpRequestInput) {
    val invalidRequestMessage = stringResource(Res.string.http_invalid_request_preview)
    var shell by remember { mutableStateOf(CurlShell.POSIX) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusBanner(
            title = stringResource(Res.string.http_request_preview),
            message = stringResource(Res.string.http_curl_redacted),
            tone = StatusTone.INFO,
        )
        ResponsiveActions {
            FilterChip(
                selected = shell == CurlShell.POSIX,
                onClick = { shell = CurlShell.POSIX },
                label = { Text(stringResource(Res.string.http_preview_bash)) },
            )
            FilterChip(
                selected = shell == CurlShell.POWERSHELL,
                onClick = { shell = CurlShell.POWERSHELL },
                label = { Text(stringResource(Res.string.http_preview_powershell)) },
            )
        }
        CodeSurface(
            text = runCatching { HttpRequestPreview.curl(input, redactSecrets = true, shell = shell) }
                .getOrElse { it.message ?: invalidRequestMessage },
            maxHeight = 420.dp,
        )
    }
}

@Composable
private fun HttpResponseCard(result: HttpResponseResult, modifier: Modifier) {
    var view by remember(result) {
        mutableStateOf(if (result.bodyIsText) HttpResponseView.PRETTY else HttpResponseView.HEX)
    }
    val successful = result.error == null && result.statusCode in 200..299
    val responseBody = remember(result.body, result.bodyBytes, result.contentType, view) {
        when (view) {
            HttpResponseView.PRETTY -> HttpRequestPreview.prettyBody(result.body, result.contentType)
            HttpResponseView.TEXT -> result.body
            HttpResponseView.HEX -> HttpResponseBodyTools.toHex(result.bodyBytes)
            HttpResponseView.BASE64 -> HttpResponseBodyTools.toBase64(result.bodyBytes)
        }
    }

    SectionCard(
        title = stringResource(Res.string.http_response_overview),
        modifier = modifier,
        action = {
            InfoPill(
                label = if (result.statusCode > 0) "${result.statusCode} ${result.statusText}" else result.statusText,
                icon = if (successful) Icons.Outlined.Http else Icons.Outlined.ErrorOutline,
            )
        },
    ) {
        result.error?.let {
            StatusBanner(
                title = stringResource(Res.string.http_request_failed),
                message = it,
                tone = StatusTone.ERROR,
            )
        } ?: StatusBanner(
            title = if (successful) stringResource(Res.string.http_success) else stringResource(Res.string.http_non_success),
            message = "${result.requestMethod} · ${result.elapsedMillis} ms · ${result.bytesReceived} B",
            tone = if (successful) StatusTone.SUCCESS else StatusTone.WARNING,
        )
        result.securityWarning?.let {
            StatusBanner(
                title = stringResource(Res.string.http_response_security_warning),
                message = it,
                tone = StatusTone.WARNING,
            )
        }
        if (!result.bodyIsText && result.bodyBytes.isNotEmpty()) {
            StatusBanner(
                title = stringResource(Res.string.http_binary_body),
                message = stringResource(Res.string.http_binary_body_desc),
                tone = StatusTone.INFO,
            )
        }

        KeyValueRow(stringResource(Res.string.http_status), "${result.statusCode} ${result.statusText}")
        KeyValueRow(stringResource(Res.string.http_duration), "${result.elapsedMillis} ms")
        KeyValueRow(stringResource(Res.string.http_size), "${result.bytesReceived} B")
        KeyValueRow(stringResource(Res.string.http_requested_url), result.requestUrl, monospaced = true)
        KeyValueRow(stringResource(Res.string.http_final_url), result.finalUrl, monospaced = true)
        KeyValueRow(
            stringResource(Res.string.http_redirected),
            if (result.redirected) stringResource(Res.string.http_redirect_yes) else stringResource(Res.string.http_redirect_no),
        )
        KeyValueRow(stringResource(Res.string.http_content_type_result), result.contentType ?: "—", monospaced = true)
        if (result.truncated) {
            StatusBanner(
                title = stringResource(Res.string.http_truncated_body),
                message = stringResource(Res.string.response_and_history_previews_are_intentionally_bounded_10fd65a5),
                tone = StatusTone.WARNING,
            )
        }

        SectionDivider()
        Text(stringResource(Res.string.http_response_headers), style = MaterialTheme.typography.titleSmall)
        CodeSurface(
            text = result.headers.entries
                .sortedBy { it.key.lowercase() }
                .joinToString("\n") { (key, value) -> "$key: $value" }
                .ifBlank { stringResource(Res.string.http_no_headers) },
            maxHeight = 240.dp,
        )

        SectionDivider()
        ResponsiveActions {
            if (result.bodyIsText) {
                FilterChip(
                    selected = view == HttpResponseView.PRETTY,
                    onClick = { view = HttpResponseView.PRETTY },
                    label = { Text(stringResource(Res.string.http_pretty_response)) },
                )
                FilterChip(
                    selected = view == HttpResponseView.TEXT,
                    onClick = { view = HttpResponseView.TEXT },
                    label = { Text(stringResource(Res.string.http_response_text)) },
                )
            }
            FilterChip(
                selected = view == HttpResponseView.HEX,
                onClick = { view = HttpResponseView.HEX },
                label = { Text(stringResource(Res.string.http_response_hex)) },
            )
            FilterChip(
                selected = view == HttpResponseView.BASE64,
                onClick = { view = HttpResponseView.BASE64 },
                label = { Text(stringResource(Res.string.http_response_base64)) },
            )
        }
        CodeSurface(
            text = responseBody.ifBlank { stringResource(Res.string.http_body_empty) },
            maxHeight = 560.dp,
        )
    }
}

@Composable
private fun HttpEditorTab.localizedLabel(): String = when (this) {
    HttpEditorTab.QUERY -> stringResource(Res.string.http_tab_query)
    HttpEditorTab.HEADERS -> stringResource(Res.string.http_tab_headers)
    HttpEditorTab.AUTH -> stringResource(Res.string.http_tab_auth)
    HttpEditorTab.BODY -> stringResource(Res.string.http_tab_body)
    HttpEditorTab.ADVANCED -> stringResource(Res.string.http_tab_advanced)
    HttpEditorTab.PREVIEW -> stringResource(Res.string.http_tab_preview)
}

@Composable
private fun HttpAuthType.localizedLabel(): String = when (this) {
    HttpAuthType.NONE -> stringResource(Res.string.http_auth_none)
    HttpAuthType.BASIC -> stringResource(Res.string.http_auth_basic)
    HttpAuthType.BEARER -> stringResource(Res.string.http_auth_bearer)
    HttpAuthType.API_KEY -> stringResource(Res.string.http_auth_api_key)
}

@Composable
private fun HttpAuthType.localizedDescription(): String = when (this) {
    HttpAuthType.NONE -> stringResource(Res.string.http_auth_none_desc)
    HttpAuthType.BASIC -> stringResource(Res.string.http_basic_desc)
    HttpAuthType.BEARER -> stringResource(Res.string.http_bearer_desc)
    HttpAuthType.API_KEY -> stringResource(Res.string.http_api_key_desc)
}

@Composable
private fun HttpBodyType.localizedLabel(): String = when (this) {
    HttpBodyType.NONE -> stringResource(Res.string.http_body_none)
    HttpBodyType.RAW -> stringResource(Res.string.http_body_raw)
    HttpBodyType.JSON -> stringResource(Res.string.http_body_json)
    HttpBodyType.FORM_URLENCODED -> stringResource(Res.string.http_body_form)
}

@Composable
private fun HttpTimeoutField(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unsupported: Boolean = false,
    onValue: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw -> onValue(raw.filter(Char::isDigit).toIntOrNull() ?: 0) },
        label = { Text(label) },
        supportingText = {
            Text(
                stringResource(
                    if (unsupported) Res.string.http_timeout_unsupported_by_engine
                    else Res.string.http_timeout_range,
                ),
            )
        },
        enabled = enabled,
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    var revealed by remember { mutableStateOf(false) }
    val supportingContent: (@Composable () -> Unit)? = supporting?.let { text -> { Text(text) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingContent,
        enabled = enabled,
        singleLine = supporting == null,
        minLines = if (supporting == null) 1 else 3,
        maxLines = if (supporting == null) 1 else 8,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                Icon(
                    imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(
                        if (revealed) Res.string.http_hide_secret else Res.string.http_show_secret,
                    ),
                )
            }
        },
        modifier = modifier,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun validateHttpInput(state: AppState, input: HttpRequestInput): String? {
    val issue = HttpRequestValidator.firstIssue(
        input = input,
        policy = HttpRequestValidationPolicy(
            allowPublicCleartext = state.settings.allowPublicCleartext && state.capabilities.publicCleartextOverride,
            manualRedirects = state.capabilities.manualHttpRedirects,
            manualCookieHeader = state.capabilities.manualCookieHeader,
            browserManagedRequestHeaders = state.capabilities.browserManagedRequestHeaders,
        ),
    ) ?: return null
    return issue.localizedMessage()
}

@Composable
private fun HttpRequestValidationIssue.localizedMessage(): String = when (this) {
    HttpRequestValidationIssue.INVALID_URL -> stringResource(Res.string.http_invalid_url)
    HttpRequestValidationIssue.PUBLIC_CLEARTEXT_BLOCKED -> stringResource(Res.string.http_public_cleartext_blocked)
    HttpRequestValidationIssue.UNSUPPORTED_METHOD -> stringResource(Res.string.http_unsupported_method)
    HttpRequestValidationIssue.INVALID_TIMEOUT -> stringResource(Res.string.http_invalid_timeout)
    HttpRequestValidationIssue.REDIRECT_UNSUPPORTED -> stringResource(Res.string.http_redirect_unsupported)
    HttpRequestValidationIssue.INVALID_HEADERS -> stringResource(Res.string.http_invalid_headers)
    HttpRequestValidationIssue.RESTRICTED_HEADERS -> stringResource(Res.string.http_restricted_headers)
    HttpRequestValidationIssue.BROWSER_FORBIDDEN_HEADERS -> stringResource(Res.string.http_browser_forbidden_headers)
    HttpRequestValidationIssue.COOKIE_UNSUPPORTED -> stringResource(Res.string.http_cookie_unsupported)
    HttpRequestValidationIssue.DUPLICATE_COOKIE -> stringResource(Res.string.http_invalid_cookies)
    HttpRequestValidationIssue.DUPLICATE_CONTENT_TYPE -> stringResource(Res.string.http_duplicate_content_type)
    HttpRequestValidationIssue.INVALID_QUERY -> stringResource(Res.string.http_invalid_query)
    HttpRequestValidationIssue.INVALID_COOKIES -> stringResource(Res.string.http_invalid_cookies)
    HttpRequestValidationIssue.INVALID_AUTH -> stringResource(Res.string.http_invalid_auth)
    HttpRequestValidationIssue.INVALID_BODY -> stringResource(Res.string.http_invalid_body)
    HttpRequestValidationIssue.REQUEST_BODY_TOO_LARGE -> stringResource(Res.string.http_request_body_too_large)
}

private fun isHttpUrlValid(url: String): Boolean = runCatching {
    TransportSecurity.requireScheme(
        rawUrl = url,
        allowedSchemes = setOf("http", "https"),
        label = "HTTP URL",
    )
}.isSuccess

package com.msa.iotofflinetoolbox.core.data

import com.msa.iotofflinetoolbox.core.model.APP_VERSION
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.BackupInspection
import com.msa.iotofflinetoolbox.core.model.BackupMetadata
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.core.model.ImportSummary
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.TOOLBOX_BACKUP_VERSION
import com.msa.iotofflinetoolbox.core.model.ToolLog
import com.msa.iotofflinetoolbox.core.model.ToolboxBackup
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class StorageRecoveryEvent(
    val key: String,
    val quarantineKey: String?,
    val message: String,
)

/** Local-first persistence and versioned JSON backup boundary. */
class ToolboxRepository(
    private val settings: Settings = Settings(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
        explicitNulls = false
    },
) {
    private val recoveryEvents = mutableListOf<StorageRecoveryEvent>()
    private val prettyJson = Json(json) { prettyPrint = true }

    fun drainRecoveryEvents(): List<StorageRecoveryEvent> = recoveryEvents.toList().also { recoveryEvents.clear() }

    fun loadSettings(): AppSettings = (
        decodeOrNull<AppSettings>(KEY_SETTINGS)
            ?: decodeOrNull<AppSettings>(LEGACY_SETTINGS)
            ?: AppSettings()
        ).normalize()
    fun saveSettings(value: AppSettings) = encode(KEY_SETTINGS, value.normalize())

    fun loadDevices(): List<SavedDevice> =
        decodeOrNull<List<SavedDevice>>(KEY_DEVICES, ::validateDevices)
            ?: decodeOrNull<List<SavedDevice>>(LEGACY_DEVICES, ::validateDevices)
            ?: emptyList()
    fun saveDevices(value: List<SavedDevice>) {
        validateDevices(value)
        encode(KEY_DEVICES, value)
    }

    fun loadLogs(): List<ToolLog> =
        decodeOrNull<List<ToolLog>>(KEY_LOGS, ::validateLogs)
            ?: decodeOrNull<List<ToolLog>>(LEGACY_LOGS, ::validateLogs)
            ?: emptyList()
    fun saveLogs(value: List<ToolLog>) {
        validateLogs(value)
        encode(KEY_LOGS, value)
    }

    fun loadProfiles(): List<EndpointProfile> =
        decodeOrNull<List<EndpointProfile>>(KEY_PROFILES, ::validateProfiles) ?: emptyList()
    fun saveProfiles(value: List<EndpointProfile>) {
        validateProfiles(value)
        encode(KEY_PROFILES, value)
    }

    fun loadTemplates(): List<PayloadTemplate> =
        decodeOrNull<List<PayloadTemplate>>(KEY_TEMPLATES, ::validateTemplates) ?: defaultTemplates()
    fun saveTemplates(value: List<PayloadTemplate>) {
        validateTemplates(value)
        encode(KEY_TEMPLATES, value)
    }

    fun loadHistory(): List<HistoryEntry> =
        decodeOrNull<List<HistoryEntry>>(KEY_HISTORY, ::validateHistory) ?: emptyList()
    fun saveHistory(value: List<HistoryEntry>) {
        validateHistory(value)
        encode(KEY_HISTORY, value)
    }

    fun exportBackup(includeLogs: Boolean): String {
        val devices = loadDevices().map { it.redacted() }
        val profiles = loadProfiles().map { it.redacted() }
        val templates = loadTemplates().map { it.redacted() }
        val history = loadHistory().map { it.redacted() }
        val logs = if (includeLogs) loadLogs().map { it.redacted() } else emptyList()
        val encoded = prettyJson.encodeToString(
            ToolboxBackup(
                exportedAtMillis = Clock.System.now().toEpochMilliseconds(),
                settings = loadSettings(),
                devices = devices,
                profiles = profiles,
                templates = templates,
                history = history,
                logs = logs,
                metadata = BackupMetadata(
                    applicationVersion = APP_VERSION,
                    secretsRedacted = true,
                    deviceCount = devices.size,
                    profileCount = profiles.size,
                    templateCount = templates.size,
                    historyCount = history.size,
                    logCount = logs.size,
                ),
            ),
        )
        require(encoded.encodeToByteArray().size <= MAX_BACKUP_BYTES) { "Generated backup exceeds the 8 MiB safety limit" }
        return encoded
    }

    fun inspectBackup(raw: String): BackupInspection {
        if (raw.isBlank()) {
            return BackupInspection(0, 0, null, 0, 0, 0, 0, 0, false, "Backup JSON cannot be empty")
        }
        return runCatching {
            val backup = decodeAndValidate(raw)
            BackupInspection(
                schemaVersion = backup.schemaVersion,
                exportedAtMillis = backup.exportedAtMillis,
                applicationVersion = backup.metadata?.applicationVersion,
                devices = backup.devices.size,
                profiles = backup.profiles.size,
                templates = backup.templates.size,
                history = backup.history.size,
                logs = backup.logs.size,
                compatible = true,
                message = "Compatible backup. Choose Merge to preserve local records or Replace to overwrite them.",
            )
        }.getOrElse { error ->
            BackupInspection(0, 0, null, 0, 0, 0, 0, 0, false, SecretRedactor.redact(error.message ?: "Invalid backup"))
        }
    }

    fun importBackup(raw: String, mode: BackupImportMode): ImportSummary {
        val backup = decodeAndValidate(raw)
        val incomingSettings = backup.settings.normalize().copy(
            // A shared backup must not silently enable an unsafe public-cleartext override.
            allowPublicCleartext = false,
        )
        val incomingDevices = backup.devices.map { it.normalizeForImport() }.distinctBy { it.id }
        val incomingProfiles = backup.profiles.map { it.normalizeForImport() }.distinctBy { it.id }
        val incomingTemplates = backup.templates.map { it.normalizeForImport() }.distinctBy { it.id }
        val incomingHistory = backup.history.map { it.redacted().normalizeForImport() }.distinctBy { it.id }
        val incomingLogs = backup.logs.map { it.redacted().normalizeForImport() }.distinctBy { it.id }

        val targetSettings = if (mode == BackupImportMode.REPLACE) incomingSettings else loadSettings()
        val devices = combine(loadDevices(), incomingDevices, mode) { it.id }
        val profiles = combine(loadProfiles(), incomingProfiles, mode) { it.id }
        val templates = combine(loadTemplates(), incomingTemplates, mode) { it.id }
        val history = combine(loadHistory(), incomingHistory, mode) { it.id }
            .sortedByDescending { it.timestampMillis }
            .take(targetSettings.retainHistory)
        val logs = combine(loadLogs(), incomingLogs, mode) { it.id }
            .sortedByDescending { it.timestampMillis }
            .take(targetSettings.retainLogs)

        // Every collection is parsed, normalized and bounded before the first durable write.
        val previousSettings = loadSettings()
        val previousDevices = loadDevices()
        val previousProfiles = loadProfiles()
        val previousTemplates = loadTemplates()
        val previousHistory = loadHistory()
        val previousLogs = loadLogs()
        try {
            if (mode == BackupImportMode.REPLACE) saveSettings(targetSettings)
            saveDevices(devices)
            saveProfiles(profiles)
            saveTemplates(templates)
            saveHistory(history)
            saveLogs(logs)
        } catch (writeFailure: Exception) {
            // Attempt every restore independently. One failed platform write must not prevent
            // the remaining collections from being returned to their previous snapshots.
            val rollbackFailures = rollbackImportSnapshot(
                settings = previousSettings,
                devices = previousDevices,
                profiles = previousProfiles,
                templates = previousTemplates,
                history = previousHistory,
                logs = previousLogs,
            )
            if (rollbackFailures.isNotEmpty()) {
                throw IllegalStateException(
                    "Backup import failed and rollback was incomplete for: ${rollbackFailures.joinToString()}; " +
                        "original error: ${writeFailure.message ?: writeFailure::class.simpleName}",
                )
            }
            throw writeFailure
        }

        return ImportSummary(devices.size, profiles.size, templates.size, history.size, logs.size, mode)
    }


    private fun rollbackImportSnapshot(
        settings: AppSettings,
        devices: List<SavedDevice>,
        profiles: List<EndpointProfile>,
        templates: List<PayloadTemplate>,
        history: List<HistoryEntry>,
        logs: List<ToolLog>,
    ): List<String> {
        val failures = mutableListOf<String>()
        listOf(
            "settings" to { saveSettings(settings) },
            "devices" to { saveDevices(devices) },
            "profiles" to { saveProfiles(profiles) },
            "templates" to { saveTemplates(templates) },
            "history" to { saveHistory(history) },
            "logs" to { saveLogs(logs) },
        ).forEach { (name, restore) ->
            if (runCatching { restore() }.isFailure) failures += name
        }
        return failures
    }

    private fun decodeAndValidate(raw: String): ToolboxBackup {
        require(raw.isNotBlank()) { "Backup JSON cannot be empty" }
        require(raw.encodeToByteArray().size <= MAX_BACKUP_BYTES) { "Backup exceeds the 8 MiB safety limit" }
        val backup = json.decodeFromString<ToolboxBackup>(raw)
        require(backup.schemaVersion in 1..TOOLBOX_BACKUP_VERSION) {
            "Unsupported backup schema ${backup.schemaVersion}; this app supports up to $TOOLBOX_BACKUP_VERSION"
        }
        require(backup.exportedAtMillis > 0) { "Backup export timestamp is invalid" }
        require(backup.devices.size <= MAX_IMPORT_ITEMS) { "Backup contains too many devices" }
        require(backup.profiles.size <= MAX_IMPORT_ITEMS) { "Backup contains too many profiles" }
        require(backup.templates.size <= MAX_IMPORT_ITEMS) { "Backup contains too many templates" }
        require(backup.history.size <= MAX_IMPORT_ITEMS) { "Backup contains too many history entries" }
        require(backup.logs.size <= MAX_IMPORT_ITEMS) { "Backup contains too many logs" }
        backup.metadata?.let { metadata ->
            require(metadata.deviceCount == backup.devices.size) { "Backup metadata device count does not match its payload" }
            require(metadata.profileCount == backup.profiles.size) { "Backup metadata profile count does not match its payload" }
            require(metadata.templateCount == backup.templates.size) { "Backup metadata template count does not match its payload" }
            require(metadata.historyCount == backup.history.size) { "Backup metadata history count does not match its payload" }
            require(metadata.logCount == backup.logs.size) { "Backup metadata log count does not match its payload" }
        }
        return backup
    }

    private fun <T, K> combine(existing: List<T>, incoming: List<T>, mode: BackupImportMode, key: (T) -> K): List<T> =
        if (mode == BackupImportMode.REPLACE) incoming else (incoming + existing).associateBy(key).values.toList()

    private inline fun <reified T> decodeOrNull(
        key: String,
        validate: (T) -> Unit = {},
    ): T? {
        var raw: String? = null
        return try {
            raw = settings[key]
            if (raw.isNullOrBlank()) {
                null
            } else {
                require(raw.encodeToByteArray().size <= MAX_LOCAL_STORAGE_BYTES) {
                    "Stored JSON for $key exceeds the local storage safety limit"
                }
                json.decodeFromString<T>(raw).also(validate)
            }
        } catch (error: Exception) {
            val quarantineKey = "$QUARANTINE_PREFIX$key.latest"
            val corruptRaw = raw
            val quarantined = if (corruptRaw.isNullOrBlank()) {
                false
            } else {
                runCatching {
                    settings[quarantineKey] = SecretRedactor.redact(corruptRaw.take(MAX_CORRUPT_SNAPSHOT_CHARS))
                    true
                }.getOrDefault(false)
            }
            recoveryEvents += StorageRecoveryEvent(
                key = key,
                quarantineKey = quarantineKey.takeIf { quarantined },
                message = SecretRedactor.redact(error.message ?: "Stored JSON could not be read or decoded"),
            )
            null
        }
    }

    private inline fun <reified T> encode(key: String, value: T) {
        val encoded = json.encodeToString(value)
        require(encoded.encodeToByteArray().size <= MAX_LOCAL_STORAGE_BYTES) {
            "Serialized value for $key exceeds the local storage safety limit"
        }
        settings[key] = encoded
    }

    private fun validateDevices(value: List<SavedDevice>) {
        require(value.size <= MAX_IMPORT_ITEMS) { "Device collection exceeds the item safety limit" }
        value.forEach { item ->
            require(item.id.isNotBlank() && item.host.isNotBlank()) { "Device id and host cannot be empty" }
            require(item.lastSeenMillis >= 0) { "Device last-seen timestamp cannot be negative" }
            require(item.openPorts.all { it in 1..65_535 }) { "Device contains an invalid open port" }
            require(item.id.length <= MAX_ID_LENGTH && item.host.length <= MAX_ENDPOINT_LENGTH && item.displayName.length <= MAX_NAME_LENGTH) {
                "Device identity or endpoint exceeds the field safety limit"
            }
            require(item.addresses.size <= MAX_CHILD_ITEMS && item.services.size <= MAX_CHILD_ITEMS && item.tags.size <= MAX_CHILD_ITEMS && item.openPorts.size <= MAX_CHILD_ITEMS) {
                "Device child collection exceeds the safety limit"
            }
            require(item.addresses.all { it.length <= MAX_ENDPOINT_LENGTH } && item.services.all { it.length <= MAX_NAME_LENGTH } && item.tags.all { it.length <= MAX_NAME_LENGTH } && item.note.length <= MAX_NOTES_LENGTH) {
                "Device metadata exceeds the field safety limit"
            }
        }
    }

    private fun validateProfiles(value: List<EndpointProfile>) {
        require(value.size <= MAX_IMPORT_ITEMS) { "Profile collection exceeds the item safety limit" }
        value.forEach { item ->
            require(item.id.isNotBlank() && item.name.isNotBlank() && item.hostOrUrl.isNotBlank()) {
                "Profile id, name and endpoint cannot be empty"
            }
            require(item.createdAtMillis >= 0 && item.updatedAtMillis >= 0) { "Profile timestamps cannot be negative" }
            require(item.id.length <= MAX_ID_LENGTH && item.name.length <= MAX_NAME_LENGTH && item.hostOrUrl.length <= MAX_ENDPOINT_LENGTH) {
                "Profile identity or endpoint exceeds the field safety limit"
            }
            require(item.username.length <= MAX_NAME_LENGTH && item.defaultTopic.length <= MAX_ENDPOINT_LENGTH && item.headersText.length <= MAX_HEADER_BLOCK_LENGTH && item.notes.length <= MAX_NOTES_LENGTH) {
                "Profile metadata exceeds the field safety limit"
            }
            require(item.tags.size <= MAX_CHILD_ITEMS && item.tags.all { it.length <= MAX_NAME_LENGTH }) { "Profile tags exceed the safety limit" }
            require(item.port == null || item.port in 1..65_535) { "Profile port must be between 1 and 65535" }
        }
    }

    private fun validateTemplates(value: List<PayloadTemplate>) {
        require(value.size <= MAX_IMPORT_ITEMS) { "Template collection exceeds the item safety limit" }
        value.forEach { item ->
            require(item.id.isNotBlank() && item.name.isNotBlank() && item.content.isNotBlank()) {
                "Template id, name and content cannot be empty"
            }
            require(item.createdAtMillis >= 0 && item.updatedAtMillis >= 0) { "Template timestamps cannot be negative" }
            require(item.id.length <= MAX_ID_LENGTH && item.name.length <= MAX_NAME_LENGTH && item.contentType.length <= MAX_NAME_LENGTH && item.notes.length <= MAX_NOTES_LENGTH) {
                "Template metadata exceeds the field safety limit"
            }
            require(item.content.encodeToByteArray().size <= MAX_TEMPLATE_LENGTH) { "Template content exceeds the 1 MiB safety limit" }
        }
    }

    private fun validateHistory(value: List<HistoryEntry>) {
        require(value.size <= MAX_IMPORT_ITEMS) { "History collection exceeds the item safety limit" }
        value.forEach { item ->
            require(item.id.isNotBlank() && item.timestampMillis >= 0) { "History id cannot be empty and timestamp cannot be negative" }
            require(item.id.length <= MAX_ID_LENGTH && item.target.length <= MAX_ENDPOINT_LENGTH && item.summary.length <= MAX_NOTES_LENGTH) {
                "History metadata exceeds the field safety limit"
            }
            require(item.requestPreview.length <= MAX_HISTORY_LENGTH && item.responsePreview.length <= MAX_HISTORY_LENGTH) {
                "History preview exceeds the field safety limit"
            }
        }
    }

    private fun validateLogs(value: List<ToolLog>) {
        require(value.size <= MAX_IMPORT_ITEMS) { "Log collection exceeds the item safety limit" }
        value.forEach { item ->
            require(item.id.isNotBlank() && item.timestampMillis >= 0) { "Log id cannot be empty and timestamp cannot be negative" }
            require(item.id.length <= MAX_ID_LENGTH && item.source.length <= MAX_NAME_LENGTH && item.message.length <= MAX_LOG_LENGTH) {
                "Log entry exceeds the field safety limit"
            }
        }
    }

    private fun SavedDevice.redacted(): SavedDevice = copy(
        host = SecretRedactor.redact(host),
        displayName = SecretRedactor.redact(displayName),
        addresses = addresses.map(SecretRedactor::redact),
        services = services.map(SecretRedactor::redact),
        note = SecretRedactor.redact(note),
        tags = tags.map(SecretRedactor::redact),
    )

    private fun EndpointProfile.redacted(): EndpointProfile {
        val headers = SecretRedactor.removeSensitiveHeaders(headersText)
        return copy(
            name = SecretRedactor.redact(name),
            hostOrUrl = SecretRedactor.redact(hostOrUrl),
            username = SecretRedactor.redact(username),
            defaultTopic = SecretRedactor.redact(defaultTopic),
            headersText = headers.value,
            notes = SecretRedactor.redact(notes),
            tags = tags.map(SecretRedactor::redact),
        )
    }

    private fun PayloadTemplate.redacted(): PayloadTemplate = copy(
        name = SecretRedactor.redact(name),
        content = SecretRedactor.redact(content),
        contentType = SecretRedactor.redact(contentType),
        notes = SecretRedactor.redact(notes),
    )

    private fun ToolLog.redacted(): ToolLog = copy(
        source = SecretRedactor.redact(source),
        message = SecretRedactor.redact(message),
    )

    private fun SavedDevice.normalizeForImport(): SavedDevice {
        require(id.isNotBlank()) { "Device id cannot be empty" }
        require(host.isNotBlank()) { "Device host cannot be empty" }
        return copy(
            id = id.take(MAX_ID_LENGTH),
            host = SecretRedactor.redact(host.trim()).take(MAX_ENDPOINT_LENGTH),
            displayName = SecretRedactor.redact(displayName.ifBlank { host }.trim()).take(MAX_NAME_LENGTH),
            addresses = addresses.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(MAX_ENDPOINT_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
            openPorts = openPorts.filter { it in 1..65_535 }.distinct().sorted().take(MAX_CHILD_ITEMS),
            services = services.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(MAX_NAME_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
            note = SecretRedactor.redact(note).take(MAX_NOTES_LENGTH),
            tags = tags.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(MAX_NAME_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
        )
    }

    private fun EndpointProfile.normalizeForImport(): EndpointProfile {
        require(id.isNotBlank()) { "Profile id cannot be empty" }
        require(name.isNotBlank()) { "Profile name cannot be empty" }
        require(hostOrUrl.isNotBlank()) { "Profile endpoint cannot be empty" }
        val headers = SecretRedactor.removeSensitiveHeaders(headersText)
        return copy(
            id = id.take(MAX_ID_LENGTH),
            name = SecretRedactor.redact(name.trim()).take(MAX_NAME_LENGTH),
            hostOrUrl = SecretRedactor.redact(hostOrUrl.trim()).take(MAX_ENDPOINT_LENGTH),
            port = port?.takeIf { it in 1..65_535 },
            username = SecretRedactor.redact(username).take(MAX_NAME_LENGTH),
            defaultTopic = SecretRedactor.redact(defaultTopic).take(MAX_ENDPOINT_LENGTH),
            headersText = headers.value.take(MAX_HEADER_BLOCK_LENGTH),
            notes = SecretRedactor.redact(notes).take(MAX_NOTES_LENGTH),
            tags = tags.map(SecretRedactor::redact).map(String::trim).filter(String::isNotBlank).map { it.take(MAX_NAME_LENGTH) }.distinct().take(MAX_CHILD_ITEMS),
        )
    }

    private fun PayloadTemplate.normalizeForImport(): PayloadTemplate {
        require(id.isNotBlank()) { "Template id cannot be empty" }
        require(name.isNotBlank()) { "Template name cannot be empty" }
        require(content.isNotBlank()) { "Template content cannot be empty" }
        val redactedContent = SecretRedactor.redact(content)
        require(redactedContent.encodeToByteArray().size <= MAX_TEMPLATE_LENGTH) {
            "Template content exceeds the 1 MiB safety limit"
        }
        return copy(
            id = id.take(MAX_ID_LENGTH),
            name = SecretRedactor.redact(name.trim()).take(MAX_NAME_LENGTH),
            content = redactedContent,
            contentType = SecretRedactor.redact(contentType).take(MAX_NAME_LENGTH),
            notes = SecretRedactor.redact(notes).take(MAX_NOTES_LENGTH),
        )
    }

    private fun ToolLog.normalizeForImport(): ToolLog = copy(
        id = id.take(MAX_ID_LENGTH),
        source = SecretRedactor.redact(source).take(MAX_NAME_LENGTH),
        message = SecretRedactor.redact(message).take(MAX_LOG_LENGTH),
    )

    private fun HistoryEntry.redacted(): HistoryEntry = copy(
        target = SecretRedactor.redact(target),
        summary = SecretRedactor.redact(summary),
        requestPreview = SecretRedactor.redact(requestPreview),
        responsePreview = SecretRedactor.redact(responsePreview),
    )

    private fun HistoryEntry.normalizeForImport(): HistoryEntry {
        require(id.isNotBlank()) { "History id cannot be empty" }
        return copy(
            id = id.take(MAX_ID_LENGTH),
            target = target.take(MAX_ENDPOINT_LENGTH),
            summary = summary.take(MAX_NOTES_LENGTH),
            requestPreview = requestPreview.take(MAX_HISTORY_LENGTH),
            responsePreview = responsePreview.take(MAX_HISTORY_LENGTH),
        )
    }

    private fun defaultTemplates(): List<PayloadTemplate> {
        val now = Clock.System.now().toEpochMilliseconds()
        return listOf(
            PayloadTemplate(
                id = "template-telemetry",
                name = "Telemetry JSON",
                content = """{
  "deviceId": "{{uuid}}",
  "timestamp": {{timestamp}},
  "temperature": 23.4,
  "online": true
}""",
                notes = "Generic sensor telemetry with runtime variables.",
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
            PayloadTemplate(
                id = "template-command",
                name = "Device command",
                content = """{
  "command": "setState",
  "requestId": "{{uuid}}",
  "payload": {
    "enabled": true
  }
}""",
                notes = "Reusable JSON command envelope.",
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    private fun AppSettings.normalize(): AppSettings = copy(
        rtl = rtl || language == AppLanguage.PERSIAN,
        defaultTimeoutMillis = defaultTimeoutMillis.coerceIn(100, 60_000),
        defaultHttpTimeoutMillis = defaultHttpTimeoutMillis.coerceIn(100, 120_000),
        defaultHttpConnectTimeoutMillis = defaultHttpConnectTimeoutMillis.coerceIn(100, 120_000),
        defaultHttpSocketTimeoutMillis = defaultHttpSocketTimeoutMillis.coerceIn(100, 120_000),
        scanConcurrency = scanConcurrency.coerceIn(1, 256),
        maxPortScanItems = maxPortScanItems.coerceIn(1, 4_096),
        maxDiscoveryHosts = maxDiscoveryHosts.coerceIn(1, 4_096),
        maxResponseBytes = maxResponseBytes.coerceIn(1_024, 1_048_576),
        retainLogs = retainLogs.coerceIn(20, 2_000),
        retainHistory = retainHistory.coerceIn(20, 1_000),
    )

    private companion object {
        const val KEY_SETTINGS = "app.settings.v3"
        const val KEY_DEVICES = "saved.devices.v3"
        const val KEY_LOGS = "activity.logs.v3"
        const val KEY_PROFILES = "endpoint.profiles.v3"
        const val KEY_TEMPLATES = "payload.templates.v3"
        const val KEY_HISTORY = "request.history.v3"
        const val LEGACY_SETTINGS = "app.settings.v2"
        const val LEGACY_DEVICES = "saved.devices.v2"
        const val LEGACY_LOGS = "activity.logs.v2"
        const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
        const val MAX_LOCAL_STORAGE_BYTES = 8 * 1024 * 1024
        const val MAX_IMPORT_ITEMS = 5_000
        const val MAX_CHILD_ITEMS = 512
        const val MAX_ID_LENGTH = 160
        const val MAX_NAME_LENGTH = 256
        const val MAX_ENDPOINT_LENGTH = 2_048
        const val MAX_NOTES_LENGTH = 8_192
        const val MAX_HEADER_BLOCK_LENGTH = 65_536
        const val MAX_TEMPLATE_LENGTH = 1_048_576
        const val MAX_HISTORY_LENGTH = 16_384
        const val MAX_LOG_LENGTH = 4_096
        const val MAX_CORRUPT_SNAPSHOT_CHARS = 1_048_576
        const val QUARANTINE_PREFIX = "recovery.corrupt."
    }
}

package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.core.model.ToolLog
import com.msa.iotofflinetoolbox.core.port.ToolboxSnapshotReader
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/** Builds the initial immutable application snapshot from durable ports and recovery evidence. */
internal class ToolboxStateLoader(
    private val persistence: ToolboxSnapshotReader,
    private val runtime: ToolboxRuntime,
) {
    fun load(capabilities: PlatformCapabilities): AppState {
        // Load every collection before draining recovery events. The persistence adapter may discover
        // corruption while decoding any collection, and all resulting evidence belongs in this one
        // initial state snapshot.
        val settings = persistence.loadSettings()
        val devices = persistence.loadDevices()
        val profiles = persistence.loadProfiles()
        val templates = persistence.loadTemplates()
        val history = persistence.loadHistory()
        val existingLogs = persistence.loadLogs()
        val recoveryEvents = persistence.drainRecoveryEvents()

        val recoveryLogs = recoveryEvents.mapIndexed { index, event ->
            val redactedDetail = SecretRedactor.redact(event.message)
            val message = if (settings.language == AppLanguage.PERSIAN) {
                buildString {
                    append("داده محلی ناخوانا در ").append(event.key).append(" بازیابی شد")
                    event.quarantineKey?.let { append("؛ Snapshot با نام ").append(it).append(" ذخیره شد") }
                    append(": ").append(redactedDetail)
                }
            } else {
                buildString {
                    append("Recovered from unreadable local data at ").append(event.key)
                    event.quarantineKey?.let { append("; snapshot saved as ").append(it) }
                    append(": ").append(redactedDetail)
                }
            }
            val timestamp = runtime.clock.nowMillis()
            ToolLog(
                id = runtime.idGenerator.nextId("recovery-$index", timestamp),
                timestampMillis = timestamp,
                level = LogLevel.WARNING,
                source = if (settings.language == AppLanguage.PERSIAN) "بازیابی ذخیره‌سازی" else "Storage recovery",
                message = message.take(MAX_RECOVERY_LOG_MESSAGE),
            )
        }
        val logs = (recoveryLogs + existingLogs).take(settings.retainLogs)
        if (recoveryLogs.isNotEmpty()) runCatching { persistence.saveLogs(logs) }
        val recoveryNotice = recoveryEvents.takeIf { it.isNotEmpty() }?.let {
            if (settings.language == AppLanguage.PERSIAN) {
                "${it.size} رکورد محلی ناخوانا بازیابی شد و در صورت امکان Snapshot تشخیصی نگه‌داری شد."
            } else {
                "Recovered ${it.size} unreadable local data record(s). A diagnostic snapshot was preserved when storage allowed it."
            }
        }
        return AppState(
            currentScreen = settings.lastScreen,
            settings = settings,
            capabilities = capabilities,
            savedDevices = devices,
            profiles = profiles,
            templates = templates,
            history = history,
            logs = logs,
            feedback = recoveryNotice?.let { AppFeedback.notice(it, "Storage recovery") },
        )
    }

    private companion object {
        const val MAX_RECOVERY_LOG_MESSAGE = 2_000
    }
}

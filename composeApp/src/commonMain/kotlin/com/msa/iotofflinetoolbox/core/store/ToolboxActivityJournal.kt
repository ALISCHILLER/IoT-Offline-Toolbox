package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.security.SecretRedactor
import com.msa.iotofflinetoolbox.core.port.ActivityPersistence
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.core.model.HistoryProtocol
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.ToolLog

/**
 * Owns the activity journal policy: redaction, retention, persistence and storage-failure fallback.
 *
 * Keeping this policy outside [ToolboxStore] prevents network and inventory orchestration from
 * duplicating durable logging rules and gives the application one auditable history boundary.
 */
internal class ToolboxActivityJournal(
    private val persistence: ActivityPersistence,
    private val state: () -> AppState,
    private val updateState: (AppState.() -> AppState) -> Unit,
    private val messages: ToolboxMessages,
    private val runtime: ToolboxRuntime,
) {
    fun log(level: LogLevel, source: String, message: String) {
        val snapshot = state()
        val entry = ToolLog(
            id = newId("log"),
            timestampMillis = now(),
            level = level,
            source = messages.source(source),
            message = SecretRedactor.redact(message).take(MAX_LOG_MESSAGE),
        )
        val updated = (listOf(entry) + snapshot.logs).take(snapshot.settings.retainLogs)
        val persistenceError = persistenceFailure("Activity logs") { persistence.saveLogs(updated) }
        updateState {
            copy(
                logs = updated,
                feedback = persistenceError?.let { AppFeedback.error(it, "Activity logs") } ?: feedback,
            )
        }
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    fun addHistory(
        protocol: HistoryProtocol,
        target: String,
        summary: String,
        successful: Boolean,
        request: String = "",
        response: String = "",
        durationMillis: Long? = null,
    ) {
        val snapshot = state()
        val item = HistoryEntry(
            id = newId("history"),
            timestampMillis = now(),
            protocol = protocol,
            target = SecretRedactor.redact(target).take(MAX_TARGET),
            summary = SecretRedactor.redact(summary).take(MAX_SUMMARY),
            successful = successful,
            requestPreview = SecretRedactor.redact(request).take(MAX_HISTORY_PREVIEW),
            responsePreview = SecretRedactor.redact(response).take(MAX_HISTORY_PREVIEW),
            durationMillis = durationMillis,
        )
        val updated = (listOf(item) + snapshot.history).take(snapshot.settings.retainHistory)
        val persistenceError = persistenceFailure("Request history") { persistence.saveHistory(updated) }
        updateState {
            copy(
                history = updated,
                feedback = persistenceError?.let { AppFeedback.error(it, "Request history") } ?: feedback,
            )
        }
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    fun trimLogs() {
        val snapshot = state()
        val updated = snapshot.logs.take(snapshot.settings.retainLogs)
        val persistenceError = persistenceFailure("Activity logs") { persistence.saveLogs(updated) }
        updateState { copy(logs = updated, feedback = persistenceError?.let { AppFeedback.error(it, "Activity logs") } ?: feedback) }
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    fun trimHistory() {
        val snapshot = state()
        val updated = snapshot.history.take(snapshot.settings.retainHistory)
        val persistenceError = persistenceFailure("Request history") { persistence.saveHistory(updated) }
        updateState { copy(history = updated, feedback = persistenceError?.let { AppFeedback.error(it, "Request history") } ?: feedback) }
        persistenceError?.let(::appendEphemeralStorageLog)
    }

    fun clearLogs(): Boolean {
        val persistenceError = persistenceFailure("Activity logs") { persistence.saveLogs(emptyList()) }
        if (persistenceError != null) {
            reportPersistenceFailure(persistenceError)
            return false
        }
        updateState { copy(logs = emptyList()) }
        return true
    }

    fun clearHistory(): Boolean {
        val persistenceError = persistenceFailure("Request history") { persistence.saveHistory(emptyList()) }
        if (persistenceError != null) {
            reportPersistenceFailure(persistenceError)
            return false
        }
        updateState { copy(history = emptyList()) }
        return true
    }

    fun persistenceFailure(source: String, operation: () -> Unit): String? = try {
        operation()
        null
    } catch (error: Exception) {
        val unknown = messages.text("Unknown storage error", "خطای ناشناخته ذخیره‌سازی")
        val detail = SecretRedactor.redact(error.message ?: error::class.simpleName ?: unknown)
        val displaySource = messages.source(source)
        messages.text(
            "$displaySource could not be saved: $detail",
            "ذخیره $displaySource انجام نشد: $detail",
        )
    }

    fun reportPersistenceFailure(message: String) {
        updateState { copy(feedback = AppFeedback.error(message, "Storage")) }
        appendEphemeralStorageLog(message)
    }

    private fun appendEphemeralStorageLog(message: String) {
        val entry = ToolLog(
            id = newId("storage-error"),
            timestampMillis = now(),
            level = LogLevel.ERROR,
            source = messages.source("Storage"),
            message = SecretRedactor.redact(message).take(MAX_LOG_MESSAGE),
        )
        updateState {
            copy(logs = (listOf(entry) + logs).take(settings.retainLogs))
        }
    }

    private fun now(): Long = runtime.clock.nowMillis()
    private fun newId(prefix: String): String = runtime.idGenerator.nextId(prefix, now())

    private companion object {
        const val MAX_LOG_MESSAGE = 2_000
        const val MAX_HISTORY_PREVIEW = 4_000
        const val MAX_TARGET = 500
        const val MAX_SUMMARY = 1_000
    }
}

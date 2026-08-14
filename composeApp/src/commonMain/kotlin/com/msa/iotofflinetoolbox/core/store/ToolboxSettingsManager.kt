package com.msa.iotofflinetoolbox.core.store

import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.store.AppState
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.LogLevel
import com.msa.iotofflinetoolbox.core.model.TOOLBOX_BACKUP_VERSION
import com.msa.iotofflinetoolbox.core.port.SettingsBackupPersistence
import com.msa.iotofflinetoolbox.core.security.SecretRedactor

/** Application service for settings, backup, restore and retention operations. */
internal class ToolboxSettingsManager(
    private val persistence: SettingsBackupPersistence,
    private val updateState: (AppState.() -> AppState) -> Unit,
    private val messages: ToolboxMessages,
    private val journal: ToolboxActivityJournal,
) : SettingsAndBackupActions {
    override fun exportBackup(includeLogs: Boolean) {
        runCatching { persistence.exportBackup(includeLogs) }
            .onSuccess { backup ->
                updateState {
                    copy(
                        backupText = backup,
                        backupInspection = localizedInspection(backup),
                        feedback = AppFeedback.notice(
                            text("Redacted backup JSON generated locally", "نسخه پشتیبان پالایش‌شده به‌صورت محلی تولید شد"),
                            "Backup",
                        ),
                    )
                }
                journal.log(LogLevel.SUCCESS, "Backup", text("Generated schema v$TOOLBOX_BACKUP_VERSION backup", "نسخه پشتیبان با Schema v$TOOLBOX_BACKUP_VERSION تولید شد"))
            }
            .onFailure { fail("Backup export", it) }
    }

    override fun inspectBackup(raw: String) {
        updateState { copy(backupInspection = localizedInspection(raw), feedback = null) }
    }

    override fun importBackup(raw: String, mode: BackupImportMode) {
        runCatching { persistence.importBackup(raw, mode) }
            .onSuccess { summary ->
                reloadPersistentState()
                updateState {
                    copy(
                        backupInspection = localizedInspection(raw),
                        feedback = AppFeedback.notice(
                            text(
                                "${summary.mode.name.lowercase().replaceFirstChar { it.uppercase() }} complete: ${summary.devices} devices, ${summary.profiles} profiles, ${summary.templates} templates, ${summary.history} history entries and ${summary.logs} logs",
                                "عملیات ${summary.mode.name} کامل شد: ${summary.devices} دستگاه، ${summary.profiles} پروفایل، ${summary.templates} قالب، ${summary.history} سابقه و ${summary.logs} لاگ",
                            ),
                            "Backup",
                        ),
                    )
                }
                journal.log(LogLevel.SUCCESS, "Backup", text("Backup ${summary.mode.name.lowercase()} completed", "عملیات نسخه پشتیبان ${summary.mode.name} کامل شد"))
            }
            .onFailure { fail("Backup import", it) }
    }

    override fun updateSettings(settings: AppSettings) {
        journal.persistenceFailure("Application settings") { persistence.saveSettings(settings) }
            ?.let { return journal.reportPersistenceFailure(it) }
        val normalized = persistence.loadSettings()
        updateState { copy(settings = normalized) }
        journal.trimLogs()
        journal.trimHistory()
        journal.log(LogLevel.INFO, "Settings", text("Application settings updated", "تنظیمات برنامه به‌روزرسانی شد"))
    }

    override fun clearLogs() {
        journal.clearLogs()
    }

    override fun clearHistory() {
        if (!journal.clearHistory()) return
        journal.log(LogLevel.WARNING, "History", text("Request history cleared", "تاریخچه درخواست‌ها پاک شد"))
    }

    private fun reloadPersistentState() {
        updateState {
            copy(
                settings = persistence.loadSettings(),
                savedDevices = persistence.loadDevices(),
                profiles = persistence.loadProfiles(),
                templates = persistence.loadTemplates(),
                history = persistence.loadHistory(),
                logs = persistence.loadLogs(),
            )
        }
    }

    private fun localizedInspection(raw: String) =
        messages.localizeBackupInspection(raw, persistence.inspectBackup(raw))

    private fun fail(source: String, throwable: Throwable) {
        val unknown = text("Unknown error", "خطای ناشناخته")
        val message = throwable.message ?: throwable::class.simpleName ?: unknown
        updateState { copy(feedback = AppFeedback.error(SecretRedactor.redact(message), source)) }
        journal.log(LogLevel.ERROR, source, message)
    }

    private fun text(english: String, persian: String): String = messages.text(english, persian)
}

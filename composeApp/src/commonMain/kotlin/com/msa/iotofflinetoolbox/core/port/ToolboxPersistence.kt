package com.msa.iotofflinetoolbox.core.port

import com.msa.iotofflinetoolbox.core.model.AppSettings
import com.msa.iotofflinetoolbox.core.model.BackupImportMode
import com.msa.iotofflinetoolbox.core.model.BackupInspection
import com.msa.iotofflinetoolbox.core.model.EndpointProfile
import com.msa.iotofflinetoolbox.core.model.HistoryEntry
import com.msa.iotofflinetoolbox.core.model.ImportSummary
import com.msa.iotofflinetoolbox.core.model.PayloadTemplate
import com.msa.iotofflinetoolbox.core.model.SavedDevice
import com.msa.iotofflinetoolbox.core.model.ToolLog

/** Adapter-neutral description of a local record recovered from corrupt persistence. */
data class StorageRecoveryEvent(
    val key: String,
    val quarantineKey: String?,
    val message: String,
)

interface StorageRecoverySource {
    fun drainRecoveryEvents(): List<StorageRecoveryEvent>
}

interface SettingsPersistence {
    fun loadSettings(): AppSettings
    fun saveSettings(value: AppSettings)
}

interface DevicePersistence {
    fun loadDevices(): List<SavedDevice>
    fun saveDevices(value: List<SavedDevice>)
}

interface ProfilePersistence {
    fun loadProfiles(): List<EndpointProfile>
    fun saveProfiles(value: List<EndpointProfile>)
}

interface TemplatePersistence {
    fun loadTemplates(): List<PayloadTemplate>
    fun saveTemplates(value: List<PayloadTemplate>)
}

interface HistoryPersistence {
    fun loadHistory(): List<HistoryEntry>
    fun saveHistory(value: List<HistoryEntry>)
}

interface LogPersistence {
    fun loadLogs(): List<ToolLog>
    fun saveLogs(value: List<ToolLog>)
}

interface ActivityPersistence : HistoryPersistence, LogPersistence

interface BackupPersistence {
    fun exportBackup(includeLogs: Boolean): String
    fun inspectBackup(raw: String): BackupInspection
    fun importBackup(raw: String, mode: BackupImportMode): ImportSummary
}

/** Persistence surface legitimately required by settings and backup restore use-cases. */
interface SettingsBackupPersistence :
    SettingsPersistence,
    DevicePersistence,
    ProfilePersistence,
    TemplatePersistence,
    ActivityPersistence,
    BackupPersistence

/** Read-only aggregate used only while reconstructing an application snapshot. */
interface ToolboxSnapshotReader :
    StorageRecoverySource,
    SettingsPersistence,
    DevicePersistence,
    ProfilePersistence,
    TemplatePersistence,
    HistoryPersistence,
    LogPersistence

/**
 * Full persistence contract implemented by the outer storage adapter.
 *
 * Application services should depend on the narrow interfaces above. This aggregate exists for
 * composition and adapter conformance only; it must not become a shortcut around interface
 * segregation inside application code.
 */
interface ToolboxPersistence :
    ToolboxSnapshotReader,
    SettingsBackupPersistence

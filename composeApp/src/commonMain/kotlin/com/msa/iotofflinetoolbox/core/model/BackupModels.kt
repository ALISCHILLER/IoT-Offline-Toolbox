package com.msa.iotofflinetoolbox.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    val applicationVersion: String = APP_VERSION,
    val exportedBy: String = "MSA / ALISCHILLER",
    val secretsRedacted: Boolean = true,
    val deviceCount: Int = 0,
    val profileCount: Int = 0,
    val templateCount: Int = 0,
    val historyCount: Int = 0,
    val logCount: Int = 0,
)

@Serializable
data class ToolboxBackup(
    val schemaVersion: Int = TOOLBOX_BACKUP_VERSION,
    val exportedAtMillis: Long,
    val settings: AppSettings,
    val devices: List<SavedDevice>,
    val profiles: List<EndpointProfile>,
    val templates: List<PayloadTemplate>,
    val history: List<HistoryEntry>,
    val logs: List<ToolLog> = emptyList(),
    val metadata: BackupMetadata? = null,
)

@Serializable
enum class BackupImportMode { MERGE, REPLACE }

data class BackupInspection(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val applicationVersion: String?,
    val devices: Int,
    val profiles: Int,
    val templates: Int,
    val history: Int,
    val logs: Int,
    val compatible: Boolean,
    val message: String,
)

data class ImportSummary(
    val devices: Int,
    val profiles: Int,
    val templates: Int,
    val history: Int,
    val logs: Int,
    val mode: BackupImportMode,
)

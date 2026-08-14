package com.msa.iotofflinetoolbox.core.model

import kotlinx.serialization.Serializable

const val APP_VERSION: String = "3.6.2"
const val TOOLBOX_BACKUP_VERSION: Int = 4

@Serializable
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR }

@Serializable
data class ToolLog(
    val id: String,
    val timestampMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
)

@Serializable
data class SavedDevice(
    val id: String,
    val host: String,
    val displayName: String,
    val addresses: List<String> = emptyList(),
    val openPorts: List<Int> = emptyList(),
    val services: List<String> = emptyList(),
    val note: String = "",
    val tags: List<String> = emptyList(),
    val lastSeenMillis: Long,
)

@Serializable
enum class AppLanguage { ENGLISH, PERSIAN }

@Serializable
data class AppSettings(
    val darkMode: Boolean = false,
    val useSystemTheme: Boolean = true,
    val compactMode: Boolean = false,
    val rtl: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val lastScreen: ToolScreen = ToolScreen.DASHBOARD,
    val defaultTimeoutMillis: Int = 1_500,
    /** Total HTTP request deadline. */
    val defaultHttpTimeoutMillis: Int = 10_000,
    /** Time allowed to establish the HTTP connection when the platform engine supports it. */
    val defaultHttpConnectTimeoutMillis: Int = 5_000,
    /** Maximum HTTP socket inactivity when the platform engine supports it. */
    val defaultHttpSocketTimeoutMillis: Int = 10_000,
    val defaultFollowRedirects: Boolean = false,
    val scanConcurrency: Int = 32,
    val maxPortScanItems: Int = 4_096,
    val maxDiscoveryHosts: Int = 256,
    val maxResponseBytes: Int = 65_536,
    val retainLogs: Int = 300,
    val retainHistory: Int = 100,
    val showAdvancedOptions: Boolean = true,
    /** Explicit opt-in for public HTTP/WS where the platform can enforce the policy. */
    val allowPublicCleartext: Boolean = false,
)

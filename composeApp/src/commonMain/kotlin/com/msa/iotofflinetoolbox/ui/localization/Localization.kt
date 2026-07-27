package com.msa.iotofflinetoolbox.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.msa.iotofflinetoolbox.core.model.AppLanguage
import com.msa.iotofflinetoolbox.core.model.ToolScreen
import com.msa.iotofflinetoolbox.core.model.PlatformCapabilities
import com.msa.iotofflinetoolbox.resources.*
import org.jetbrains.compose.resources.stringResource

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun ToolScreen.localizedTitle(): String = stringResource(
    when (this) {
        ToolScreen.DASHBOARD -> Res.string.screen_dashboard
        ToolScreen.DISCOVERY -> Res.string.screen_discovery
        ToolScreen.NETWORK_TOOLS -> Res.string.screen_network_tools
        ToolScreen.HTTP_CLIENT -> Res.string.screen_http_client
        ToolScreen.REALTIME -> Res.string.screen_realtime
        ToolScreen.PAYLOAD_TOOLS -> Res.string.screen_payload_tools
        ToolScreen.PROFILES -> Res.string.screen_profiles
        ToolScreen.DEVICES -> Res.string.screen_devices
        ToolScreen.LOGS -> Res.string.screen_logs
        ToolScreen.GUIDE -> Res.string.screen_guide
        ToolScreen.SETTINGS -> Res.string.screen_settings
    },
)


@Composable
fun PlatformCapabilities.localizedNotes(): String = when {
    !tcpClient && !udpClient -> stringResource(Res.string.platform_notes_browser)
    platformName.equals("iOS", ignoreCase = true) -> stringResource(Res.string.platform_notes_ios)
    else -> stringResource(Res.string.platform_notes_native)
}

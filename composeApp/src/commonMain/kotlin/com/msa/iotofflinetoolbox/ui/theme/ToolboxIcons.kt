package com.msa.iotofflinetoolbox.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.msa.iotofflinetoolbox.core.model.ToolScreen

val ToolScreen.icon: ImageVector
    get() = when (this) {
        ToolScreen.DASHBOARD -> Icons.Outlined.Dashboard
        ToolScreen.DISCOVERY -> Icons.Outlined.WifiTethering
        ToolScreen.NETWORK_TOOLS -> Icons.Outlined.NetworkCheck
        ToolScreen.HTTP_CLIENT -> Icons.Outlined.Http
        ToolScreen.REALTIME -> Icons.Outlined.Bolt
        ToolScreen.PAYLOAD_TOOLS -> Icons.Outlined.DataObject
        ToolScreen.PROFILES -> Icons.Outlined.Backup
        ToolScreen.DEVICES -> Icons.Outlined.DevicesOther
        ToolScreen.LOGS -> Icons.Outlined.ReceiptLong
        ToolScreen.GUIDE -> Icons.Outlined.MenuBook
        ToolScreen.SETTINGS -> Icons.Outlined.Settings
    }

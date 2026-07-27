package com.msa.iotofflinetoolbox.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun PlatformSystemBars(darkMode: Boolean) {
    val composeView = LocalView.current
    if (composeView.isInEditMode) return

    SideEffect {
        val window = composeView.context.findActivity()?.window ?: return@SideEffect
        val decorView = window.decorView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val lightStatus = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            val lightNavigation = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val mask = lightStatus or lightNavigation
            window.insetsController?.setSystemBarsAppearance(
                if (darkMode) 0 else mask,
                mask,
            )
        } else {
            @Suppress("DEPRECATION")
            var flags = decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            flags = if (darkMode) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                flags = if (darkMode) {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = flags
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

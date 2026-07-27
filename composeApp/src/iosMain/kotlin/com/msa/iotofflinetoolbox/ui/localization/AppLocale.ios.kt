package com.msa.iotofflinetoolbox.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults

@OptIn(InternalComposeUiApi::class)
actual object LocalAppLocale {
    private const val LANGUAGE_KEY = "AppleLanguages"
    private val defaultLocale = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val localAppLocale = staticCompositionLocalOf { defaultLocale }

    actual val current: String
        @Composable get() = localAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val locale = value?.takeIf(String::isNotBlank) ?: defaultLocale
        if (value.isNullOrBlank()) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANGUAGE_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(arrayListOf(locale), LANGUAGE_KEY)
        }
        return localAppLocale.provides(locale)
    }
}

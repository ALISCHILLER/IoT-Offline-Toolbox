package com.msa.iotofflinetoolbox.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
    private var defaultLocale: Locale? = null
    private val localAppLocale = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = localAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (defaultLocale == null) defaultLocale = Locale.getDefault()
        val locale = value?.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag)
            ?: requireNotNull(defaultLocale)
        Locale.setDefault(locale)
        return localAppLocale.provides(locale.toLanguageTag())
    }
}

package com.msa.iotofflinetoolbox.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import com.msa.iotofflinetoolbox.core.model.AppLanguage

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

val AppLanguage.localeTag: String
    get() = when (this) {
        AppLanguage.ENGLISH -> "en"
        AppLanguage.PERSIAN -> "fa"
    }

@Composable
fun AppLocaleEnvironment(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalAppLocale provides language.localeTag,
    ) {
        key(language) {
            content()
        }
    }
}

package com.mbk.hayplan.ui

import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Resolve Android resources using the app override rather than only the system locale. */
@Composable
internal fun localizedString(@StringRes id: Int, vararg arguments: Any): String {
    val resources = localizedResources()
    return resources.getString(id, *arguments)
}

@Composable
internal fun localizedPlural(@PluralsRes id: Int, quantity: Int, vararg arguments: Any): String {
    val resources = localizedResources()
    return resources.getQuantityString(id, quantity, *arguments)
}

@Composable
private fun localizedResources(): android.content.res.Resources {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val language = LocalUiStrings.current.language
    return remember(context, currentConfiguration, language) {
        val configuration = Configuration(currentConfiguration).apply {
            setLocale(Locale.forLanguageTag(language.code))
        }
        context.createConfigurationContext(configuration).resources
    }
}

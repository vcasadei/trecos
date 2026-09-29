package app.trecos.ui.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The languages Trecos is available in.
 *
 * @property tag the BCP 47 language tag of the language's resources.
 */
enum class AppLanguage(val tag: String) {
    English("en"),
    PortugueseBrazil("pt-BR");

    companion object {
        /**
         * Picks the app language for a list of preferred locales: the first
         * one Trecos supports, or English when none is supported. Every
         * Portuguese locale (Brazil, Portugal and others) gets Portuguese (Brazil).
         *
         * @param locales the preferred locales, most preferred first.
         * @return the language to show.
         */
        fun resolve(locales: List<Locale>): AppLanguage =
            locales.firstNotNullOfOrNull(::fromLocale) ?: English

        /**
         * Returns the language the app is showing: the in-app choice if the
         * user made one, otherwise the device language resolved by [resolve].
         *
         * @return the current app language.
         */
        fun current(): AppLanguage {
            val chosen = AppCompatDelegate.getApplicationLocales()
            val locales = if (chosen.isEmpty) LocaleListCompat.getAdjustedDefault() else chosen
            return resolve((0 until locales.size()).mapNotNull { locales[it] })
        }

        /**
         * Switches the whole app to [language] and keeps the choice across
         * restarts, on every supported Android version.
         *
         * @param language the language the user chose.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }

        /**
         * Maps one locale to a supported language.
         *
         * @param locale a locale from the device or the app setting.
         * @return the matching language, or `null` if Trecos doesn't support it;
         *   any Portuguese locale maps to [PortugueseBrazil].
         */
        private fun fromLocale(locale: Locale): AppLanguage? = when {
            locale.language == "en" -> English
            locale.language == "pt" -> PortugueseBrazil
            else -> null
        }
    }
}

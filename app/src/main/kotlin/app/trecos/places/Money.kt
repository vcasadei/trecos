package app.trecos.places

import app.trecos.ui.language.AppLanguage
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Converts between stored minor units and what the user reads and types.
 * Amounts are never converted between currencies; the currency only labels
 * them and sets the number of decimal places.
 */
object Money {

    /**
     * @param language the app language.
     * @return the locale whose number format that language uses.
     */
    fun localeOf(language: AppLanguage): Locale = when (language) {
        AppLanguage.English -> Locale.US
        AppLanguage.PortugueseBrazil -> Locale.forLanguageTag("pt-BR")
    }

    /**
     * @param currencyCode an ISO 4217 code.
     * @return how many decimal places the currency uses, such as 2 for BRL and 0 for JPY.
     */
    fun fractionDigits(currencyCode: String): Int =
        Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)

    /**
     * Formats an amount with the currency symbol and the language's separators.
     *
     * @param minor the amount in minor units.
     * @param currencyCode the display currency.
     * @param language the app language.
     * @return text such as "R$ 1.234,50" (Portuguese) or "R$1,234.50" (English).
     */
    fun format(minor: Long, currencyCode: String, language: AppLanguage): String {
        val digits = fractionDigits(currencyCode)
        val format = NumberFormat.getCurrencyInstance(localeOf(language)).apply {
            currency = Currency.getInstance(currencyCode)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(BigDecimal.valueOf(minor).movePointLeft(digits)).replace(' ', ' ')
    }

    /**
     * Formats an amount for an input field: separators of the language, no symbol.
     *
     * @param minor the amount in minor units.
     * @param currencyCode the display currency.
     * @param language the app language.
     * @return text such as "1234,50" (Portuguese) or "1234.50" (English).
     */
    fun formatInput(minor: Long, currencyCode: String, language: AppLanguage): String {
        val plain = BigDecimal.valueOf(minor).movePointLeft(fractionDigits(currencyCode)).toPlainString()
        return if (language == AppLanguage.PortugueseBrazil) plain.replace('.', ',') else plain
    }

    /**
     * Reads an amount typed by the user in the app language. Grouping
     * separators are accepted; the decimal separator is "," in Portuguese and
     * "." in English.
     *
     * @param text what the user typed.
     * @param currencyCode the display currency, which limits the decimal places.
     * @param language the app language.
     * @return the amount in minor units, or `null` if the text isn't a valid amount of 0 or more.
     */
    fun parse(text: String, currencyCode: String, language: AppLanguage): Long? {
        val (group, decimal) = if (language == AppLanguage.PortugueseBrazil) '.' to ',' else ',' to '.'
        val cleaned = text.trim().replace(" ", "").replace(" ", "").replace(group.toString(), "")
        if (cleaned.isEmpty() || !cleaned.matches(Regex("""\d+(\Q$decimal\E\d*)?"""))) return null
        val value = BigDecimal(cleaned.replace(decimal, '.'))
        val digits = fractionDigits(currencyCode)
        if (value.stripTrailingZeros().scale() > digits) return null
        return value.movePointRight(digits).longValueExact()
    }
}

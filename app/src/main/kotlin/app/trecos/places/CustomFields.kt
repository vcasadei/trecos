package app.trecos.places

import app.trecos.data.FieldType
import app.trecos.ui.language.AppLanguage
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * Reading, storing and showing custom field values (spec "Field types").
 * Values are stored as language-independent text: plain decimals for numbers,
 * ISO dates, and "true"/"false" for Yes/No.
 */
object CustomFields {

    /** The stored value of Yes. */
    const val YES = "true"

    /** The stored value of No. */
    const val NO = "false"

    /**
     * The outcome of [parse].
     *
     * @property value the value to store, or `null` for none (blank input).
     * @property error why the input was rejected, or `null`.
     */
    data class Parsed(val value: String?, val error: FieldError? = null)

    /**
     * The date pattern users type in a language: "M/d/yyyy" in English, "dd/MM/yyyy" in Portuguese.
     *
     * @param language the app language.
     * @return the pattern.
     */
    fun datePattern(language: AppLanguage): String = when (language) {
        AppLanguage.English -> "M/d/yyyy"
        AppLanguage.PortugueseBrazil -> "dd/MM/yyyy"
    }

    /**
     * Reads what the user typed or chose.
     *
     * @param type the field's type.
     * @param input the typed text; for Yes/No, [YES], [NO] or blank.
     * @param language the app language, for decimal separators and dates.
     * @return the value to store, or the error.
     */
    fun parse(type: FieldType, input: String, language: AppLanguage): Parsed {
        val text = input.trim()
        if (text.isEmpty()) return Parsed(null)
        return when (type) {
            FieldType.Text -> Parsed(text)
            FieldType.Number -> parseNumber(text, language)?.let { Parsed(it.stripTrailingZeros().toPlainString()) } ?: Parsed(null, FieldError.NumberInvalid)
            FieldType.Date -> parseDate(text, language)?.let { Parsed(it.toString()) } ?: Parsed(null, FieldError.DateInvalid)
            FieldType.YesNo -> Parsed(if (text == YES) YES else NO)
        }
    }

    /**
     * Turns a stored value back into what the form's field shows.
     *
     * @param type the field's type.
     * @param value the stored value.
     * @param language the app language.
     * @return the text for the input field.
     */
    fun toInput(type: FieldType, value: String, language: AppLanguage): String = when (type) {
        FieldType.Number -> value.toBigDecimalOrNull()?.let { numberFormat(language).format(it) } ?: value
        FieldType.Date -> runCatching { LocalDate.parse(value).format(dateFormatter(language)) }.getOrDefault(value)
        else -> value
    }

    /**
     * Shows a stored value, such as "4 GB" or "30/09/2026".
     *
     * @param type the field's type.
     * @param value the stored value.
     * @param unit a Number field's unit, or `null`.
     * @param language the app language.
     * @param yes the word for Yes.
     * @param no the word for No.
     * @return the text to display.
     */
    fun display(type: FieldType, value: String, unit: String?, language: AppLanguage, yes: String, no: String): String = when (type) {
        FieldType.Text -> value
        FieldType.Number -> toInput(type, value, language) + (unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
        FieldType.Date -> toInput(type, value, language)
        FieldType.YesNo -> if (value == YES) yes else no
    }

    /**
     * @param text the typed number, with the language's decimal separator.
     * @param language the app language.
     * @return the number, or `null` when it isn't one.
     */
    private fun parseNumber(text: String, language: AppLanguage): BigDecimal? {
        val symbols = DecimalFormatSymbols.getInstance(Money.localeOf(language))
        val grouping = symbols.groupingSeparator
        val decimal = symbols.decimalSeparator
        val normal = text.replace(" ", "").replace(grouping.toString(), "").replace(decimal, '.')
        if (!Regex("-?\\d+(\\.\\d+)?").matches(normal)) return null
        return normal.toBigDecimalOrNull()
    }

    /**
     * @param text the typed date.
     * @param language the app language.
     * @return the date, or `null` when it isn't a real date in the language's pattern.
     */
    private fun parseDate(text: String, language: AppLanguage): LocalDate? =
        try {
            LocalDate.parse(text, dateFormatter(language))
        } catch (_: DateTimeParseException) {
            null
        }

    /**
     * @param language the app language.
     * @return a strict formatter for [datePattern].
     */
    private fun dateFormatter(language: AppLanguage): DateTimeFormatter =
        DateTimeFormatter.ofPattern(datePattern(language).replace("yyyy", "uuuu"), Money.localeOf(language)).withResolverStyle(ResolverStyle.STRICT)

    /**
     * @param language the app language.
     * @return a number format without grouping, keeping every decimal.
     */
    private fun numberFormat(language: AppLanguage): DecimalFormat =
        DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(Money.localeOf(language)))
}

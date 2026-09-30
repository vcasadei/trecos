package app.trecos.places

/** Why a form field was rejected. */
enum class FieldError {
    /** The name is empty. */
    NameRequired,

    /** The quantity isn't a whole number from 0 to 999,999. */
    QuantityInvalid,

    /** The unit price isn't an amount of 0 or more with the currency's decimals. */
    PriceInvalid,

    /** Another item or container in the house already uses this QR code. */
    QrInUse,

    /** The QR code is longer than [Validation.MAX_QR]. */
    QrTooLong,

    /** A Number custom field holds something that isn't a number. */
    NumberInvalid,

    /** A Date custom field holds something that isn't a date. */
    DateInvalid,

    /** A new custom field has no name. */
    FieldNameRequired,
}

/** Field rules for houses, containers and items. */
object Validation {
    /** The largest quantity an item can have. */
    const val MAX_QUANTITY = 999_999

    /** The longest QR code, in characters. */
    const val MAX_QR = 256

    /**
     * @param name the typed name.
     * @return the trimmed name, or `null` when it is empty.
     */
    fun name(name: String): String? = name.trim().ifEmpty { null }

    /**
     * Reads a quantity. A blank field means the default of 1.
     *
     * @param text the typed quantity.
     * @return the quantity, or `null` unless it is a whole number from 0 to [MAX_QUANTITY].
     */
    fun quantity(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 1
        if (!trimmed.all(Char::isDigit)) return null
        return trimmed.toIntOrNull()?.takeIf { it in 0..MAX_QUANTITY }
    }

    /**
     * @param text a typed optional field.
     * @return the trimmed text, or `null` when blank, so empty fields are stored as missing.
     */
    fun optional(text: String): String? = text.trim().ifEmpty { null }
}

/**
 * An item's total value: quantity times unit price, calculated and never
 * entered. An item without a price has no total, not zero.
 *
 * @param quantity the quantity.
 * @param unitPrice the unit price in minor units, or `null`.
 * @return the total in minor units, or `null` without a price.
 */
fun totalValue(quantity: Int, unitPrice: Long?): Long? = unitPrice?.let { it * quantity }

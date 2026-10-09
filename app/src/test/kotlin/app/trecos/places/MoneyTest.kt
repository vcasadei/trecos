package app.trecos.places

import app.trecos.ui.language.AppLanguage.English
import app.trecos.ui.language.AppLanguage.PortugueseBrazil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scenarios "Price formatting in Portuguese" and "Price formatting in English",
 * plus parsing what users type.
 */
class MoneyTest {

    @Test
    fun priceFormattingInPortuguese() {
        assertEquals("R$ 1.234,50", Money.format(123_450, "BRL", PortugueseBrazil))
    }

    @Test
    fun priceFormattingInEnglish() {
        assertEquals("R$1,234.50", Money.format(123_450, "BRL", English))
    }

    @Test
    fun symbolMatchesTheFormattedAmount() {
        assertEquals("R$", Money.symbol("BRL", PortugueseBrazil))
        assertEquals("R$", Money.symbol("BRL", English))
        assertEquals("US$", Money.symbol("USD", PortugueseBrazil))
        assertEquals("$", Money.symbol("USD", English))
    }

    @Test
    fun currencyDecimalsFollowTheCurrency() {
        assertEquals(0, Money.fractionDigits("JPY"))
        assertEquals("¥1,234", Money.format(1_234, "JPY", English))
    }

    @Test
    fun parsesTheLanguagesSeparators() {
        assertEquals(123_450L, Money.parse("1.234,50", "BRL", PortugueseBrazil))
        assertEquals(123_450L, Money.parse("1234,5", "BRL", PortugueseBrazil))
        assertEquals(123_450L, Money.parse("1,234.50", "BRL", English))
        assertEquals(10L, Money.parse("0.10", "BRL", English))
        assertEquals(0L, Money.parse("0", "BRL", English))
        assertEquals(1_234L, Money.parse("1234", "JPY", English))
    }

    @Test
    fun rejectsNegativeTooPreciseAndNonNumbers() {
        assertNull(Money.parse("-1", "BRL", English))
        assertNull(Money.parse("1.234", "BRL", English))
        assertNull(Money.parse("12.5", "JPY", English))
        assertNull(Money.parse("abc", "BRL", English))
        assertNull(Money.parse("", "BRL", English))
    }

    @Test
    fun inputFormatRoundTrips() {
        assertEquals("1234,50", Money.formatInput(123_450, "BRL", PortugueseBrazil))
        assertEquals(123_450L, Money.parse(Money.formatInput(123_450, "BRL", PortugueseBrazil), "BRL", PortugueseBrazil))
    }
}

package app.trecos.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scenarios "Minimal item", "Invalid quantity", "Missing name" and "Screws".
 */
class ValidationTest {

    @Test
    fun minimalItemHasQuantityOneAndNoPrice() {
        assertEquals(1, Validation.quantity(""))
        assertEquals(1, Validation.quantity("1"))
        assertNull(totalValue(1, null))
    }

    @Test
    fun invalidQuantity() {
        assertNull(Validation.quantity("2.5"))
        assertNull(Validation.quantity("-1"))
        assertNull(Validation.quantity("1000000"))
        assertNull(Validation.quantity("two"))
        assertEquals(0, Validation.quantity("0"))
        assertEquals(999_999, Validation.quantity("999999"))
    }

    @Test
    fun missingName() {
        assertNull(Validation.name("   "))
        assertEquals("Raspberry Pi 4", Validation.name("  Raspberry Pi 4 "))
    }

    @Test
    fun screws() {
        assertEquals(2_000L, totalValue(quantity = 200, unitPrice = 10))
    }

    @Test
    fun blankOptionalFieldsAreMissing() {
        assertNull(Validation.optional("  "))
        assertEquals("Logitech", Validation.optional(" Logitech "))
    }
}

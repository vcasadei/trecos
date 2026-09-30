package app.trecos.places

import app.trecos.data.FieldType
import app.trecos.ui.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests of the custom field types (spec "Field types"). */
class CustomFieldsTest {

    private val en = AppLanguage.English
    private val pt = AppLanguage.PortugueseBrazil

    @Test
    fun numberWithUnit() {
        val parsed = CustomFields.parse(FieldType.Number, "4", en)
        assertEquals(CustomFields.Parsed("4"), parsed)
        assertEquals("4 GB", CustomFields.display(FieldType.Number, parsed.value!!, "GB", en, "Yes", "No"))
    }

    @Test
    fun wrongType() {
        assertEquals(FieldError.NumberInvalid, CustomFields.parse(FieldType.Number, "four", en).error)
        assertEquals(FieldError.NumberInvalid, CustomFields.parse(FieldType.Number, "1.2.3", en).error)
        assertEquals(FieldError.DateInvalid, CustomFields.parse(FieldType.Date, "31/02/2026", pt).error)
        assertEquals(FieldError.DateInvalid, CustomFields.parse(FieldType.Date, "tomorrow", en).error)
    }

    @Test
    fun decimalsFollowTheLanguage() {
        assertEquals("2.5", CustomFields.parse(FieldType.Number, "2,5", pt).value)
        assertEquals("2.5", CustomFields.parse(FieldType.Number, "2.5", en).value)
        assertEquals("1500", CustomFields.parse(FieldType.Number, "1,500", en).value)
        assertEquals("2,5 cm", CustomFields.display(FieldType.Number, "2.5", "cm", pt, "Sim", "Não"))
        assertEquals("-3", CustomFields.parse(FieldType.Number, "-3.0", en).value)
    }

    @Test
    fun datesAreStoredAsIsoAndShownInTheLanguage() {
        assertEquals("2026-09-30", CustomFields.parse(FieldType.Date, "30/09/2026", pt).value)
        assertEquals("2026-09-30", CustomFields.parse(FieldType.Date, "9/30/2026", en).value)
        assertEquals("30/09/2026", CustomFields.display(FieldType.Date, "2026-09-30", null, pt, "", ""))
        assertEquals("9/30/2026", CustomFields.toInput(FieldType.Date, "2026-09-30", en))
    }

    @Test
    fun textAndYesNo() {
        assertEquals("Klipper", CustomFields.parse(FieldType.Text, "  Klipper ", en).value)
        assertEquals(null, CustomFields.parse(FieldType.Text, "   ", en).value)
        assertEquals(CustomFields.YES, CustomFields.parse(FieldType.YesNo, CustomFields.YES, en).value)
        assertEquals("No", CustomFields.display(FieldType.YesNo, CustomFields.NO, null, en, "Yes", "No"))
        assertEquals("M/d/yyyy", CustomFields.datePattern(en))
    }
}

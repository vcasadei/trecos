package app.trecos.places

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Labels (task 8.2): the rendered image decodes back to the same text, and
 * several labels fit on pages for one print job (task 8.3).
 */
@RunWith(RobolectricTestRunner::class)
class QrLabelsTest {

    /**
     * Decodes the QR code in a rendered label.
     *
     * @param label the label.
     * @return the decoded text.
     */
    private fun decode(label: Label): String {
        val bitmap = QrLabels.render(label)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }

    @Test
    fun aRenderedLabelDecodesToTheSameText() {
        assertEquals("BOX-A", decode(Label("BOX-A")))
        assertEquals("Armário #1 — cozinha", decode(Label("Armário #1 — cozinha")))
    }

    @Test
    fun longCodesStillDecode() {
        // Varied text: ZXing's reader can't read back 256 copies of one letter, which no real label contains.
        val code = (0 until Validation.MAX_QR).map { 'A' + it % 26 }.joinToString("")
        assertEquals(code, decode(Label(code)))
    }

    @Test
    fun severalLabelsSharePages() {
        assertEquals(1, QrLabels.pageCount(6))
        assertEquals(1, QrLabels.pageCount(12))
        assertEquals(2, QrLabels.pageCount(13))
        assertEquals(listOf(12, 1), QrLabels.pages((1..13).map { Label("L$it") }).map { it.size })
    }
}

package app.trecos.places

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.OutputStream

/**
 * One printable label.
 *
 * @property code the QR code's text.
 * @property caption the text printed under the code (the code itself, as the spec asks).
 */
data class Label(val code: String, val caption: String = code)

/** Draws QR labels with ZXing (design D9) and lays several out on pages. */
object QrLabels {

    /** Labels per row on a printed page. */
    const val COLUMNS = 3

    /** Rows of labels on a printed page. */
    const val ROWS = 4

    /**
     * Draws a QR code with its caption underneath, black on white.
     *
     * @param label the label.
     * @param size the code's side in pixels; the caption adds space below.
     * @return the label image.
     */
    fun render(label: Label, size: Int = 600): Bitmap {
        val matrix = QRCodeWriter().encode(
            label.code, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2),
        )
        val captionHeight = size / 6
        val bitmap = Bitmap.createBitmap(size, size + captionHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) Color.BLACK else Color.WHITE }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = captionHeight * 0.55f
        }
        val caption = ellipsize(label.caption, paint, size * 0.95f)
        Canvas(bitmap).drawText(caption, size / 2f, size + captionHeight * 0.65f, paint)
        return bitmap
    }

    /**
     * @param count how many labels.
     * @return how many pages they take.
     */
    fun pageCount(count: Int): Int = (count + COLUMNS * ROWS - 1) / (COLUMNS * ROWS)

    /**
     * Splits labels into pages.
     *
     * @param labels the labels.
     * @return the labels of each page, in order.
     */
    fun pages(labels: List<Label>): List<List<Label>> = labels.chunked(COLUMNS * ROWS)

    /**
     * Writes labels as a PDF, a grid of [COLUMNS] × [ROWS] per A4 page, for one print job.
     *
     * @param labels the labels.
     * @param out where the PDF goes.
     */
    fun writePdf(labels: List<Label>, out: OutputStream) {
        val document = PdfDocument()
        val width = 595
        val height = 842
        val cellWidth = width / COLUMNS
        val cellHeight = height / ROWS
        pages(labels).forEachIndexed { index, pageLabels ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
            pageLabels.forEachIndexed { i, label ->
                val image = render(label, size = 300)
                val side = minOf(cellWidth, cellHeight) - 16
                val left = (i % COLUMNS) * cellWidth + (cellWidth - side) / 2
                val top = (i / COLUMNS) * cellHeight + 8
                val bottom = top + side * image.height / image.width
                page.canvas.drawBitmap(image, null, android.graphics.Rect(left, top, left + side, minOf(bottom, top + cellHeight - 8)), null)
            }
            document.finishPage(page)
        }
        document.writeTo(out)
        document.close()
    }

    /**
     * Shortens text with "…" so it fits a width.
     *
     * @param text the text.
     * @param paint the paint it is drawn with.
     * @param width the available width.
     * @return the text, shortened if needed.
     */
    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
    }
}

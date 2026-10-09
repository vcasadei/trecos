package app.trecos.ui.places

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import app.trecos.R
import app.trecos.places.Label
import app.trecos.places.QrLabels
import java.io.File
import java.io.FileOutputStream

/** Prints labels; replaced by a fake in tests, which can't open the system print dialog. */
interface LabelPrinter {
    /**
     * Prints labels in one print job.
     *
     * @param context an activity context.
     * @param labels the labels, at least one.
     */
    fun print(context: Context, labels: List<Label>)
}

/** Prints through the system print framework: one label with `PrintHelper`, several as a PDF. */
object SystemLabelPrinter : LabelPrinter {
    override fun print(context: Context, labels: List<Label>) {
        if (labels.size == 1) {
            PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }.printBitmap("Trecos · ${labels[0].caption}", QrLabels.render(labels[0]))
        } else {
            context.getSystemService(PrintManager::class.java).print("Trecos labels", LabelsAdapter(labels), null)
        }
    }

    /**
     * Writes the labels as a PDF for the print framework.
     *
     * @property labels the labels.
     */
    private class LabelsAdapter(private val labels: List<Label>) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            val info = PrintDocumentInfo.Builder("trecos-labels.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(QrLabels.pageCount(labels.size))
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
            runCatching { FileOutputStream(destination.fileDescriptor).use { QrLabels.writePdf(labels, it) } }
                .onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.message) }
        }
    }
}

/** The label printer in use; tests provide a fake. */
val LocalLabelPrinter = staticCompositionLocalOf<LabelPrinter> { SystemLabelPrinter }

/**
 * Shares a label as a PNG image through the system share sheet.
 *
 * @param context an activity context.
 * @param label the label.
 */
fun shareLabel(context: Context, label: Label) {
    val dir = File(context.cacheDir, "labels").apply { mkdirs() }
    val file = File(dir, "trecos-label.png")
    file.outputStream().use { QrLabels.render(label).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "app.trecos.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * A QR code shown large with its text underneath, with Share and Print.
 *
 * @param label the label.
 * @param onDismiss closes the view.
 */
@Composable
fun QrLabelView(label: Label, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val printer = LocalLabelPrinter.current
    val image = remember(label) { QrLabels.render(label).asImageBitmap() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.White).statusBarsPadding().padding(24.dp).testTag("qr_label"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = Color.Black) }
            }
            Image(image, contentDescription = label.caption, modifier = Modifier.fillMaxWidth())
            Text(label.caption, color = Color.Black, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { shareLabel(context, label) }, modifier = Modifier.testTag("share_label")) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null)
                    Text(stringResource(R.string.action_share))
                }
                Button(onClick = { printer.print(context, listOf(label)) }, modifier = Modifier.testTag("print_label")) {
                    Icon(painterResource(R.drawable.ic_print), contentDescription = null)
                    Text(stringResource(R.string.action_print))
                }
            }
        }
    }
}

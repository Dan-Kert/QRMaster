package md.dankert.qrmaster

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import com.google.zxing.MultiFormatWriter

fun generateBarcode(
    content: String,
    format: BarcodeFormat = BarcodeFormat.QR_CODE
): Bitmap? {
    val width = 512
    val height = if (format == BarcodeFormat.QR_CODE) 512 else 200

    val writer = MultiFormatWriter()
    val hints = mutableMapOf<EncodeHintType, Any>()
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 2

    return try {
        val bitMatrix = writer.encode(content, format, width, height, hints)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCRGenerator(
    historyList: MutableList<QRHistoryItem>? = null,
    isHistoryEnabled: Boolean = true
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scrollState = rememberScrollState()

    var selectedIndex by remember { mutableIntStateOf(0) }
    val formats = listOf("QR Code", "Barcode")

    val historyTypeLabel = stringResource(R.string.type_created)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(100.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            formats.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                    onClick = { selectedIndex = index },
                    selected = selectedIndex == index
                ) {
                    Text(label)
                }
            }
        }

        TextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text(stringResource(R.string.gen_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            singleLine = false,
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (textInput.isNotBlank()) {
                    val format = if (selectedIndex == 0) BarcodeFormat.QR_CODE else BarcodeFormat.CODE_128
                    val bitmap = generateCode(textInput, format)

                    if (bitmap != null) {
                        qrBitmap = bitmap
                        if (isHistoryEnabled && historyList != null) {
                            historyList.add(0, QRHistoryItem(content = textInput, type = historyTypeLabel))
                        }
                    } else {
                        Toast.makeText(context, "Invalid input for this format", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.gen_button_create))
        }

        Spacer(modifier = Modifier.height(32.dp))

        qrBitmap?.let { bitmap ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (selectedIndex == 0) 200.dp else 120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Generated Code",
                            modifier = Modifier.padding(12.dp).fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.gen_result_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = textInput,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { saveImageToGallery(context, bitmap) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.gen_btn_download))
                        }

                        TextButton(
                            onClick = { copyImageToClipboard(context, bitmap) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.gen_btn_copy))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun generateCode(content: String, format: BarcodeFormat): Bitmap? {
    val width = 512
    val height = if (format == BarcodeFormat.QR_CODE) 512 else 200

    val writer = com.google.zxing.MultiFormatWriter()
    val hints = mutableMapOf<EncodeHintType, Any>()
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 2

    return try {
        val bitMatrix = writer.encode(content, format, width, height, hints)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

fun copyImageToClipboard(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "shared_qr.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "QR Code", contentUri)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, context.getString(R.string.toast_gen_copied), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_gen_copy_error), Toast.LENGTH_SHORT).show()
    }
}

fun saveImageToGallery(context: Context, bitmap: Bitmap) {
    val filename = "QR_${System.currentTimeMillis()}.png"
    var fos: OutputStream? = null
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/QRMaster")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val imageUri: Uri? = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )

    try {
        imageUri?.let { uri ->
            fos = context.contentResolver.openOutputStream(uri)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos!!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(context, context.getString(R.string.toast_gen_saved), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_gen_save_error), Toast.LENGTH_SHORT).show()
    } finally {
        fos?.close()
    }
}

fun generateQRCode(content: String): Bitmap {
    val size = 512
    val writer = QRCodeWriter()
    val hints = mutableMapOf<EncodeHintType, Any>()
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 1

    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    for (x in 0 until size) {
        for (y in 0 until size) {
            val pixelColor = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            bitmap.setPixel(x, y, pixelColor)
        }
    }
    return bitmap
}
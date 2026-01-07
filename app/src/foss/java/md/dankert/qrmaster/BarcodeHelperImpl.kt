package md.dankert.qrmaster

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

class BarcodeHelperImpl : BarcodeHelper {
    private val reader = MultiFormatReader()

    override fun scanImage(imageProxy: ImageProxy, onResult: (ScanResult?) -> Unit) {
        val buffer = imageProxy.planes[0].buffer
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source = PlanarYUVLuminanceSource(
            data, imageProxy.width, imageProxy.height,
            0, 0, imageProxy.width, imageProxy.height, false
        )
        decode(BinaryBitmap(HybridBinarizer(source)), onResult)
        imageProxy.close()
    }

    override fun scanUri(context: Context, uri: Uri, onResult: (ScanResult?) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) { onResult(null); return }

            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            decode(BinaryBitmap(HybridBinarizer(source)), onResult)
        } catch (e: Exception) {
            onResult(null)
        }
    }

    private fun decode(bitmap: BinaryBitmap, onResult: (ScanResult?) -> Unit) {
        try {
            val raw = reader.decode(bitmap).text
            // Парсинг Wi-Fi для FOSS
            if (raw.startsWith("WIFI:")) {
                val ssid = Regex("S:([^;]+)").find(raw)?.groupValues?.get(1)
                val pass = Regex("P:([^;]+)").find(raw)?.groupValues?.get(1)
                onResult(ScanResult(raw, ScanType.WIFI, ssid, pass))
            } else if (raw.startsWith("http")) {
                onResult(ScanResult(raw, ScanType.URL))
            } else {
                onResult(ScanResult(raw, ScanType.TEXT))
            }
        } catch (e: Exception) {
            onResult(null)
        }
    }
}
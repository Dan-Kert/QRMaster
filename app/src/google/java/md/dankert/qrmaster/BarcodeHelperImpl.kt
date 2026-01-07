package md.dankert.qrmaster

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeHelperImpl : BarcodeHelper {
    // Поддержка всех форматов (штрих-коды и QR)
    private val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()
    private val scanner = BarcodeScanning.getClient(options)

    override fun scanImage(imageProxy: ImageProxy, onResult: (ScanResult?) -> Unit) {
        val mediaImage = imageProxy.image ?: return imageProxy.close()
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        processImage(image, onResult) { imageProxy.close() }
    }

    override fun scanUri(context: Context, uri: Uri, onResult: (ScanResult?) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            processImage(image, onResult) {}
        } catch (e: Exception) {
            onResult(null)
        }
    }

    private fun processImage(image: InputImage, onResult: (ScanResult?) -> Unit, onComplete: () -> Unit) {
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull()
                if (barcode != null) {
                    val type = when (barcode.valueType) {
                        Barcode.TYPE_WIFI -> ScanType.WIFI
                        Barcode.TYPE_URL -> ScanType.URL
                        else -> ScanType.TEXT
                    }
                    val ssid = barcode.wifi?.ssid
                    val password = barcode.wifi?.password
                    onResult(ScanResult(barcode.rawValue ?: "", type, ssid, password))
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { onComplete() }
    }
}
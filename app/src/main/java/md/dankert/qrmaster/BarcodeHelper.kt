package md.dankert.qrmaster

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageProxy

interface BarcodeHelper {
    fun scanImage(imageProxy: ImageProxy, onResult: (ScanResult?) -> Unit)
    fun scanUri(context: Context, uri: Uri, onResult: (ScanResult?) -> Unit)
}
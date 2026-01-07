package md.dankert.qrmaster

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion

@Composable
fun QRCScanner(
    historyList: MutableList<QRHistoryItem>? = null,
    isHistoryEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var scanData by remember { mutableStateOf<ScanResult?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val barcodeHelper = remember { BarcodeHelperImpl() }
    val historyTypeLabel = stringResource(R.string.type_scanned)

    val addToHistory: (String) -> Unit = { text ->
        if (isHistoryEnabled && historyList != null && text.isNotEmpty()) {
            if (historyList.isEmpty() || historyList.first().content != text) {
                historyList.add(0, QRHistoryItem(content = text, type = historyTypeLabel))
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            barcodeHelper.scanUri(context, it) { result ->
                if (result != null) {
                    scanData = result
                    addToHistory(result.rawValue)
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_no_qr), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (!perms.values.all { it }) {
            Toast.makeText(context, context.getString(R.string.toast_permissions_denied), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) { launcher.launch(permissions) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        barcodeHelper.scanImage(imageProxy) { result ->
                            if (result != null && (scanData == null || scanData!!.rawValue.isEmpty())) {
                                scanData = result
                                addToHistory(result.rawValue)
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) { Log.e("Scanner", "Error", e) }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 68.dp)) {

            scanData?.let { data ->
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { scanData = null }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                            Icon(Icons.Default.Close, null)
                        }

                        Column(modifier = Modifier.padding(16.dp).padding(end = 32.dp)) {
                            val title = if (data.type == ScanType.WIFI && data.wifiSsid != null) {
                                stringResource(R.string.scan_wifi_title, data.wifiSsid)
                            } else {
                                stringResource(R.string.scan_result_title)
                            }

                            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(text = data.rawValue, style = MaterialTheme.typography.bodyLarge)

                            if (data.type == ScanType.WIFI && !data.wifiPassword.isNullOrEmpty()) {
                                Text(stringResource(R.string.scan_wifi_password, data.wifiPassword), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val textToCopy = if (data.type == ScanType.WIFI) data.wifiPassword ?: "" else data.rawValue
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.btn_copy))
                                }

                                if (data.type == ScanType.URL) {
                                    Button(onClick = { uriHandler.openUri(data.rawValue) }) {
                                        Text(stringResource(R.string.btn_open))
                                    }
                                }

                                if (data.type == ScanType.WIFI) {
                                    Button(onClick = {
                                        connectToWifi(context, data.wifiSsid, data.wifiPassword)
                                    }) {
                                        Icon(Icons.Default.Wifi, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.btn_wifi))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                SmallFloatingActionButton(onClick = { galleryLauncher.launch("image/*") }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(Icons.Default.PhotoLibrary, null)
                }
                Spacer(Modifier.width(24.dp))
                LargeFloatingActionButton(onClick = {
                    isFlashOn = !isFlashOn
                    cameraControl?.enableTorch(isFlashOn)
                }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(if (isFlashOn) Icons.Default.FlashOff else Icons.Default.FlashOn, null)
                }
            }
        }
    }
}


private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

private fun connectToWifi(context: Context, ssid: String?, password: String?) {
    if (ssid.isNullOrEmpty()) return

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val suggestionBuilder = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .setIsAppInteractionRequired(false)

        if (password.isNullOrEmpty()) {
            suggestionBuilder.setUntrusted(false)
        } else {
            suggestionBuilder.setWpa2Passphrase(password)
        }

        val suggestion = suggestionBuilder.build()
        val suggestionsList = listOf(suggestion)

        val status = wifiManager.addNetworkSuggestions(suggestionsList)

        if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Toast.makeText(context, "Сеть $ssid сохранена! Система подключится к ней.", Toast.LENGTH_LONG).show()

        } else {
            Toast.makeText(context, "Ошибка сохранения сети", Toast.LENGTH_SHORT).show()
        }
    }
    else {
        try {
            if (!wifiManager.isWifiEnabled) wifiManager.isWifiEnabled = true

            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                if (!password.isNullOrEmpty()) {
                    preSharedKey = "\"$password\""
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }

            val netId = wifiManager.addNetwork(wifiConfig)
            wifiManager.disconnect()
            wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()

            Toast.makeText(context, "Подключение к $ssid...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
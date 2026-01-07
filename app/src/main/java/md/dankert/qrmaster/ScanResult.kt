package md.dankert.qrmaster

enum class ScanType {
    TEXT, URL, WIFI
}

data class ScanResult(
    val rawValue: String,
    val type: ScanType,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null
)
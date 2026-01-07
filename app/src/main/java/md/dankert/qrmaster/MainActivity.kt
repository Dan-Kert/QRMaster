package md.dankert.qrmaster

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import md.dankert.qrmaster.ui.theme.QRMasterTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext

data class QRHistoryItem(
    val content: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

object LocaleUtils {
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}

class MainActivity : ComponentActivity() {
    private val PREFS_NAME = "qrmaster_prefs"
    private val KEY_THEME = "is_dark_theme"
    private val KEY_HISTORY_TOGGLE = "is_history_enabled"
    private val KEY_HISTORY_DATA = "history_json"
    private val KEY_LANG = "app_lang"

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("qrmaster_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "ru") ?: "ru"
        super.attachBaseContext(LocaleUtils.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()

        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean(KEY_THEME, systemDark)) }
            var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_HISTORY_TOGGLE, true)) }
            var currentLang by remember { mutableStateOf(prefs.getString(KEY_LANG, "ru") ?: "ru") }

            val historyList = remember {
                val json = prefs.getString(KEY_HISTORY_DATA, null)
                val list: MutableList<QRHistoryItem> = if (json != null) {
                    val typeToken = object : TypeToken<MutableList<QRHistoryItem>>() {}.type
                    gson.fromJson(json, typeToken)
                } else mutableListOf()
                mutableStateListOf<QRHistoryItem>().apply { addAll(list) }
            }

            QRMasterTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = {
                        isDarkTheme = it
                        prefs.edit().putBoolean(KEY_THEME, it).apply()
                    },
                    isHistoryEnabled = isHistoryEnabled,
                    onHistoryToggle = {
                        isHistoryEnabled = it
                        prefs.edit().putBoolean(KEY_HISTORY_TOGGLE, it).apply()
                    },
                    currentLang = currentLang,
                    onLangChange = { newLang ->
                        prefs.edit().putString(KEY_LANG, newLang).apply()
                        recreate()
                    },
                    historyList = historyList,
                    onHistoryUpdated = {
                        val json = gson.toJson(historyList.toList())
                        prefs.edit().putString(KEY_HISTORY_DATA, json).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    isHistoryEnabled: Boolean,
    onHistoryToggle: (Boolean) -> Unit,
    currentLang: String,
    onLangChange: (String) -> Unit,
    historyList: MutableList<QRHistoryItem>,
    onHistoryUpdated: () -> Unit
) {
    var currentScreen by remember { mutableIntStateOf(1) }
    var hasCameraPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }

    LaunchedEffect(Unit) { launcher.launch(android.Manifest.permission.CAMERA) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (currentScreen) {
                    0 -> if (hasCameraPermission) {
                        QRCScanner(historyList, isHistoryEnabled)
                        LaunchedEffect(historyList.size) { onHistoryUpdated() }
                    } else PermissionDenied()
                    1 -> HistoryScreen(historyList) {
                        historyList.clear()
                        onHistoryUpdated()
                    }
                    2 -> SettingsScreen(isDarkTheme, onThemeChange, isHistoryEnabled, onHistoryToggle, currentLang, onLangChange)
                    3 -> {
                        QRCRGenerator(historyList, isHistoryEnabled)
                        LaunchedEffect(historyList.size) { onHistoryUpdated() }
                    }
                }
            }
            MenuPill(currentScreen = currentScreen, onNavigate = { currentScreen = it })
        }
    }
}

@Composable
fun BoxScope.MenuPill(currentScreen: Int, onNavigate: (Int) -> Unit) {
    Column(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 16.dp)) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).height(64.dp).fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            shadowElevation = 8.dp
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                CustomTabItem(stringResource(R.string.nav_scanner), currentScreen == 0) { onNavigate(0) }
                VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                NavIconItem(Icons.Default.History, currentScreen == 1) { onNavigate(1) }
                NavIconItem(Icons.Default.Settings, currentScreen == 2) { onNavigate(2) }
                VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                CustomTabItem(stringResource(R.string.nav_create), currentScreen == 3) { onNavigate(3) }
            }
        }
    }
}

@Composable
fun HistoryScreen(historyList: List<QRHistoryItem>, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(100.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (historyList.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (historyList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(historyList) { item -> HistoryItemCard(item) }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: QRHistoryItem) {
    val typeLabel = if (item.type == "Создано") stringResource(R.string.type_created)
    else stringResource(R.string.type_scanned)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (item.type == "Создано") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.type == "Создано") Icons.Default.QrCode else Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = if (item.type == "Создано") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.content, style = MaterialTheme.typography.bodyLarge, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Row {
                    Text(text = typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(text = " • ${item.getFormattedDate()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    isHistoryEnabled: Boolean,
    onHistoryToggle: (Boolean) -> Unit,
    currentLang: String,
    onLangChange: (String) -> Unit
) {
    val context = LocalContext.current
    val appVersion = remember { getAppVersion(context) }

    var showLangDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(100.dp))
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        SettingsToggleItem(stringResource(R.string.settings_dark_theme), if (isDarkTheme) stringResource(R.string.settings_theme_on) else stringResource(R.string.settings_theme_off), if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, isDarkTheme, onThemeChange)
        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleItem(stringResource(R.string.settings_save_history), stringResource(R.string.settings_save_history_desc), Icons.Default.History, isHistoryEnabled, onHistoryToggle)

        Spacer(modifier = Modifier.height(16.dp))

        val langName = when(currentLang) {
            "ru" -> "Русский"
            "en" -> "English"
            "ro" -> "Română"
            "es" -> "Español"
            "zh" -> "中文"
            "ar" -> "العربية"
            else -> currentLang
        }

        SettingsClickableItem(
            title = stringResource(R.string.settings_language),
            subtitle = langName,
            icon = Icons.Default.Language,
            onClick = { showLangDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsClickableItem(
            title = stringResource(R.string.settings_about),
            subtitle = "QR Master v$appVersion",
            icon = Icons.Default.Info,
            onClick = { showAboutDialog = true }
        )
    }

    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    val langs = listOf(
                        "ru" to "Русский", "en" to "English", "ro" to "Română",
                        "es" to "Español", "zh" to "中文", "ar" to "العربية"
                    )
                    langs.forEach { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onLangChange(code); showLangDialog = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentLang == code, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text("QR Master") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.about_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    AboutInfoRow(stringResource(R.string.about_version), appVersion)
                    AboutInfoRow(stringResource(R.string.about_developer), "Dankert")

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { uriHandler.openUri("https://dan-kert.github.io") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Public, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.about_dev_site), maxLines = 1)
                        }

                        Button(
                            onClick = { uriHandler.openUri("https://github.com/dan-kert/qrmaster") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.about_source_code), maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SettingsToggleItem(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(onClick = { onCheckedChange(!checked) }, shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun NavIconItem(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun CustomTabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 8.dp).height(40.dp).clip(CircleShape).clickable { onClick() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PermissionDenied() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.permission_camera_denied), textAlign = TextAlign.Center)
    }
}

@Composable
fun SettingsClickableItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AboutInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}
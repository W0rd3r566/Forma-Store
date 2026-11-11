package dev.formastore.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) { FormaApp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormaApp(model: StoreModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Все") }
    var detail by remember { mutableStateOf<StoreApp?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.rescan() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.message(null) } }
    val categories = remember(state.apps) { listOf("Все") + state.apps.flatMap { it.categories }.distinct().sorted() }
    val results = remember(state.apps, state.installed, tab, query, category) {
        state.apps.filter { app ->
            (tab == 0 || state.installed.containsKey(app.id)) &&
            (tab != 1 || app.code > (state.installed[app.id] ?: Long.MAX_VALUE)) &&
            (category == "Все" || category in app.categories) &&
            (query.isBlank() || (app.name + " " + app.summary + " " + app.id).contains(query, true))
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Forma Store", fontWeight = FontWeight.SemiBold) }, actions = {
            IconButton(enabled = !state.loading, onClick = { model.refresh(true) }) { Icon(Icons.Outlined.Refresh, "Обновить каталог") }
            IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, "О магазине") }
        }) },
        bottomBar = { NavigationBar {
            listOf("Каталог", "Обновления", "Библиотека").forEachIndexed { i, title ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {
                    Icon(when (i) { 0 -> Icons.Outlined.Explore; 1 -> Icons.Outlined.SystemUpdate; else -> Icons.Outlined.Widgets }, null)
                }, label = { Text(title) })
            }
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Поиск приложений") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Очистить") }
            }, singleLine = true, shape = RoundedCornerShape(28.dp)) }
            if (state.apps.isEmpty()) item {
                ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_peek_mono), null, Modifier.size(70.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Твои приложения.\nБез аккаунта.", style = MaterialTheme.typography.headlineMedium)
                        Text(if (state.loading) "Первая загрузка индекса F-Droid может занять время. Проверяем подпись репозитория перед показом приложений." else "Подключитесь к сети и загрузите каталог F-Droid.")
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Button(onClick = { model.refresh(true) }) { Text("Загрузить каталог") }
                    }
                }
            } else item {
                var expanded by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${results.size} приложений", style = MaterialTheme.typography.labelLarge)
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(category.take(25) + " ▾") }
                        DropdownMenu(expanded, { expanded = false }) { categories.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false }) } }
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.progress?.let { progress -> item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp)
                        Text(progress, Modifier.weight(1f).padding(horizontal = 14.dp))
                        TextButton(onClick = { model.cancelDownload() }) { Text("Отмена") }
                    }
                }
            } }
            if (state.apps.isNotEmpty() && results.isEmpty()) item { Text(if (tab == 1) "Доступных обновлений в выбранном каталоге нет." else "Ничего не найдено. Попробуй другой запрос или категорию.", Modifier.padding(20.dp)) }
            items(results, key = { it.id }) { app ->
                ListItem(
                    headlineContent = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(app.summary.ifBlank { app.id }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AppIcon(app) },
                    trailingContent = { if (state.installed.containsKey(app.id)) Icon(if (app.code > (state.installed[app.id] ?: 0)) Icons.Outlined.SystemUpdate else Icons.Outlined.Check, "Статус установки") },
                    modifier = Modifier.clickable { detail = app }
                )
            }
            item { Text("F-Droid · без аккаунта · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp)) }
        }
    }
    detail?.let { app ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { AppIcon(app); Column { Text(app.name, style = MaterialTheme.typography.headlineSmall); Text(app.version + " · " + mb(app.size), style = MaterialTheme.typography.bodySmall) } }
                Text(app.summary, style = MaterialTheme.typography.titleMedium)
                Text(app.description.ifBlank { "Описание отсутствует." }, style = MaterialTheme.typography.bodyMedium)
                val installed = state.installed[app.id]
                Button(onClick = { model.install(app); detail = null }, enabled = state.progress == null && (installed == null || installed < app.code), modifier = Modifier.fillMaxWidth()) {
                    Text(if (installed == null) "Установить · ${mb(app.size)}" else if (installed < app.code) "Обновить" else "Установлено")
                }
                Text("${app.id}\nЛицензия: ${app.license}\nAndroid API ${app.minSdk}+ · target ${app.targetSdk}\nАрхитектуры: ${app.abis.joinToString().ifBlank { "универсальное" }}", style = MaterialTheme.typography.bodySmall)
                if (app.antiFeatures.isNotEmpty()) Text("Особенности F-Droid: ${app.antiFeatures.joinToString()}", color = MaterialTheme.colorScheme.error)
                if (app.permissions.isNotEmpty()) Text("Разрешения:\n" + app.permissions.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                if (app.source.startsWith("https://")) TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.source))) }.onFailure { model.message("Не найден браузер") } }) { Text("Исходный код ↗") }
                Text("Источник: f-droid.org/repo\nSHA-256: ${app.hash}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    state.release?.let { release -> ModalBottomSheet(onDismissRequest = model::dismissRelease) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Новая Forma уже здесь", style = MaterialTheme.typography.headlineSmall)
            Text("${release.version} · ${mb(release.size)}")
            Text(release.notes.ifBlank { "Обновление Forma Store" })
            Text("GitHub Releases. Подпись APK должна совпасть с установленной версией. Тихой установки нет.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { model.installRelease(release); model.dismissRelease() }, enabled = state.progress == null, modifier = Modifier.fillMaxWidth()) { Text("Скачать обновление") }
            TextButton(onClick = model::dismissRelease) { Text("Позже") }
            Spacer(Modifier.height(20.dp))
        }
    } }
    state.ready?.let { apk -> AlertDialog(onDismissRequest = model::consumeReady,
        title = { Text("APK готов") }, text = { Text("Пакет и версия проверены. Для приложений из каталога также проверен SHA-256. Подтверди установку в системном окне Android.") },
        confirmButton = { TextButton(onClick = {
            try {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                    model.message("Разреши установку из Forma Store и вернись к этому окну.")
                } else {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    model.consumeReady()
                }
            } catch (e: Exception) { model.message("Не удалось открыть установщик: ${e.localizedMessage}") }
        }) { Text("Установить") } }, dismissButton = { TextButton(onClick = model::consumeReady) { Text("Позже") } }
    ) }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Forma Store ${BuildConfig.VERSION_NAME}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Независимый клиент F-Droid. Без аккаунта и рекламных SDK. Запросы к F-Droid и GitHub раскрывают им обычные сетевые метаданные, включая IP. Иконки загружаются с F-Droid.")
            Text("Каталог проверяется по закреплённому SHA-256 сертификата репозитория. Обновления с отличающейся подписью APK блокируются. Библиотека может не видеть приложения, скрытые Android package visibility.")
            Text("Не связан с Google или F-Droid. Android robot inspired artwork: CC BY 3.0; см. NOTICE.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !state.checking, onClick = { model.checkRelease(true); about = false }) { Text(if (state.checking) "Проверяем…" else "Проверить обновление Forma") }
        }
    }, confirmButton = { TextButton(onClick = { about = false }) { Text("Готово") } })
}
@Composable
private fun AppIcon(app: StoreApp) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
        if (app.icon != null) AsyncImage(app.icon, contentDescription = null, modifier = Modifier.padding(6.dp))
        else Box(contentAlignment = Alignment.Center) { Text(app.name.take(1), style = MaterialTheme.typography.headlineSmall) }
    }
}
private fun mb(size: Long) = String.format(Locale.getDefault(), "%.1f МБ", size / 1_000_000.0)

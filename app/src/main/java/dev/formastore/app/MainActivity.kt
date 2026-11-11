package dev.formastore.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Appearance.load(this)
        setContent { FormaTheme { FormaApp() } }
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
    var favorites by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<StoreApp?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var downloads by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var history by remember { mutableStateOf(context.getSharedPreferences("search", 0).getString("history", "")!!.split('\n').filter(String::isNotBlank)) }
    var savedAt by remember { mutableLongStateOf(0) }
    val list0 = rememberLazyListState(); val list1 = rememberLazyListState(); val list2 = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { model.message("Не найден браузер") } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.rescan() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.message(null) } }
    LaunchedEffect(state.apps, state.loading) { savedAt = withContext(Dispatchers.IO) { CatalogCache(context).use { runCatching { it.savedAt() }.getOrDefault(0) } } }
    BackHandler(query.isNotEmpty() && detail == null && !settings && !about && !downloads && !showHistory && state.release == null && state.ready == null) { query = "" }
    val results by produceState(emptyList<StoreApp>(), state.apps, state.installed, state.ignored, state.favorites, tab, query, category, favorites) {
        kotlinx.coroutines.delay(120)
        value = withContext(Dispatchers.Default) { state.apps.filter { a ->
            (tab == 0 || a.id in state.installed) &&
            (tab != 1 || a.code > (state.installed[a.id] ?: Long.MAX_VALUE) && a.id !in state.ignored) &&
            (!favorites || a.id in state.favorites) && (category == "Все" || category in a.categories) &&
            (query.isBlank() || (a.name + " " + a.summary + " " + a.id).contains(query, true))
        } }
    }
    val categories = remember(state.apps) { listOf("Все") + state.apps.flatMap { it.categories }.distinct().sorted() }
    val updates = remember(state.apps, state.installed, state.ignored) { state.apps.count { a -> a.id !in state.ignored && state.installed[a.id]?.let { it < a.code } == true } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column {
            TopAppBar(title = { Text("Forma") }, actions = {
                IconButton(enabled = !state.loading, onClick = { model.refresh(true) }) { Icon(Icons.Outlined.Refresh, "Обновить каталог") }
                IconButton(enabled = !state.checking, onClick = { model.checkRelease(true) }) {
                    if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.SystemUpdateAlt, "Обновить Forma")
                }
                IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Tune, "Настройки") }
                IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, "О магазине") }
            })
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 8.dp), placeholder = { Text("Поиск приложений") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
                if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Очистить") }
                else IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, "История") }
            }, singleLine = true, shape = RoundedCornerShape(28.dp))
        }
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (state.progress != null || state.ready != null || state.queueNames.isNotEmpty()) TransferCard(state) { downloads = true }
            FormaNavigation(tab, updates) { tab = it }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = when(tab) { 0 -> list0; 1 -> list1; else -> list2 }, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "status") {
                val stamp = if (savedAt == 0L) "Каталог не сохранён" else "Каталог: " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(savedAt))
                Text(stamp + if (state.loading) " · обновляем…" else " · локальная копия", style = MaterialTheme.typography.labelSmall)
                if (!state.online) Text("Офлайн · сохранённые приложения доступны", style = MaterialTheme.typography.labelSmall)
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                state.catalogError?.let { Text("Ошибка обновления: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item(key = "filters") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FilterChip(favorites, { favorites = !favorites }, label = { Text("Избранное ${state.favorites.size}") })
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(category.take(16) + " ▾") }
                        DropdownMenu(expanded, { expanded = false }) { categories.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expanded = false }) } }
                    }
                }
                if (query.isNotBlank() || category != "Все" || favorites) TextButton(onClick = { query = ""; category = "Все"; favorites = false }) { Text("Сбросить фильтры") }
            }
            if (tab == 1 && results.isNotEmpty()) item(key = "all-updates") {
                FilledTonalButton(onClick = { results.forEach(model::install) }, modifier = Modifier.fillMaxWidth()) { Text("Скачать все · ${results.size}") }
                Text("Каждая установка подтверждается в Android.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.apps.isEmpty()) item(key = "empty") {
                ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_peek_mono), null, Modifier.size(65.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Твои приложения.\nБез аккаунта.", style = MaterialTheme.typography.headlineMedium)
                        Text(if (state.loading) "Проверяем подписанный каталог F-Droid…" else "Загрузи каталог, чтобы начать.")
                        if (!state.loading) Button(onClick = { model.refresh(true) }) { Text("Загрузить каталог") }
                    }
                }
            } else if (results.isEmpty()) item(key = "no-results") {
                Text(if (tab == 1 && state.catalogError == null) "В сохранённом каталоге нет обновлений по этим фильтрам." else "Ничего не найдено. Проверь фильтры или обнови каталог.")
            }
            items(results, key = { it.id }) { app ->
                ListItem(headlineContent = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = {
                    Column {
                        Text(app.summary.ifBlank { app.id }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (tab == 1) Text("${installedVersion(context, app.id)} → ${app.version} · ${sizeLabel(app.size)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }, leadingContent = { StoreIcon(app) }, trailingContent = {
                    val installed = state.installed[app.id]
                    if (installed != null && app.code > installed && app.id !in state.ignored) FilledTonalIconButton(onClick = { model.install(app) }) { Icon(Icons.Outlined.Download, "Обновить ${app.name}") }
                    else if (installed != null) Icon(Icons.Outlined.Check, "Установлено")
                }, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                    if (query.isNotBlank()) {
                        history = (listOf(query.trim().replace('\n', ' ')) + history).distinct().take(8)
                        context.getSharedPreferences("search", 0).edit().putString("history", history.joinToString("\n")).apply()
                    }
                    detail = app
                })
            }
            item(key = "footer") { Text("F-Droid · ${BuildConfig.VERSION_NAME} · универсальный APK", style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (settings) StoreSettings(state, model) { settings = false }
    detail?.let { AppDetail(it, state, model) { detail = null } }
    if (showHistory) AlertDialog(onDismissRequest = { showHistory = false }, title = { Text("История поиска") }, text = {
        Column { if (history.isEmpty()) Text("Пока пусто") else history.forEach { q -> TextButton(onClick = { query = q; showHistory = false }) { Text(q) } } }
    }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { history = emptyList(); context.getSharedPreferences("search", 0).edit().remove("history").apply() }) { Text("Очистить") } })
    if (downloads) ModalBottomSheet(onDismissRequest = { downloads = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Загрузки", style = MaterialTheme.typography.headlineSmall)
            if (state.progress != null || state.ready != null) TransferCard(state) { }
            state.queueNames.forEachIndexed { i, name ->
                Row { Text(name, Modifier.weight(1f)); IconButton(onClick = { model.removeQueued(i) }) { Icon(Icons.Outlined.Close, "Убрать из очереди") } }
            }
            Text("После остановки процесса загрузка начнётся заново. Проверенные APK ожидают подтверждения установки.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = model::cancelDownload) { Text("Отменить загрузку и очередь") }
            Button(onClick = { downloads = false }) { Text("Готово") }
            Spacer(Modifier.height(16.dp))
        }
    }
    state.release?.let { r -> ModalBottomSheet(onDismissRequest = model::dismissRelease) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Новая Forma ${r.version}", style = MaterialTheme.typography.headlineSmall)
            Text(r.notes.ifBlank { "Обновление Forma Store" })
            Button(onClick = { model.installRelease(r); model.dismissRelease() }, enabled = state.progress == null, modifier = Modifier.fillMaxWidth()) { Text("Скачать · ${sizeLabel(r.size)}") }
            TextButton(onClick = model::dismissRelease) { Text("Позже") }
        }
    } }
    state.ready?.let { apk -> AlertDialog(onDismissRequest = model::consumeReady, title = { Text("APK готов") }, text = { Text("Проверки завершены. Подтверди установку в Android.") }, confirmButton = {
        TextButton(onClick = {
            try {
                if (!context.packageManager.canRequestPackageInstalls()) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                else {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", apk)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    model.handedToInstaller()
                }
            } catch (e: Exception) { model.message("Установщик: ${e.localizedMessage}") }
        }) { Text("Установить") }
    }, dismissButton = { TextButton(onClick = model::consumeReady) { Text("Пропустить") } }) }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Forma ${BuildConfig.VERSION_NAME}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Независимый клиент F-Droid. Без аккаунта и рекламы. F-Droid и GitHub получают сетевые метаданные, включая IP.")
            FilledTonalButton(onClick = { open("https://github.com/W0rd3r566/Forma-Store") }) { Icon(painterResource(R.drawable.ic_github), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Исходный код") }
            Text("Не связан с Google или F-Droid. Android robot artwork: CC BY 3.0; см. NOTICE.", style = MaterialTheme.typography.bodySmall)
            Text("snowie thx", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
        }
    }, confirmButton = { TextButton(onClick = { about = false }) { Text("Готово") } })
}

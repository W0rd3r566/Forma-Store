package dev.formastore.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { model.message("Не найден браузер") }
    }
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
    val updates = remember(state.apps, state.installed) {
        state.apps.count { app -> state.installed[app.id]?.let { it < app.code } == true }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text("Forma Store", fontWeight = FontWeight.SemiBold) }, actions = {
                IconButton(enabled = !state.loading, onClick = { model.refresh(true) }) {
                    Icon(Icons.Outlined.Refresh, "Обновить каталог")
                }
                IconButton(enabled = !state.checking, onClick = { model.checkRelease(true) }) {
                    if (state.checking) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.SystemUpdateAlt, "Проверить обновление Forma Store")
                }
                IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, "О магазине") }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                // Download status stays reachable even when the catalog is scrolled.
                state.progress?.let { progress ->
                    Surface(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(progress, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = model::cancelDownload) { Icon(Icons.Outlined.Close, "Отменить загрузку") }
                        }
                    }
                }
                FloatingNavigation(tab, updates) { tab = it; category = "Все"; query = "" }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "search") {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Поиск приложений") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Очистить") } },
                    singleLine = true, shape = RoundedCornerShape(28.dp))
            }
            if (state.apps.isEmpty()) item(key = "empty-catalog") {
                ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_peek_mono), null, Modifier.size(70.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Твои приложения.\nБез аккаунта.", style = MaterialTheme.typography.headlineMedium)
                        Text(if (state.loading) "Загружаем каталог F-Droid и проверяем его подпись. Первый запуск может занять время." else "Подключитесь к сети и загрузите каталог F-Droid.")
                        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else Button(onClick = { model.refresh(true) }) { Text("Загрузить каталог") }
                    }
                }
            } else item(key = "filter") {
                var expanded by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${results.size} приложений", style = MaterialTheme.typography.labelLarge)
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(category.take(25) + " ▾") }
                        DropdownMenu(expanded, { expanded = false }) { categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false })
                        } }
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (state.apps.isNotEmpty() && results.isEmpty()) item(key = "no-results") {
                Text(if (tab == 1) "Все доступные приложения обновлены." else "Ничего не найдено. Попробуй другой запрос или категорию.", Modifier.padding(20.dp))
            }
            items(results, key = { it.id }) { app ->
                ListItem(
                    headlineContent = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(app.summary.ifBlank { app.id }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AppIcon(app) },
                    trailingContent = {
                        val installed = state.installed[app.id]
                        if (installed != null && app.code > installed) {
                            FilledTonalIconButton(enabled = state.progress == null, onClick = { model.install(app) }) {
                                Icon(Icons.Outlined.Download, "Обновить ${app.name}")
                            }
                        } else if (installed != null) Icon(Icons.Outlined.Check, "Установлено")
                    },
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { detail = app }
                )
            }
            item(key = "footer") { Text("F-Droid · без аккаунта · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp)) }
        }
    }
    detail?.let { app ->
        val installed = state.installed[app.id]
        // Scroll only the content; install/update action is always below it.
        ModalBottomSheet(onDismissRequest = { detail = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp)) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppIcon(app)
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(app.version + " · " + mb(app.size), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { detail = null }) { Icon(Icons.Outlined.Close, "Закрыть карточку") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(app.summary, style = MaterialTheme.typography.titleMedium)
                    Text(app.description.ifBlank { "Описание отсутствует." }, style = MaterialTheme.typography.bodyMedium)
                    Text("${app.id}\nЛицензия: ${app.license}\nAndroid API ${app.minSdk}+ · target ${app.targetSdk}\nАрхитектуры: ${app.abis.joinToString().ifBlank { "универсальное" }}", style = MaterialTheme.typography.bodySmall)
                    if (app.antiFeatures.isNotEmpty()) Text("Особенности F-Droid: ${app.antiFeatures.joinToString()}", color = MaterialTheme.colorScheme.error)
                    if (app.permissions.isNotEmpty()) Text("Разрешения:\n" + app.permissions.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    if (app.source.startsWith("https://")) TextButton(onClick = { openUrl(app.source) }) {
                        Icon(Icons.Outlined.Code, null); Spacer(Modifier.width(8.dp)); Text("Исходный код приложения ↗")
                    }
                    Text("Источник: f-droid.org/repo\nSHA-256: ${app.hash}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                }
                HorizontalDivider()
                Button(
                    onClick = { model.install(app); detail = null },
                    enabled = state.progress == null && (installed == null || installed < app.code),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp)
                ) {
                    Icon(if (installed == null) Icons.Outlined.Download else Icons.Outlined.SystemUpdate, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (installed == null) "Установить · ${mb(app.size)}" else if (installed < app.code) "Обновить · ${mb(app.size)}" else "Уже установлено")
                }
            }
        }
    }
    state.release?.let { release ->
        ModalBottomSheet(onDismissRequest = model::dismissRelease, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .8f).dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Новая Forma уже здесь", style = MaterialTheme.typography.headlineSmall)
                    Text("${release.version} · ${mb(release.size)}", Modifier.padding(top = 8.dp))
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(release.notes.ifBlank { "Обновление Forma Store" })
                    Text("GitHub Releases. Подпись APK должна совпасть с установленной версией. Android запросит подтверждение установки.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { model.installRelease(release); model.dismissRelease() }, enabled = state.progress == null, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp).heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Скачать обновление")
                }
                TextButton(onClick = model::dismissRelease, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { Text("Позже") }
            }
        }
    }
    state.ready?.let { apk ->
        AlertDialog(onDismissRequest = model::consumeReady, title = { Text("APK готов") },
            text = { Text("Пакет и версия проверены. Для приложений из каталога также проверен SHA-256. Подтверди установку в системном окне Android.") },
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
            }) { Text("Установить") } }, dismissButton = { TextButton(onClick = model::consumeReady) { Text("Позже") } })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Forma Store ${BuildConfig.VERSION_NAME}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Независимый клиент F-Droid. Без аккаунта и рекламных SDK. Запросы к F-Droid и GitHub раскрывают им обычные сетевые метаданные, включая IP.")
            FilledTonalButton(onClick = { openUrl("https://github.com/W0rd3r566/Forma-Store") }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_github), null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp)); Text("Исходный код · GitHub")
            }
            TextButton(enabled = !state.checking, onClick = { model.checkRelease(true); about = false }) {
                Icon(Icons.Outlined.SystemUpdateAlt, null); Spacer(Modifier.width(8.dp)); Text("Обновить Forma")
            }
            Text("Каталог проверяется по закреплённому сертификату F-Droid. APK с другой подписью не обновит установленное приложение. Android может скрывать часть установленных пакетов.", style = MaterialTheme.typography.bodySmall)
            Text("Не связан с Google или F-Droid. Android robot inspired artwork: CC BY 3.0; см. NOTICE.", style = MaterialTheme.typography.bodySmall)
            Text("snowie thx", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f), modifier = Modifier.padding(top = 8.dp))
        }
    }, confirmButton = { TextButton(onClick = { about = false }) { Text("Готово") } })
}

@Composable
private fun FloatingNavigation(selected: Int, updates: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(38.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Row(Modifier.selectableGroup().padding(7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Каталог", "Обновления", "Библиотека").forEachIndexed { i, title ->
                    val active = selected == i
                    val radius by animateDpAsState(if (active) 28.dp else 20.dp, label = "navigation-radius")
                    Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(radius), modifier = Modifier.weight(1f).clip(RoundedCornerShape(radius))
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(i) }).animateContentSize()) {
                        Column(Modifier.padding(horizontal = 2.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BadgedBox(badge = { if (i == 1 && updates > 0) Badge { Text(if (updates > 99) "99+" else updates.toString()) } }) {
                                Icon(when (i) { 0 -> Icons.Outlined.Explore; 1 -> Icons.Outlined.SystemUpdate; else -> Icons.Outlined.Widgets }, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                            Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: StoreApp) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
        if (app.icon != null) AsyncImage(app.icon, contentDescription = null, modifier = Modifier.padding(6.dp))
        else Box(contentAlignment = Alignment.Center) { Text(app.name.take(1), style = MaterialTheme.typography.headlineSmall) }
    }
}
private fun mb(size: Long) = String.format(Locale.getDefault(), "%.1f МБ", size / 1_000_000.0)

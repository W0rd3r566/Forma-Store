package dev.formastore.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetail(app: StoreApp, state: StoreState, model: StoreModel, close: () -> Unit) {
    val context = LocalContext.current
    val installed = state.installed[app.id]
    var expanded by remember(app.id) { mutableStateOf(false) }
    var gallery by remember(app.id) { mutableIntStateOf(-1) }
    val media by produceState(AppMedia(), app.id) { value = appMedia(context, app.id) }
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { model.message("Не найден браузер") } }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .88f).dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                StoreIcon(app)
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(app.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(app.id, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                IconButton(onClick = { model.favorite(app.id) }) { Icon(if (app.id in state.favorites) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Избранное") }
                IconButton(onClick = close) { Icon(Icons.Outlined.Close, "Закрыть") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(app.summary, style = MaterialTheme.typography.titleMedium)
                Text("${app.version} · ${sizeLabel(app.size)} · ${app.license}", style = MaterialTheme.typography.labelLarge)
                if (installed != null && installed < app.code) Text("${installedVersion(context, app.id)} → ${app.version}", color = MaterialTheme.colorScheme.primary)
                if (media.screenshots.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(media.screenshots) { i, url -> AsyncImage(url, "Скриншот ${i + 1}", Modifier.height(230.dp).width(130.dp).clip(RoundedCornerShape(16.dp)).clickable { gallery = i }, contentScale = ContentScale.Crop) }
                }
                if (media.changelog.isNotBlank()) {
                    Text("Что нового", style = MaterialTheme.typography.titleMedium)
                    Text(media.changelog, style = MaterialTheme.typography.bodyMedium)
                }
                Text("Об этом приложении", style = MaterialTheme.typography.titleMedium)
                Text(app.description.ifBlank { "Описание не предоставлено источником." }, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis)
                if (app.description.length > 220) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Читать дальше") }
                Text("Перед установкой", style = MaterialTheme.typography.titleMedium)
                Text("• Подобрано под Android и архитектуру устройства\n• Метаданные из проверенного индекса F-Droid\n• Хеш и подпись APK проверяются после скачивания", style = MaterialTheme.typography.bodySmall)
                Text("Проверки не гарантируют отсутствие вредоносного поведения.", style = MaterialTheme.typography.bodySmall)
                if (app.antiFeatures.isNotEmpty()) Text("Особенности: ${app.antiFeatures.joinToString()}", color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Скрыть из обновлений", Modifier.weight(1f)); Switch(app.id in state.ignored, { model.ignore(app.id) }) }
                if (app.permissions.isNotEmpty()) Text("Разрешения:\n" + app.permissions.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                if (installed != null) TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.id}"))) }) { Text("Системные настройки") }
                if (app.source.startsWith("https://")) TextButton(onClick = { open(app.source) }) { Icon(Icons.Outlined.Code, null); Spacer(Modifier.width(8.dp)); Text("Исходный код") }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${app.name}\nhttps://f-droid.org/packages/${app.id}/") }
                    context.startActivity(Intent.createChooser(send, "Поделиться"))
                }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Поделиться") }
                Text("API ${app.minSdk}+ · target ${app.targetSdk}\n${app.abis.joinToString().ifBlank { "Без ограничений ABI" }}\nSHA-256: ${app.hash}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider()
            Button(onClick = {
                if (installed != null && installed >= app.code) {
                    val launch = context.packageManager.getLaunchIntentForPackage(app.id)
                    if (launch != null) context.startActivity(launch) else model.message("Нет доступного экрана запуска")
                } else { model.install(app); close() }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp)) {
                Icon(if (installed != null && installed >= app.code) Icons.Outlined.OpenInNew else Icons.Outlined.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (installed == null) "Установить · ${sizeLabel(app.size)}" else if (installed < app.code) "Обновить · ${sizeLabel(app.size)}" else "Открыть")
            }
        }
    }
    if (gallery >= 0 && media.screenshots.isNotEmpty()) Dialog(onDismissRequest = { gallery = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val pager = rememberPagerState(initialPage = gallery, pageCount = { media.screenshots.size })
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${pager.currentPage + 1} / ${media.screenshots.size}", Modifier.weight(1f))
                    IconButton(onClick = { gallery = -1 }) { Icon(Icons.Outlined.Close, "Закрыть галерею") }
                }
                HorizontalPager(pager, Modifier.weight(1f)) { i -> AsyncImage(media.screenshots[i], "Скриншот ${i + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
        }
    }
}
@Composable
internal fun StoreIcon(app: StoreApp) {
    var failed by remember(app.icon) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(52.dp)) {
        if (app.icon != null && !failed) AsyncImage(app.icon, null, Modifier.padding(6.dp), onError = { failed = true })
        else Box(contentAlignment = Alignment.Center) { Text(app.name.take(1), style = MaterialTheme.typography.headlineSmall) }
    }
}
internal fun sizeLabel(size: Long): String = String.format(Locale.getDefault(), "%.1f МБ", size / 1_000_000.0)
internal fun installedVersion(context: android.content.Context, id: String): String = runCatching { context.packageManager.getPackageInfo(id, 0).versionName ?: "установленная" }.getOrDefault("установленная")

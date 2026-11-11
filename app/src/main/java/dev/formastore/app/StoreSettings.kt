package dev.formastore.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreSettings(state: StoreState, model: StoreModel, close: () -> Unit) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(Appearance.export(context)) } }
            .onSuccess { model.message("Настройки сохранены") }.onFailure { model.message("Ошибка экспорта") }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val b = CharArray(1_048_577); var n = 0
                while (n < b.size) { val count = reader.read(b, n, b.size - n); if (count < 0) break; n += count }
                require(n <= 1_048_576); String(b, 0, n)
            } ?: error("Файл недоступен")
            Appearance.import(context, text); model.reloadSettings()
        }.onSuccess { model.message("Настройки и избранное восстановлены") }.onFailure { model.message("Файл настроек не распознан") }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> model.message(if (granted) "Уведомления загрузок включены" else "Уведомления не разрешены") }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Твой магазин", style = MaterialTheme.typography.headlineSmall)
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная").forEach { (mode, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(Appearance.mode == mode, { Appearance.mode = mode; Appearance.save(context) }); TextButton(onClick = { Appearance.mode = mode; Appearance.save(context) }) { Text(label) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Цвета от обоев", Modifier.weight(1f)); Switch(Appearance.dynamic, { Appearance.dynamic = it; Appearance.save(context) }, enabled = Build.VERSION.SDK_INT >= 31) }
            Text("Если выключить — фирменный синий Forma.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Уменьшить движение", Modifier.weight(1f)); Switch(Appearance.reduced, { Appearance.reduced = it; Appearance.save(context) }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Скачивать только по Wi-Fi", Modifier.weight(1f)); Switch(state.wifiOnly, model::wifiOnly) }
            Text("Обновление каталога при открытии", style = MaterialTheme.typography.titleMedium)
            listOf(0 to "Вручную", 6 to "Каждые 6 часов", 24 to "Раз в сутки", 72 to "Раз в три дня").forEach { (hours, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(state.intervalHours == hours, { model.interval(hours) }); TextButton(onClick = { model.interval(hours) }) { Text(label) } }
            }
            if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Разрешить уведомления загрузок") }
            OutlinedButton(onClick = { export.launch("forma-settings.json") }, modifier = Modifier.fillMaxWidth()) { Text("Экспорт настроек и избранного") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text("Импорт из файла") }
            Text("Очередь сохранена локально. Фоновая загрузка работает, пока Android не остановит процесс. После остановки APK скачивается заново. Установка требует подтверждения.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

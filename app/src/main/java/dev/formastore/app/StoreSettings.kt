package dev.formastore.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreSettings(state: StoreState, model: StoreModel, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Твой магазин", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Только Wi-Fi")
                    Text("Каталог, APK и проверка GitHub", style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.wifiOnly, model::wifiOnly)
            }
            Text("Проверять свежесть каталога при запуске", style = MaterialTheme.typography.titleMedium)
            listOf(0 to "Только вручную", 6 to "Каждые 6 часов", 24 to "Раз в сутки", 72 to "Раз в три дня").forEach { (hours, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(state.intervalHours == hours, { model.interval(hours) })
                    TextButton(onClick = { model.interval(hours) }) { Text(text) }
                }
            }
            Text("Проверка выполняется при открытии, не по фоновому расписанию. Сохранённый каталог остаётся доступен без сети.", style = MaterialTheme.typography.bodySmall)
            Text("Очередь скачиваний живёт в процессе приложения. Принудительная остановка или выгрузка Android прервёт её; постоянный фоновый сервис ещё не подключён.", style = MaterialTheme.typography.bodySmall)
            Text("Исключено из обновлений: ${state.ignored.size}. Исключения меняются в карточках приложений.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

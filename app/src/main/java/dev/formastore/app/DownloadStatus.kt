package dev.formastore.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class TransferStatus(
    val name: String = "", val downloaded: Long = 0, val total: Long = 0,
    val bytesPerSecond: Long = 0, val phase: String = "Подготовка"
)
@Composable
internal fun DownloadStatus(status: TransferStatus, queue: Int, ready: Boolean, open: () -> Unit) {
    Surface(Modifier.padding(horizontal = 24.dp, vertical = 6.dp).fillMaxWidth().clickable(onClick = open),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Download, null, Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(status.name.ifBlank { if (ready) "Готово к установке" else "Очередь загрузок" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (ready) "Проверка завершена · нужно подтверждение Android" else status.phase, style = MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.Outlined.ExpandLess, "Открыть очередь")
            }
            if (!ready && status.phase == "Скачивание" && status.total > 0) {
                val progress = (status.downloaded.toFloat() / status.total).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                val speed = if (status.bytesPerSecond > 0) " · ${amount(status.bytesPerSecond)}/с" else ""
                val remaining = if (status.bytesPerSecond > 0) ((status.total - status.downloaded).coerceAtLeast(0) / status.bytesPerSecond) else -1
                Text("${(progress * 100).toInt()}% · ${amount(status.downloaded)} / ${amount(status.total)}$speed" + if (remaining >= 0) " · ~${if (remaining >= 60) "${remaining / 60} мин" else "$remaining с"}" else "", style = MaterialTheme.typography.labelSmall)
            } else if (!ready && status.name.isNotBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (queue > 0) Text("Ожидают: $queue · нажми, чтобы управлять", style = MaterialTheme.typography.labelSmall)
        }
    }
}
private fun amount(bytes: Long): String = if (bytes < 1_000_000) "${bytes / 1000} КБ" else String.format(Locale.getDefault(), "%.1f МБ", bytes / 1_000_000.0)

package dev.formastore.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun TransferCard(state: StoreState, open: () -> Unit) {
    val context = LocalContext.current
    val status by produceState(TransferStatus(), state.progress != null, state.ready, state.apps) {
        var previousBytes = 0L
        var previousTime = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val prefs = context.getSharedPreferences("transfers", 0)
            val id = prefs.getString("active", null) ?: prefs.getString("ready-id", "")
            val app = state.apps.firstOrNull { it.id == id }
            val bytes = withContext(Dispatchers.IO) { File(context.filesDir, "apks/download.part").length() }
            val now = android.os.SystemClock.elapsedRealtime()
            val speed = if (now - previousTime >= 300 && bytes >= previousBytes) (bytes - previousBytes) * 1000 / (now - previousTime) else 0L
            val total = app?.size ?: 0L
            value = TransferStatus(app?.name ?: if (id == context.packageName) "Обновление Forma" else "APK",
                bytes, total, speed, when {
                    state.ready != null -> "Готово"
                    bytes == 0L -> "Подключение к серверу…"
                    total > 0 && bytes >= total -> "Проверка целостности и подписи…"
                    else -> "Скачивание"
                })
            previousBytes = bytes; previousTime = now
            delay(650)
        }
    }
    DownloadStatus(status, state.queueNames.size, state.ready != null, open)
}

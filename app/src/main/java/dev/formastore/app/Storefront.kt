package dev.formastore.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun Storefront(apps: List<StoreApp>, openApp: (StoreApp) -> Unit, openCategory: (String) -> Unit, all: () -> Unit) {
    val picks = remember(apps) {
        val ids = listOf("org.fossify.gallery", "org.videolan.vlc", "org.mozilla.fennec_fdroid", "com.termux", "org.schabi.newpipe", "net.osmand.plus", "com.artemchep.keyguard", "org.tasks")
        ids.mapNotNull { id -> apps.firstOrNull { it.id == id } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 14.dp, bottomStart = 36.dp),
            color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(24.dp)) {
                Column(Modifier.fillMaxWidth(.76f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("СВОБОДА ВЫБОРА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Твой Pixel.\nТвои правила.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("Открытые приложения.\nБез аккаунта и лишнего шума.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = all, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Text("Найти своё"); Spacer(Modifier.width(10.dp)); Icon(Icons.Outlined.ArrowOutward, null, Modifier.size(18.dp))
                    }
                }
                Surface(shape = RoundedCornerShape(topStart = 35.dp, topEnd = 15.dp, bottomEnd = 35.dp, bottomStart = 15.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 45.dp).size(75.dp).graphicsLayer { rotationZ = 12f }) {
                    Icon(painterResource(R.drawable.ic_peek_mono), null, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).size(38.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("В своём ритме", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            val groups = listOf("Internet" to "На связи", "Multimedia" to "Звук и видео", "Games" to "Поиграем", "System" to "Под капотом", "Office" to "По делу", "Security" to "Приватность")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(groups.filter { (key, _) -> apps.any { key in it.categories } }) { (key, title) ->
                    Surface(onClick = { openCategory(key) }, shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.width(115.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(when(key) { "Internet" -> Icons.Outlined.Public; "Multimedia" -> Icons.Outlined.Headphones; "Games" -> Icons.Outlined.SportsEsports; "System" -> Icons.Outlined.Tune; "Security" -> Icons.Outlined.Shield; else -> Icons.Outlined.WorkOutline }, null)
                            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                        }
                    }
                }
            }
        }
        if (picks.isNotEmpty()) AppShelf("С чего начать", "Небольшая редакционная подборка Forma", picks, openApp)
        val light = remember(apps) { apps.filter { it.size in 1..10_000_000 && it.summary.isNotBlank() }.sortedBy { it.size }.take(14) }
        if (light.isNotEmpty()) AppShelf("Маленькие, да удаленькие", "До 10 МБ · размер из каталога", light, openApp)
        val games = remember(apps) { apps.filter { "Games" in it.categories }.take(14) }
        if (games.isNotEmpty()) AppShelf("Немного не по делу", "Игры из открытого каталога", games, openApp)
        Surface(onClick = all, color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ещё есть что открыть.", style = MaterialTheme.typography.titleLarge)
                    Text("${apps.size} совместимых приложений", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Outlined.ArrowForward, null)
            }
        }
    }
}
@Composable
private fun AppShelf(title: String, subtitle: String, apps: List<StoreApp>, open: (StoreApp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(apps, key = { it.id }) { app ->
                Surface(onClick = { open(app) }, shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.width(153.dp).padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StoreIcon(app)
                        Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.summary.ifBlank { app.id }, style = MaterialTheme.typography.bodySmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sizeLabel(app.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

package dev.formastore.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun FormaNavigation(selected: Int, updates: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp, shadowElevation = 4.dp, modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            BoxWithConstraints(Modifier.padding(6.dp)) {
                val slot = maxWidth / 3
                val x by animateDpAsState(slot * selected, spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow), label = "pill-slide")
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.offset(x = x).width(slot).matchParentSizeHeight()) {}
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    listOf("Каталог", "Обновления", "Библиотека").forEachIndexed { i, title ->
                        val active = selected == i
                        val scale by animateFloatAsState(if (active) 1.08f else 1f, spring(dampingRatio = .55f, stiffness = 400f), label = "tab-icon")
                        val tint by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab-tint")
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(30.dp))
                            .selectable(active, role = Role.Tab, onClick = { onSelect(i) })
                            .padding(horizontal = 1.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BadgedBox(badge = { if (i == 1 && updates > 0) Badge { Text(if (updates > 99) "99+" else "$updates") } }) {
                                Icon(when(i) { 0 -> Icons.Outlined.Explore; 1 -> Icons.Outlined.SystemUpdate; else -> Icons.Outlined.Widgets }, null,
                                    Modifier.size(21.dp).graphicsLayer { scaleX = scale; scaleY = scale }, tint = tint)
                            }
                            Text(title, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
// Fixed row height avoids measuring the highlight as content and keeps targets >=48dp.
private fun Modifier.matchParentSizeHeight() = height(62.dp)

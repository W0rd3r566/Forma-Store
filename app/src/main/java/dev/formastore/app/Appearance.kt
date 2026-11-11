package dev.formastore.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

internal object Appearance {
    var mode by mutableStateOf("system")
    var dynamic by mutableStateOf(true)
    var reduced by mutableStateOf(false)
    fun load(context: Context) {
        val p = context.getSharedPreferences("appearance", 0)
        mode = p.getString("mode", "system") ?: "system"
        dynamic = p.getBoolean("dynamic", true)
        reduced = p.getBoolean("reduced", false)
    }
    fun save(context: Context) {
        context.getSharedPreferences("appearance", 0).edit().putString("mode", mode).putBoolean("dynamic", dynamic).putBoolean("reduced", reduced).apply()
    }
    fun export(context: Context): String {
        val p = context.getSharedPreferences("store-settings", 0)
        return JSONObject().apply {
            put("format", 1); put("theme", mode); put("dynamic", dynamic); put("reduced", reduced)
            put("favorites", JSONArray(p.getStringSet("favorites", emptySet())!!.sorted()))
            put("ignored", JSONArray(p.getStringSet("ignored", emptySet())!!.sorted()))
            put("wifi", p.getBoolean("wifi", false)); put("interval", p.getInt("interval", 24))
        }.toString(2)
    }
    fun import(context: Context, text: String) {
        require(text.length <= 1_048_576) { "Файл слишком большой" }
        val j = JSONObject(text)
        require(j.getInt("format") == 1) { "Неизвестный формат" }
        val nextMode = j.getString("theme")
        require(nextMode in listOf("system", "light", "dark"))
        val hours = j.getInt("interval")
        require(hours in listOf(0, 6, 24, 72))
        fun packages(key: String): Set<String> {
            val a = j.getJSONArray(key)
            require(a.length() <= 10000)
            return (0 until a.length()).map { a.getString(it).also { id -> require(Regex("^[A-Za-z0-9_.]+$").matches(id)) } }.toSet()
        }
        val favorites = packages("favorites"); val ignored = packages("ignored")
        context.getSharedPreferences("store-settings", 0).edit().putStringSet("favorites", favorites)
            .putStringSet("ignored", ignored).putBoolean("wifi", j.getBoolean("wifi")).putInt("interval", hours).apply()
        mode = nextMode; dynamic = j.getBoolean("dynamic"); reduced = j.getBoolean("reduced"); save(context)
    }
}
@Composable
internal fun FormaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (Appearance.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val colors = if (Appearance.dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFAFC6FF), primaryContainer = Color(0xFF254578))
    else lightColorScheme(primary = Color(0xFF315DA8), primaryContainer = Color(0xFFDCE6FF))
    MaterialTheme(colorScheme = colors, content = content)
}

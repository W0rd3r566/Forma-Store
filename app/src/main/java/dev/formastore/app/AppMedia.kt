package dev.formastore.app

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.jar.JarFile

internal data class AppMedia(val screenshots: List<String> = emptyList(), val changelog: String = "")
internal suspend fun appMedia(context: Context, id: String): AppMedia = withContext(Dispatchers.IO) {
    runCatching {
        JarFile(File(context.filesDir, "index-v1.jar"), true).use { jar ->
            val entry = checkNotNull(jar.getJarEntry("index-v1.json"))
            val bytes = jar.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size().toLong() + count <= 180L * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            check(entry.codeSigners?.any { sha(it.signerCertPath.certificates.first().encoded) == FINGERPRINT } == true)
            val apps = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("apps")
            val meta = (0 until apps.length()).asSequence().map { apps.getJSONObject(it) }.firstOrNull { it.optString("packageName") == id } ?: return@use AppMedia()
            val locales = meta.optJSONObject("localized") ?: return@use AppMedia()
            val language = Locale.getDefault().language
            val keys = locales.keys().asSequence().toList()
            val key = listOf(Locale.getDefault().toLanguageTag(), "$language-${language.uppercase(Locale.ROOT)}", language, "en-US").firstOrNull { locales.has(it) }
                ?: keys.firstOrNull() ?: return@use AppMedia()
            val loc = locales.getJSONObject(key)
            val screenshots = loc.optJSONArray("phoneScreenshots")
            val urls = if (screenshots == null) emptyList() else (0 until screenshots.length()).mapNotNull { i ->
                val name = screenshots.optString(i)
                if (Regex("^[A-Za-z0-9_.+-]+$").matches(name) && Regex("^[A-Za-z0-9-]+$").matches(key)) "$REPO$id/$key/phoneScreenshots/$name" else null
            }
            AppMedia(urls, Html.fromHtml(loc.optString("whatsNew"), Html.FROM_HTML_MODE_COMPACT).toString())
        }
    }.getOrDefault(AppMedia())
}

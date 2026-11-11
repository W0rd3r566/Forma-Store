package dev.formastore.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.jar.JarFile

internal const val REPO = "https://f-droid.org/repo/"
internal const val FINGERPRINT = "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab"
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
internal data class StoreApp(
    val id: String, val name: String, val summary: String, val description: String,
    val license: String, val source: String, val icon: String?, val categories: List<String>,
    val version: String, val code: Long, val apk: String, val hash: String, val size: Long,
    val minSdk: Int, val targetSdk: Int, val abis: List<String>, val permissions: List<String>,
    val antiFeatures: List<String>
)
internal data class Catalog(val apps: List<StoreApp>, val timestamp: Long)
internal object Network {
    suspend fun download(url: String, destination: File, max: Long, progress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        require(URL(url).protocol == "https") { "Только HTTPS" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "FormaStore/${BuildConfig.VERSION_NAME}")
            check(connection.responseCode == 200) { "Сервер: HTTP ${connection.responseCode}" }
            check(connection.url.protocol == "https") { "Небезопасный редирект" }
            val expected = connection.contentLengthLong
            check(expected <= max) { "Слишком большой файл" }
            var count = 0L
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    count += n
                    check(count <= max) { "Превышен лимит размера" }
                    output.write(buffer, 0, n)
                    progress(count, expected)
                }
            } }
            check(expected < 0 || count == expected) { "Загрузка оборвалась" }
        } catch (e: Exception) {
            destination.delete()
            throw e
        } finally { connection.disconnect() }
    }
}
internal class Repository(private val context: Context) {
    private val lock = Mutex()
    private val index = File(context.filesDir, "index-v1.jar")
    suspend fun load(force: Boolean): Catalog = lock.withLock { withContext(Dispatchers.IO) {
        if (index.exists() && !force) {
            try { return@withContext verified(index) } catch (_: Exception) { index.delete() }
        }
        val pending = File(context.cacheDir, "index.pending")
        Network.download(REPO + "index-v1.jar", pending, 80L * 1024 * 1024)
        val result = verified(pending)
        if (index.exists()) {
            val old = runCatching { verified(index).timestamp }.getOrDefault(0)
            check(result.timestamp >= old) { "Отказ: индекс старее сохранённого" }
        }
        check(pending.copyTo(index, overwrite = true).exists())
        pending.delete()
        result
    } }
    private fun verified(file: File): Catalog {
        val text = JarFile(file, true).use { jar ->
            val entry = checkNotNull(jar.getJarEntry("index-v1.json")) { "В архиве нет индекса" }
            val bytes = jar.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(output.size().toLong() + n <= 180L * 1024 * 1024) { "Индекс слишком большой" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            // Reading to EOF triggers JAR entry digest and signature validation.
            val signers = entry.codeSigners ?: error("Индекс не подписан")
            check(signers.any { signer -> sha(signer.signerCertPath.certificates.first().encoded) == FINGERPRINT }) { "Неизвестная подпись F-Droid" }
            String(bytes, Charsets.UTF_8)
        }
        val json = JSONObject(text)
        val timestamp = json.getJSONObject("repo").getLong("timestamp")
        check(timestamp <= System.currentTimeMillis() + 24 * 60 * 60 * 1000L) { "Индекс из будущего: проверьте часы" }
        val apps = json.getJSONArray("apps")
        val packages = json.getJSONObject("packages")
        val result = ArrayList<StoreApp>()
        for (i in 0 until apps.length()) {
            val meta = apps.getJSONObject(i)
            val id = meta.getString("packageName")
            val versions = packages.optJSONArray(id) ?: continue
            var best: JSONObject? = null
            for (j in 0 until versions.length()) {
                val v = versions.getJSONObject(j)
                if (v.optInt("minSdkVersion", 1) > Build.VERSION.SDK_INT) continue
                if (v.optInt("maxSdkVersion", Int.MAX_VALUE) < Build.VERSION.SDK_INT) continue
                val abis = v.optJSONArray("nativecode")
                if (abis != null && abis.length() > 0 && (0 until abis.length()).none { abis.getString(it) in Build.SUPPORTED_ABIS }) continue
                // Android 14+ blocks new installation of apps targeting below 23;
                // Android 15+ raises this minimum to 24.
                val floor = if (Build.VERSION.SDK_INT >= 35) 24 else if (Build.VERSION.SDK_INT >= 34) 23 else 1
                if (v.optInt("targetSdkVersion", 1) < floor) continue
                if (best == null || v.getLong("versionCode") > best.getLong("versionCode")) best = v
            }
            val v = best ?: continue
            if (v.optString("hashType") != "sha256") continue
            val apk = v.optString("apkName")
            val hash = v.optString("hash")
            if (!Regex("^[A-Za-z0-9_.+-]+\\.apk$").matches(apk) || !Regex("^[a-fA-F0-9]{64}$").matches(hash)) continue
            val localized = meta.optJSONObject("localized")
            val language = Locale.getDefault().language
            val loc = localized?.optJSONObject(Locale.getDefault().toLanguageTag())
                ?: localized?.optJSONObject(language)
                ?: localized?.optJSONObject("$language-${language.uppercase(Locale.ROOT)}")
                ?: localized?.optJSONObject("en-US")
            fun field(key: String, fallback: String = ""): String = loc?.optString(key)?.takeIf { it.isNotBlank() } ?: meta.optString(key, fallback)
            val description = Html.fromHtml(field("description"), Html.FROM_HTML_MODE_COMPACT).toString()
            val iconPath = meta.optString("icon").takeIf { it.isNotBlank() && !it.contains("..") && !it.startsWith("/") }
            val cats = meta.optJSONArray("categories")
            val anti = meta.optJSONArray("antiFeatures")
            val abis = v.optJSONArray("nativecode")
            val permissions = v.optJSONArray("uses-permission")
            result += StoreApp(id, field("name", id), field("summary"), description,
                meta.optString("license", "Не указана"), meta.optString("sourceCode"),
                iconPath?.let { REPO + "icons/" + it },
                if (cats == null) emptyList() else (0 until cats.length()).map { cats.getString(it) },
                v.optString("versionName", v.getLong("versionCode").toString()), v.getLong("versionCode"), apk, hash.lowercase(Locale.ROOT), v.optLong("size"),
                v.optInt("minSdkVersion", 1), v.optInt("targetSdkVersion", 1),
                if (abis == null) emptyList() else (0 until abis.length()).map { abis.getString(it) },
                if (permissions == null) emptyList() else (0 until permissions.length()).mapNotNull { permissions.optJSONArray(it)?.optString(0) },
                if (anti == null) emptyList() else (0 until anti.length()).map { anti.getString(it) })
        }
        check(result.isNotEmpty()) { "В индексе нет совместимых приложений" }
        return Catalog(result.sortedBy { it.name.lowercase(Locale.ROOT) }, timestamp)
    }
}
internal fun installedCode(context: Context, id: String): Long? = try {
    val info = context.packageManager.getPackageInfo(id, 0)
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
} catch (_: PackageManager.NameNotFoundException) { null }

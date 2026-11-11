package dev.formastore.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal data class Release(val version: String, val code: Long, val url: String, val notes: String, val size: Long)
internal data class StoreState(
    val apps: List<StoreApp> = emptyList(), val installed: Map<String, Long> = emptyMap(),
    val loading: Boolean = false, val message: String? = null, val progress: String? = null,
    val release: Release? = null, val ready: File? = null, val checking: Boolean = false
)
internal class StoreModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val repository = Repository(application)
    private val mutable = MutableStateFlow(StoreState())
    val state = mutable.asStateFlow()
    private var downloadJob: Job? = null
    private fun update(block: (StoreState) -> StoreState) { mutable.value = block(mutable.value) }
    init { refresh(false); checkRelease(false) }
    fun message(text: String?) = update { it.copy(message = text) }
    fun dismissRelease() = update { it.copy(release = null) }
    fun consumeReady() = update { it.copy(ready = null) }
    fun refresh(force: Boolean) {
        if (mutable.value.loading) return
        update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val catalog = repository.load(force)
                val installed = withContext(Dispatchers.IO) { catalog.apps.mapNotNull { app -> installedCode(context, app.id)?.let { app.id to it } }.toMap() }
                update { it.copy(apps = catalog.apps, installed = installed, loading = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(loading = false, message = "Каталог: ${e.localizedMessage ?: "ошибка сети"}") } }
        }
    }
    fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = mutable.value.apps.mapNotNull { app -> installedCode(context, app.id)?.let { app.id to it } }.toMap()
            withContext(Dispatchers.Main) { update { it.copy(installed = installed) } }
        }
    }
    private fun signatures(info: android.content.pm.PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certificates?.map { sha(it.toByteArray()) }?.toSet().orEmpty()
    }
    private fun checkArchive(file: File, id: String, code: Long) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Android не распознал APK")
        val actualCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        check(archive.packageName == id && actualCode == code) { "Идентификатор или версия APK не совпадают с каталогом" }
        check(signatures(archive).isNotEmpty()) { "У APK нет читаемой подписи" }
        val installed = try { pm.getPackageInfo(id, flags) } catch (_: PackageManager.NameNotFoundException) { null }
        if (installed != null) {
            val existing = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
            check(code > existing) { "Установлена такая же или более новая версия" }
            check(signatures(installed) == signatures(archive)) { "Подпись отличается от установленного приложения. Обновление остановлено: ваши данные не затронуты." }
        }
    }
    fun install(app: StoreApp) = startDownload(app.id, app.code, REPO + app.apk, app.hash, app.size)
    fun installRelease(release: Release) = startDownload(context.packageName, release.code, release.url, null, release.size)
    fun cancelDownload() { downloadJob?.cancel() }
    private fun startDownload(id: String, code: Long, url: String, expectedHash: String?, expectedSize: Long) {
        if (downloadJob?.isActive == true) { message("Дождитесь загрузки или отмените её."); return }
        downloadJob = viewModelScope.launch {
            val dir = File(context.filesDir, "apks").apply { mkdirs() }
            val pending = File(dir, "download.part")
            val ready = File(dir, "ready.apk")
            update { it.copy(progress = "Подготовка загрузки…", ready = null) }
            try {
                withContext(Dispatchers.IO) {
                    check(expectedSize in 1..1_500_000_000L) { "Недопустимый размер APK" }
                    var lastTick = 0L
                    Network.download(url, pending, expectedSize) { done, _ ->
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 150) {
                            lastTick = now
                            viewModelScope.launch { update { it.copy(progress = "Загрузка ${(done * 100 / expectedSize).coerceIn(0, 100)}%") } }
                        }
                    }
                    check(pending.length() == expectedSize) { "Размер APK не совпал" }
                    if (expectedHash != null) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        pending.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
                        check(digest.digest().hex().equals(expectedHash, true)) { "SHA-256 APK не совпал. Установка запрещена." }
                    }
                    checkArchive(pending, id, code)
                    ready.delete()
                    check(pending.renameTo(ready)) { "Не удалось подготовить файл" }
                }
                update { it.copy(progress = null, ready = ready) }
            } catch (e: CancellationException) {
                pending.delete(); update { it.copy(progress = null) }; throw e
            } catch (e: Exception) {
                pending.delete(); update { it.copy(progress = null, message = e.localizedMessage ?: "Не удалось подготовить APK") }
            }
        }
    }
    fun checkRelease(manual: Boolean) {
        if (mutable.value.checking) return
        val prefs = context.getSharedPreferences("updates", 0)
        if (!manual && System.currentTimeMillis() - prefs.getLong("checked", 0) in 0 until 21_600_000L) return
        update { it.copy(checking = true) }
        viewModelScope.launch {
            val file = File(context.cacheDir, "release.json")
            try {
                val release = withContext(Dispatchers.IO) {
                    Network.download("https://api.github.com/repos/W0rd3r566/Forma-Store/releases/latest", file, 1_048_576)
                    val json = JSONObject(file.readText())
                    check(!json.optBoolean("prerelease") && !json.optBoolean("draft")) { "Нет стабильного релиза" }
                    val version = json.getString("tag_name").removePrefix("v")
                    val remote = versionCode(version) ?: error("Неверный тег релиза")
                    if (remote <= BuildConfig.VERSION_CODE) return@withContext null
                    val assets = json.getJSONArray("assets")
                    val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "forma-store.apk" } ?: error("В релизе нет forma-store.apk")
                    val url = asset.getString("browser_download_url")
                    check(url.startsWith("https://github.com/W0rd3r566/Forma-Store/releases/download/")) { "Неожиданный адрес APK" }
                    Release(version, remote.toLong(), url, json.optString("body").take(12000), asset.getLong("size"))
                }
                prefs.edit().putLong("checked", System.currentTimeMillis()).apply()
                update { it.copy(release = release ?: it.release, checking = false, message = if (manual && release == null) "Установлена актуальная версия" else it.message) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(checking = false, message = if (manual) "Проверка GitHub: ${e.localizedMessage}. Если релизов ещё нет, это нормально." else it.message) } }
            finally { file.delete() }
        }
    }
}
internal fun versionCode(version: String): Int? {
    val match = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(version) ?: return null
    val values = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    if (values.any { it > 99 }) return null
    return values[0] * 10000 + values[1] * 100 + values[2]
}

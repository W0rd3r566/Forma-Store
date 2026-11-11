package dev.formastore.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class Release(val version: String, val code: Long, val url: String, val notes: String, val size: Long)
internal data class StoreState(
    val apps: List<StoreApp> = emptyList(), val installed: Map<String, Long> = emptyMap(),
    val loading: Boolean = false, val message: String? = null, val progress: String? = null,
    val release: Release? = null, val ready: File? = null, val checking: Boolean = false,
    val favorites: Set<String> = emptySet(), val ignored: Set<String> = emptySet(),
    val wifiOnly: Boolean = false, val intervalHours: Int = 24,
    val queueNames: List<String> = emptyList(), val online: Boolean = true, val catalogError: String? = null
)
// Application-lived scope: navigating away or recreating Activity does not cancel transfers.
internal object TransferScope { val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
internal class StoreModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val scope = TransferScope.scope
    private val repository = Repository(application)
    private val cache = CatalogCache(application)
    private val prefs = application.getSharedPreferences("store-settings", 0)
    private val transfers = application.getSharedPreferences("transfers", 0)
    private val mutable = MutableStateFlow(StoreState())
    val state = mutable.asStateFlow()
    private var downloadJob: Job? = null
    private val queue = ArrayDeque<String>()
    private var handedOff = false
    private fun update(block: (StoreState) -> StoreState) { mutable.value = block(mutable.value) }
    init {
        reloadSettings()
        val pending = transfers.getString("queue", "")!!.split('\n').filter { it.isNotBlank() }
        val active = transfers.getString("active", null)
        (listOfNotNull(active) + pending).distinct().forEach(queue::addLast)
        val ready = File(context.filesDir, "apks/ready.apk")
        if (transfers.getBoolean("ready", false) && ready.exists()) update { it.copy(ready = ready) }
        scope.launch {
            val local = withContext(Dispatchers.IO) { runCatching { cache.load() }.getOrDefault(emptyList()) }
            if (local.isNotEmpty()) { update { it.copy(apps = local) }; rescan(); queueState(); resumeQueue() }
            val age = withContext(Dispatchers.IO) { runCatching { System.currentTimeMillis() - cache.savedAt() }.getOrDefault(Long.MAX_VALUE) }
            if (local.isEmpty() || mutable.value.intervalHours > 0 && age > mutable.value.intervalHours * 3_600_000L) refresh(local.isNotEmpty())
        }
        checkRelease(false)
    }
    fun reloadSettings() {
        update { it.copy(favorites = prefs.getStringSet("favorites", emptySet())!!.toSet(), ignored = prefs.getStringSet("ignored", emptySet())!!.toSet(), wifiOnly = prefs.getBoolean("wifi", false), intervalHours = prefs.getInt("interval", 24)) }
    }
    fun message(text: String?) = update { it.copy(message = text) }
    fun dismissRelease() = update { it.copy(release = null) }
    fun consumeReady() {
        transfers.edit().putBoolean("ready", false).apply()
        update { it.copy(ready = null) }
        resumeQueue()
    }
    fun handedToInstaller() {
        handedOff = true
        update { it.copy(ready = null) }
        // Do not replace ready.apk until the installer returns.
    }
    private fun toggle(key: String, id: String, old: Set<String>): Set<String> {
        val next = if (id in old) old - id else old + id
        prefs.edit().putStringSet(key, next).apply()
        return next
    }
    fun favorite(id: String) = update { it.copy(favorites = toggle("favorites", id, it.favorites)) }
    fun ignore(id: String) = update { it.copy(ignored = toggle("ignored", id, it.ignored)) }
    fun wifiOnly(value: Boolean) { prefs.edit().putBoolean("wifi", value).apply(); update { it.copy(wifiOnly = value) } }
    fun interval(hours: Int) { prefs.edit().putInt("interval", hours).apply(); update { it.copy(intervalHours = hours) } }
    private fun network(): android.net.NetworkCapabilities? {
        val m = context.getSystemService(android.net.ConnectivityManager::class.java)
        return m.getNetworkCapabilities(m.activeNetwork)
    }
    private fun allowedNetwork(): Boolean = !mutable.value.wifiOnly || network()?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    fun refresh(force: Boolean) {
        if (mutable.value.loading) return
        if (!allowedNetwork()) { message("Разрешены загрузки только по Wi-Fi"); return }
        update { it.copy(loading = true, catalogError = null, online = network()?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) }
        scope.launch {
            try {
                val catalog = repository.load(force)
                withContext(Dispatchers.IO) { cache.save(catalog.apps) }
                update { it.copy(apps = catalog.apps, loading = false) }
                rescan(); queueState(); resumeQueue()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(loading = false, catalogError = e.localizedMessage, message = "Не удалось обновить каталог. Сохранённые данные не удалены.") } }
        }
    }
    fun rescan() {
        update { it.copy(online = network()?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) }
        scope.launch {
            val installed = withContext(Dispatchers.IO) { mutable.value.apps.mapNotNull { app -> installedCode(context, app.id)?.let { app.id to it } }.toMap() }
            update { it.copy(installed = installed) }
            if (handedOff) {
                handedOff = false
                val id = transfers.getString("ready-id", "")!!
                val expected = transfers.getLong("ready-code", -1)
                val actual = withContext(Dispatchers.IO) { installedCode(context, id) }
                message(if (actual != null && actual >= expected) "Приложение установлено" else "Установка не завершена: отменена или отклонена Android. APK можно повторно установить из загрузок.")
                if (actual != null && actual >= expected) consumeReady()
                else update { it.copy(ready = File(context.filesDir, "apks/ready.apk").takeIf(File::exists)) }
            }
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
        val a = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Повреждённый или неподдерживаемый APK")
        val actualCode = if (Build.VERSION.SDK_INT >= 28) a.longVersionCode else @Suppress("DEPRECATION") a.versionCode.toLong()
        check(a.packageName == id && actualCode == code) { "Имя пакета или версия не совпадают с каталогом" }
        check(signatures(a).isNotEmpty()) { "Подпись APK не распознана" }
        val old = try { pm.getPackageInfo(id, flags) } catch (_: PackageManager.NameNotFoundException) { null }
        if (old != null) {
            val existing = if (Build.VERSION.SDK_INT >= 28) old.longVersionCode else @Suppress("DEPRECATION") old.versionCode.toLong()
            check(code > existing) { "Установлена такая же или более новая версия" }
            check(signatures(old) == signatures(a)) { "Конфликт подписи. Данные и установленное приложение не затронуты." }
        }
    }
    private fun queueState() {
        transfers.edit().putString("queue", queue.joinToString("\n")).apply()
        update { s -> s.copy(queueNames = queue.map { id -> s.apps.firstOrNull { it.id == id }?.name ?: id }) }
    }
    fun removeQueued(index: Int) {
        val list = queue.toMutableList()
        if (index in list.indices) list.removeAt(index)
        queue.clear(); queue.addAll(list); queueState()
    }
    private fun resumeQueue() {
        if (downloadJob?.isActive == true || mutable.value.ready != null || handedOff || mutable.value.apps.isEmpty()) return
        val id = queue.firstOrNull() ?: return
        val app = mutable.value.apps.firstOrNull { it.id == id } ?: return
        queue.removeFirst(); queueState()
        startDownload(app.id, app.code, REPO + app.apk, app.hash, app.size)
    }
    fun install(app: StoreApp) {
        if (transfers.getString("active", null) == app.id || app.id in queue) return
        queue.addLast(app.id); queueState(); resumeQueue()
    }
    fun installRelease(r: Release) {
        if (downloadJob?.isActive == true || mutable.value.ready != null) { message("Сначала заверши текущую загрузку"); return }
        startDownload(context.packageName, r.code, r.url, null, r.size)
    }
    fun cancelDownload() { queue.clear(); queueState(); downloadJob?.cancel(); transfers.edit().remove("active").apply(); message("Загрузка и очередь отменены") }
    private fun startDownload(id: String, code: Long, url: String, expectedHash: String?, size: Long) {
        if (!allowedNetwork()) { queue.addFirst(id); queueState(); message("Для загрузки нужен Wi-Fi"); return }
        transfers.edit().putString("active", id).apply()
        downloadJob = scope.launch {
            val dir = File(context.filesDir, "apks").apply { mkdirs() }
            val pending = File(dir, "download.part"); val ready = File(dir, "ready.apk")
            update { it.copy(progress = "Подготовка загрузки…", ready = null) }
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
                withContext(Dispatchers.IO) {
                    check(size in 1..1_500_000_000L) { "Некорректный размер APK" }
                    check(dir.usableSpace > size + 50_000_000) { "Недостаточно свободной памяти" }
                    var tick = 0L
                    Network.download(url, pending, size) { done, _ ->
                        if (System.currentTimeMillis() - tick > 400) {
                            tick = System.currentTimeMillis()
                            val percent = (done * 100 / size).coerceIn(0, 100).toInt()
                            scope.launch { update { it.copy(progress = "Загрузка $percent%") }; DownloadService.progress(context, percent) }
                        }
                    }
                    check(pending.length() == size) { "Размер APK не совпал" }
                    if (expectedHash != null) {
                        val digest = MessageDigest.getInstance("SHA-256")
                        pending.inputStream().use { input -> val buf = ByteArray(65536); while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
                        check(digest.digest().hex().equals(expectedHash, true)) { "SHA-256 не совпал. Установка запрещена." }
                    }
                    checkArchive(pending, id, code)
                    ready.delete(); check(pending.renameTo(ready)) { "Не удалось подготовить файл" }
                }
                transfers.edit().remove("active").putBoolean("ready", true).putString("ready-id", id).putLong("ready-code", code).apply()
                update { it.copy(progress = null, ready = ready) }
            } catch (e: CancellationException) {
                pending.delete(); update { it.copy(progress = null) }; throw e
            } catch (e: Exception) {
                pending.delete(); transfers.edit().remove("active").apply(); update { it.copy(progress = null, message = e.localizedMessage ?: "Ошибка загрузки") }
            } finally { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
    fun checkRelease(manual: Boolean) {
        if (mutable.value.checking) return
        val p = context.getSharedPreferences("updates", 0)
        if (!manual && System.currentTimeMillis() - p.getLong("checked", 0) in 0 until 21_600_000L) return
        if (!allowedNetwork()) { if (manual) message("Проверка доступна по Wi-Fi"); return }
        update { it.copy(checking = true) }
        scope.launch {
            val file = File(context.cacheDir, "release.json")
            try {
                val release = withContext(Dispatchers.IO) {
                    Network.download("https://api.github.com/repos/W0rd3r566/Forma-Store/releases/latest", file, 1_048_576)
                    val j = JSONObject(file.readText())
                    check(!j.optBoolean("prerelease") && !j.optBoolean("draft"))
                    val version = j.getString("tag_name").removePrefix("v")
                    val code = versionCode(version) ?: error("Неверная версия релиза")
                    if (code <= BuildConfig.VERSION_CODE) return@withContext null
                    val assets = j.getJSONArray("assets")
                    val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "forma-store.apk" } ?: error("Нет APK релиза")
                    val url = asset.getString("browser_download_url")
                    check(url.startsWith("https://github.com/W0rd3r566/Forma-Store/releases/download/"))
                    Release(version, code.toLong(), url, j.optString("body").take(12000), asset.getLong("size"))
                }
                p.edit().putLong("checked", System.currentTimeMillis()).apply()
                update { it.copy(release = release ?: it.release, checking = false, message = if (manual && release == null) "Установлена актуальная версия" else it.message) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { update { it.copy(checking = false, message = if (manual) "GitHub: ${e.localizedMessage}" else it.message) } }
            finally { file.delete() }
        }
    }
}
internal fun versionCode(version: String): Int? {
    val match = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$").matchEntire(version) ?: return null
    val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
    if (parts.any { it > 99 }) return null
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}

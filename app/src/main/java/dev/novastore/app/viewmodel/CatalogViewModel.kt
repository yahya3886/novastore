package dev.novastore.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.novastore.app.data.AppEntry
import dev.novastore.app.data.CatalogRepository
import dev.novastore.app.data.IndexPackage
import dev.novastore.app.data.RepoConfig
import dev.novastore.app.data.RepoStore
import dev.novastore.app.data.allCategories
import dev.novastore.app.data.buildEntries
import dev.novastore.app.data.filterEntries
import dev.novastore.app.data.installedVersionCode
import dev.novastore.app.data.withInstalledVersions
import dev.novastore.app.install.ApkDownloader
import dev.novastore.app.install.Installer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Katalog ekranının durumu. */
data class CatalogUiState(
    val repos: List<RepoConfig> = emptyList(),
    val entries: List<AppEntry> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** repoId -> hata mesajı */
    val repoErrors: Map<String, String> = emptyMap(),
    val loadedAt: Long? = null,
) {
    /** Hiç içerik yokken gösterilecek genel hata. */
    val error: String?
        get() = if (entries.isEmpty() && repoErrors.isNotEmpty()) repoErrors.values.firstOrNull() else null
}

enum class InstallPhase { DOWNLOADING, VERIFYING, INSTALLING, PERMISSION_NEEDED, FAILED, DONE }

data class InstallState(
    val phase: InstallPhase,
    /** 0..1 arası; bilinmiyorsa -1. */
    val progress: Float = -1f,
    val message: String? = null,
)

/** Kök ekranın başlatması gereken niyetler. */
sealed interface InstallEvent {
    data class OpenInstaller(val file: File) : InstallEvent
    data class OpenApp(val intent: Intent) : InstallEvent
    data class OpenUninstaller(val intent: Intent) : InstallEvent
    data object RequestInstallPermission : InstallEvent
}

/**
 * Mağazanın tek ViewModel'i:
 *  - depo listesi + `index-v1.json` indirme ve önbellek
 *  - arama & kategori filtresi
 *  - APK indirme + SHA-256 doğrulama + kurulum akışı
 *  - cihazda kurulu / güncellenebilir sürümler
 */
class CatalogViewModel(app: Application) : AndroidViewModel(app) {

    private val repoStore = RepoStore(app)
    private val catalogRepository = CatalogRepository(app)
    private val downloader = ApkDownloader(app)

    private val _state = MutableStateFlow(CatalogUiState(repos = repoStore.load()))
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private val _visible = MutableStateFlow(emptyList<AppEntry>())
    /** Arama + kategori uygulanmış görünür liste. */
    val visible: StateFlow<List<AppEntry>> = _visible.asStateFlow()

    private val _installStates = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installStates: StateFlow<Map<String, InstallState>> = _installStates.asStateFlow()

    private val _events = Channel<InstallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var refreshJob: Job? = null

    init {
        refresh(force = false)
    }

    // ------------------------------------------------------------------ katalog

    fun refresh(force: Boolean = true) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.Default) {
            val repos = repoStore.load()
            _state.update { it.copy(repos = repos, refreshing = true, loading = it.entries.isEmpty()) }

            var collected: List<AppEntry> = emptyList()
            val errors = mutableMapOf<String, String>()

            for (repo in repos) {
                try {
                    val index = catalogRepository.load(repo, force = force)
                    collected += buildEntries(repo, index)
                    publish(collected, errors, loading = false)
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    errors[repo.id] = e.message ?: "Depo okunamadı"
                    publish(collected, errors, loading = false)
                }
            }

            _state.update {
                it.copy(loading = false, refreshing = false, loadedAt = System.currentTimeMillis())
            }
        }
    }

    private fun publish(entries: List<AppEntry>, errors: Map<String, String>, loading: Boolean) {
        _state.update { state ->
            state.copy(
                entries = withInstalledVersions(entries, installedVersions(entries)),
                categories = allCategories(entries),
                repoErrors = errors.toMap(),
                loading = loading,
            )
        }
        publishVisible()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        publishVisible()
    }

    fun setCategory(category: String?) {
        _state.update { it.copy(category = category) }
        publishVisible()
    }

    private fun publishVisible() {
        val snapshot = _state.value
        _visible.value = filterEntries(snapshot.entries, snapshot.query, snapshot.category)
    }

    // ---------------------------------------------------------------- kurulum

    /** Verilen sürümü (varsayılan: en yenisi) indirir ve kurulum ekranını açar. */
    fun install(entry: AppEntry, version: IndexPackage? = null) {
        val target = version ?: entry.latest ?: return fail(entry.packageName, "İndirilecek sürüm yok")
        val url = entry.apkUrlOf(target) ?: return fail(entry.packageName, "APK adresi bulunamadı")

        val key = entry.packageName
        when (_installStates.value[key]?.phase) {
            InstallPhase.DOWNLOADING, InstallPhase.VERIFYING, InstallPhase.INSTALLING -> return
            else -> Unit
        }

        if (!Installer.canInstallPackages(getApplication())) {
            _installStates.update { it + (key to InstallState(InstallPhase.PERMISSION_NEEDED)) }
            viewModelScope.launch { _events.send(InstallEvent.RequestInstallPermission) }
            return
        }

        _installStates.update { it + (key to InstallState(InstallPhase.DOWNLOADING, 0f)) }
        viewModelScope.launch {
            try {
                val file = downloader.download(
                    url = url,
                    fileName = target.apkName ?: "$key.apk",
                    expectedSha256 = target.sha256,
                ) { progress ->
                    _installStates.update { states ->
                        states + (key to InstallState(InstallPhase.DOWNLOADING, progress.fraction))
                    }
                }
                _installStates.update { it + (key to InstallState(InstallPhase.VERIFYING, 1f)) }
                _installStates.update { it + (key to InstallState(InstallPhase.INSTALLING, 1f)) }
                _events.send(InstallEvent.OpenInstaller(file))
                _installStates.update { it + (key to InstallState(InstallPhase.DONE, 1f)) }
            } catch (c: CancellationException) {
                _installStates.update { it - key }
                throw c
            } catch (e: Exception) {
                fail(key, e.message ?: "İndirme başarısız")
            }
        }
    }

    fun dismissInstall(packageName: String) {
        _installStates.update { it - packageName }
    }

    fun launch(packageName: String) {
        val intent = Installer.launchIntent(getApplication(), packageName) ?: return
        viewModelScope.launch { _events.send(InstallEvent.OpenApp(intent)) }
    }

    fun uninstall(packageName: String) {
        viewModelScope.launch {
            _events.send(InstallEvent.OpenUninstaller(Installer.uninstallIntent(packageName)))
        }
    }

    fun requestInstallPermission() {
        viewModelScope.launch { _events.send(InstallEvent.RequestInstallPermission) }
    }

    /** Uygulama öne geldiğinde: kurulu sürümleri tazele, biten kurulumları temizle. */
    fun onAppResumed() {
        _installStates.update { map ->
            map.filterValues {
                it.phase != InstallPhase.DONE &&
                    it.phase != InstallPhase.PERMISSION_NEEDED &&
                    // Başarısız bir kurulum, kullanıcı uygulamayı açıp geri
                    // dönünce ekranda kalmasın (aksi halde Yükle/Kaldır alanı
                    // yerine "Kurulum başarısız" mesajı görünmeye devam eder).
                    it.phase != InstallPhase.FAILED
            }
        }
        viewModelScope.launch(Dispatchers.Default) {
            val entries = _state.value.entries
            if (entries.isEmpty()) return@launch
            publish(entries, _state.value.repoErrors, loading = false)
        }
    }

    private fun installedVersions(entries: List<AppEntry>): Map<String, Long> {
        val context = getApplication<Application>()
        return entries.mapNotNull { entry ->
            installedVersionCode(context, entry.packageName)?.let { entry.packageName to it }
        }.toMap()
    }

    private fun fail(packageName: String, message: String) {
        _installStates.update { it + (packageName to InstallState(InstallPhase.FAILED, -1f, message)) }
    }

    // ------------------------------------------------------------------ depolar

    /** @return başarılıysa null, değilse hata mesajı. */
    fun addRepo(name: String, address: String): String? {
        val normalized = RepoStore.normalizeAddress(address) ?: return "Geçersiz depo adresi"
        val added = repoStore.add(name.trim(), normalized)
            ?: return "Bu depo zaten eklenmiş olabilir"
        _state.update { it.copy(repos = repoStore.load()) }
        refresh(force = true)
        return null
    }

    fun removeRepo(id: String) {
        repoStore.remove(id)
        _state.update { it.copy(repos = repoStore.load()) }
        refresh(force = true)
    }

    fun clearCache() {
        catalogRepository.clearCache()
        downloader.clear()
        refresh(force = true)
    }

    fun entryOrNull(repoId: String, packageName: String): AppEntry? =
        _state.value.entries.firstOrNull { it.repo.id == repoId && it.packageName == packageName }
}

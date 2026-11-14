package dev.novastore.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Kullanıcının eklediği bir depo (repository). */
data class RepoConfig(
    val id: String,
    val name: String,
    val address: String,
) {
    companion object {
        const val DEFAULT_FDROID_ADDRESS = "https://f-droid.org/repo"

        fun idFor(address: String): String = Integer.toHexString(address.trim().hashCode())

        fun default(): RepoConfig = RepoConfig(
            id = idFor(DEFAULT_FDROID_ADDRESS),
            name = "F-Droid",
            address = DEFAULT_FDROID_ADDRESS,
        )
    }
}

/**
 * Depo listesinin kalıcı (SharedPreferences) saklayıcısı.
 * Varsayılan olarak resmi F-Droid deposu gelir; kullanıcı Ayarlar'dan
 * kendi deposunu (ör. `https://repo.ornek.com/repo`) ekleyebilir.
 */
class RepoStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("novastore_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): List<RepoConfig> {
        val raw = prefs.getString(KEY_REPOS, null) ?: return defaults()
        return runCatching {
            val type = object : TypeToken<List<RepoConfig>>() {}.type
            val parsed: List<RepoConfig> = gson.fromJson(raw, type)
            parsed.distinctBy { it.address.trimEnd('/') }.ifEmpty { defaults() }
        }.getOrDefault(defaults())
    }

    private fun defaults(): List<RepoConfig> = listOf(RepoConfig.default())

    fun save(repos: List<RepoConfig>) {
        prefs.edit().putString(KEY_REPOS, gson.toJson(repos)).apply()
    }

    /** Yeni depo ekler; adres normalize edilir, aynı adres iki kez eklenmez. */
    fun add(name: String, address: String): RepoConfig? {
        val normalized = normalizeAddress(address) ?: return null
        val repos = load().toMutableList()
        if (repos.any { it.address.equals(normalized, ignoreCase = true) }) return null
        val repo = RepoConfig(
            id = RepoConfig.idFor(normalized),
            name = name.ifBlank { hostOf(normalized) },
            address = normalized,
        )
        repos.add(repo)
        save(repos)
        return repo
    }

    fun remove(id: String) {
        save(load().filterNot { it.id == id })
    }

    fun resetToDefaults(): Unit = save(defaults())

    companion object {
        private const val KEY_REPOS = "repos_json"

        /**
         * Kullanıcının girdiği metni geçerli bir depo adresine çevirir.
         * `https://host/repo/` -> `https://host/repo`; geçersizse null.
         */
        fun normalizeAddress(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.contains("://") -> return null
                else -> "https://$trimmed"
            }
            if (!withScheme.startsWith("http://", true) && !withScheme.startsWith("https://", true)) return null
            val host = hostOf(withScheme)
            if (host.isBlank()) return null
            return withScheme
        }

        fun hostOf(address: String): String = runCatching {
            java.net.URI(address).host ?: ""
        }.getOrDefault("")

        /** Adresin bağlandığı sunucu için izin verilen cleartext (http) mi? */
        fun allowsCleartext(address: String): Boolean =
            address.startsWith("http://", ignoreCase = true)
    }
}

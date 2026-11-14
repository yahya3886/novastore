package dev.novastore.app.data

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bir depo için `index-v1.json` indirir, önbelleğe alır ve ayrıştırır.
 *
 * Notlar:
 *  - F-Droid'in resmi indeksi ~75 MB olduğundan gövde doğrudan dosyaya
 *    yazılır ve ayrıştırma bir `Reader` üzerinden akıtılır (devasa bir
 *    String oluşturulmaz).
 *  - Önbellek: `cacheDir/repos/<adres-özet>.json`. Depo `maxage` süresi
 *    içindeyse ağ çağrılmaz; `force=true` ile her zaman tazelenir.
 */
class CatalogRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    private val cacheDir: File
        get() = File(appContext.cacheDir, "repos").apply { mkdirs() }

    suspend fun load(repo: RepoConfig, force: Boolean = false): FdroidIndex =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(repo)

            if (!force && cache.exists() && cache.length() > 0) {
                val cached = parseOrNull(cache)
                if (cached != null && isFresh(cache, cached.repo?.maxAgeHours)) {
                    return@withContext cached
                }
            }

            val temp = File(cacheDir, sha1(repo.address) + ".tmp")
            try {
                download(url = join(repo.address, "index-v1.json"), target = temp)
                val index = parseOrNull(temp)
                    ?: throw IOException("Depo indeksi okunamadı (geçersiz index-v1.json)")
                if (cache.exists()) cache.delete()
                if (!temp.renameTo(cache)) {
                    temp.copyTo(cache, overwrite = true)
                    temp.delete()
                }
                index
            } finally {
                if (temp.exists()) temp.delete()
            }
        }

    /** İndeks gövdesini hedef dosyaya akıtır; gzip cevaplarını da açar. */
    private fun download(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")

            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Depo sunucusu HTTP $code döndü")

            val encoding = connection.contentEncoding.orEmpty().lowercase()
            val source = connection.inputStream.let {
                if (encoding.contains("gzip")) GZIPInputStream(it) else it
            }.buffered()

            source.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseOrNull(file: File): FdroidIndex? {
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            file.inputStream().use { stream ->
                InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                    gson.fromJson(reader, FdroidIndex::class.java)
                }
            }
        }.getOrNull()?.takeIf { it.repo != null || it.apps != null }
    }

    /** `maxage` (saat) süresi dolmadıysa önbellek tazedir. */
    private fun isFresh(cache: File, maxAgeHours: Int?): Boolean {
        val hours = (maxAgeHours ?: DEFAULT_MAX_AGE_HOURS).coerceAtLeast(1)
        val age = System.currentTimeMillis() - cache.lastModified()
        return age in 0..(hours * 3_600_000L)
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun cacheFile(repo: RepoConfig): File = File(cacheDir, sha1(repo.address) + ".json")

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun join(address: String, path: String): String =
        address.trim().trimEnd('/') + "/" + path.trimStart('/')

    companion object {
        const val USER_AGENT = "NovaStore/1.0 (F-Droid uyumlu açık kaynak mağaza)"
        private const val DEFAULT_MAX_AGE_HOURS = 24
    }
}

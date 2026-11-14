package dev.novastore.app.install

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** APK indirme sırasında ilerleme bilgisi. */
data class DownloadProgress(
    val bytes: Long,
    val totalBytes: Long,
    /** 0..1; toplam bilinmiyorsa -1. */
    val fraction: Float,
)

/**
 * APK'ları indirir, SHA-256 özetini depo indeksindeki değerle
 * karşılaştırır ve `cacheDir/apks/` altına kaydeder.
 */
class ApkDownloader(context: Context) {

    private val appContext = context.applicationContext

    private val apkDir: File
        get() = File(appContext.cacheDir, "apks").apply { mkdirs() }

    /**
     * @param url APK'ın tam adresi
     * @param fileName Hedef dosya adı (indeksteki `apkName`)
     * @param expectedSha256 Depo indeksindeki özet; null ise doğrulama atlanır
     * @param onProgress İlerleme geri çağrısı (IO dispatcher'ında çağrılır)
     */
    suspend fun download(
        url: String,
        fileName: String,
        expectedSha256: String?,
        onProgress: (DownloadProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val safeName = fileName.substringAfterLast('/').ifBlank { "app.apk" }
        val target = File(apkDir, safeName)

        if (target.exists() && target.length() > 0) {
            val localHash = sha256Of(target)
            if (expectedSha256 == null || localHash.equals(expectedSha256, ignoreCase = true)) {
                onProgress(DownloadProgress(target.length(), target.length(), 1f))
                return@withContext target
            }
            target.delete()
        }

        val partial = File(apkDir, "$safeName.part")
        partial.delete()

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val code = connection.responseCode
            if (code !in 200..299) throw IOException("İndirme başarısız: HTTP $code")

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            val encoding = connection.contentEncoding.orEmpty().lowercase()
            val input = connection.inputStream.let {
                if (encoding.contains("gzip")) GZIPInputStream(it) else it
            }.buffered()

            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            input.use { stream ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()          // iptal edilirse dur
                        val read = stream.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        onProgress(
                            DownloadProgress(
                                bytes = copied,
                                totalBytes = total,
                                fraction = if (total > 0) copied.toFloat() / total else -1f,
                            )
                        )
                    }
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!expectedSha256.isNullOrBlank() && !actualHash.equals(expectedSha256, ignoreCase = true)) {
                partial.delete()
                throw IOException("SHA-256 doğrulaması başarısız — dosya bozuk veya değiştirilmiş olabilir")
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) throw IOException("İndirilen dosya kaydedilemedi")
            onProgress(DownloadProgress(copied, copied, 1f))
            target
        } finally {
            connection.disconnect()
            if (partial.exists() && !target.exists()) partial.delete()
        }
    }

    fun clear() {
        apkDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val USER_AGENT = "NovaStore/1.0 (F-Droid uyumlu açık kaynak mağaza)"
    }
}

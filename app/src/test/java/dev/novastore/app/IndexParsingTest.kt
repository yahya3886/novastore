package dev.novastore.app

import com.google.gson.Gson
import dev.novastore.app.data.CATEGORY_ALL
import dev.novastore.app.data.FdroidIndex
import dev.novastore.app.data.RepoConfig
import dev.novastore.app.data.RepoStore
import dev.novastore.app.data.allCategories
import dev.novastore.app.data.buildEntries
import dev.novastore.app.data.filterEntries
import dev.novastore.app.data.withInstalledVersions
import dev.novastore.app.util.appDescription
import dev.novastore.app.util.appSummary
import dev.novastore.app.util.appTitle
import dev.novastore.app.util.collectScreenshots
import dev.novastore.app.util.humanSize
import dev.novastore.app.util.iconUrl
import dev.novastore.app.util.joinAddress
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-Droid `index-v1.json` şemasına ait gerçekçi bir örnek üzerinden
 * ayrıştırma, yerelleştirme, URL üretimi ve filtreleme testleri.
 */
class IndexParsingTest {

    private val gson = Gson()
    private val repo = RepoConfig(
        id = RepoConfig.idFor("https://repo.ornek.com/repo"),
        name = "Örnek Depo",
        address = "https://repo.ornek.com/repo",
    )

    private fun fixture(): String = checkNotNull(
        javaClass.classLoader?.getResource("sample-index.json")
    ) { "sample-index.json test kaynağı bulunamadı" }.readText()

    private fun index(): FdroidIndex = gson.fromJson(fixture(), FdroidIndex::class.java)

    // ------------------------------------------------------------ ayrıştırma

    @Test
    fun indeks_ayristirilir() {
        val parsed = index()

        assertEquals("NovaStore Test Deposu", parsed.repo?.name)
        assertEquals(168, parsed.repo?.maxAgeHours)
        assertEquals(2, parsed.apps?.size)
        assertEquals(2, parsed.packages?.size)
        assertEquals(2, parsed.packages?.get("com.ornek.hello")?.size)
        assertEquals(1, parsed.packages?.get("com.ornek.notlar")?.size)
    }

    @Test
    fun paket_alanlari_dogrulanir() {
        val version = index().packages!!["com.ornek.hello"]!!.first { it.versionCode == 3L }

        assertEquals("com.ornek.hello_3.apk", version.apkName)
        assertEquals(3L, version.versionCode)
        assertEquals("1.2.0", version.versionName)
        assertEquals(20992426L, version.size)
        assertNotNull(version.sha256)
        assertEquals(64, version.sha256!!.length)
        assertEquals(listOf("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS"), version.permissionNames)
        assertEquals(26, version.minSdkVersion)
        assertEquals(36, version.targetSdkVersion)
        assertEquals(listOf("arm64-v8a"), version.nativeCode)
    }

    @Test
    fun eski_hashSha256_bicimi_desteklenir() {
        val parsed = gson.fromJson(
            """{"repo":{"address":"https://x/repo"},"packages":{"a.b":[{"apkName":"a.apk","versionCode":1,
                "hashSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}}""",
            FdroidIndex::class.java,
        )
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            parsed.packages!!["a.b"]!!.single().sha256,
        )
    }

    // -------------------------------------------------------- yerelleştirme

    @Test
    fun cihaz_diline_gore_metin_secilir() {
        val parsed = index()
        val hello = parsed.apps!!.first { it.packageName == "com.ornek.hello" }
        val notlar = parsed.apps!!.first { it.packageName == "com.ornek.notlar" }

        assertEquals("Merhaba Dünya", appTitle(hello, Locale("tr", "TR")))
        assertEquals("Türkçe özet", appSummary(hello, Locale("tr", "TR")))
        assertTrue(appDescription(hello, Locale("tr", "TR")).contains("Türkçe açıklama"))

        assertEquals("Hello World", appTitle(hello, Locale("en", "US")))
        assertEquals("English summary", appSummary(hello, Locale("en", "US")))

        // localized olmayan uygulama üst düzey alanlara düşer
        assertEquals("Hızlı Notlar", appTitle(notlar, Locale("tr", "TR")))
        assertEquals("Çevrimdışı çalışan not uygulaması", appSummary(notlar, Locale("tr", "TR")))
    }

    @Test
    fun bulunamayan_dil_icin_yedek_kullanilir() {
        val hello = index().apps!!.first { it.packageName == "com.ornek.hello" }
        // Almanca localized yok → en-US'ye düşer
        assertEquals("Hello World", appTitle(hello, Locale("de", "DE")))
    }

    // ----------------------------------------------------------------- URL

    @Test
    fun ikon_urlleri_uretilir() {
        val parsed = index()
        val hello = parsed.apps!!.first { it.packageName == "com.ornek.hello" }
        val notlar = parsed.apps!!.first { it.packageName == "com.ornek.notlar" }

        // sadece dosya adı → <depo>/icons/<dosya>
        assertEquals(
            "https://repo.ornek.com/repo/icons/com.ornek.hello.3.png",
            iconUrl(repo, hello, Locale("tr", "TR")),
        )
        // göreli yol → doğrudan <depo>/<yol>
        assertEquals(
            "https://repo.ornek.com/repo/icons/com.ornek.notlar.png",
            iconUrl(repo, notlar, Locale("tr", "TR")),
        )
    }

    @Test
    fun ekran_goruntusu_urlleri_uretilir() {
        val hello = index().apps!!.first { it.packageName == "com.ornek.hello" }
        val shots = collectScreenshots(repo, hello, Locale("tr", "TR"))

        // iki dosya var; dil başına tekrar etmez
        assertEquals(2, shots.size)
        assertEquals(
            "https://repo.ornek.com/repo/com.ornek.hello/tr-TR/phoneScreenshots/1.png",
            shots.first().url,
        )
    }

    @Test
    fun adres_birlestirme_tekrarli_boluslere_dikkat_eder() {
        assertEquals("https://x/repo/index-v1.json", joinAddress("https://x/repo/", "/index-v1.json"))
        assertEquals("https://x/repo/index-v1.json", joinAddress("https://x/repo", "index-v1.json"))
    }

    @Test
    fun apk_adresi_uretilir() {
        val entry = buildEntries(repo, index(), Locale("tr", "TR")).first { it.packageName == "com.ornek.hello" }
        assertEquals("https://repo.ornek.com/repo/com.ornek.hello_3.apk", entry.apkUrlOf())
        assertEquals("https://repo.ornek.com/repo/com.ornek.hello_2.apk", entry.apkUrlOf(entry.versions[1]))
    }

    // --------------------------------------------------------- katalog modeli

    @Test
    fun surumler_azalan_sirada_ve_alanlar_dolu() {
        val entry = buildEntries(repo, index(), Locale("tr", "TR"))
            .first { it.packageName == "com.ornek.hello" }

        assertEquals(2, entry.versions.size)
        assertEquals(3L, entry.latestVersionCode)
        assertEquals("Merhaba Dünya", entry.title)
        assertEquals(listOf("Tools", "Demo"), entry.categories)
        assertEquals("GPL-3.0-or-later", entry.license)
        assertNotNull(entry.description)
        assertFalse(entry.isInstalled)
        assertFalse(entry.hasUpdate)
    }

    @Test
    fun kurulum_durumu_ve_guncelleme_hesabi() {
        val entries = buildEntries(repo, index(), Locale("tr", "TR"))
        val installed = mapOf("com.ornek.hello" to 2L, "com.ornek.notlar" to 1130103L)

        val updated = withInstalledVersions(entries, installed)
        val hello = updated.first { it.packageName == "com.ornek.hello" }
        val notlar = updated.first { it.packageName == "com.ornek.notlar" }

        assertTrue(hello.isInstalled)
        assertTrue(hello.hasUpdate)          // kurulu 2, depoda 3
        assertTrue(notlar.isInstalled)
        assertFalse(notlar.hasUpdate)        // son sürüm kurulu

        // kurulmayan paket null'a döner
        val none = withInstalledVersions(updated, emptyMap())
        none.forEach { assertNull(it.installedVersion) }
    }

    @Test
    fun kategoriler_ve_filtre_calisir() {
        val entries = buildEntries(repo, index(), Locale("tr", "TR"))

        assertEquals(listOf("Demo", "Note", "Tools"), allCategories(entries))

        assertEquals(2, filterEntries(entries, "", null).size)
        assertEquals(2, filterEntries(entries, "", CATEGORY_ALL).size)
        assertEquals(1, filterEntries(entries, "", "Tools").size)
        assertEquals(1, filterEntries(entries, "notlar", null).size)
        assertEquals(1, filterEntries(entries, "com.ornek.hello", null).size)
        assertEquals(0, filterEntries(entries, "bulunmayan", null).size)
        // kategori + arama birlikte
        assertEquals(1, filterEntries(entries, "ornek", "Note").size)
    }

    // ------------------------------------------------------------------ diğer

    @Test
    fun depo_adresi_normalize_edilir() {
        assertEquals("https://repo.ornek.com/repo", RepoStore.normalizeAddress("repo.ornek.com/repo/"))
        assertEquals("http://10.0.2.2:8080/repo", RepoStore.normalizeAddress("http://10.0.2.2:8080/repo"))
        assertEquals("https://repo.ornek.com/repo", RepoStore.normalizeAddress("  https://repo.ornek.com/repo  "))
        assertNull(RepoStore.normalizeAddress(""))
        assertNull(RepoStore.normalizeAddress("   "))
        assertNull(RepoStore.normalizeAddress("ftp://repo.ornek.com/repo"))
        assertTrue(RepoStore.allowsCleartext("http://10.0.2.2:8080/repo"))
        assertFalse(RepoStore.allowsCleartext("https://repo.ornek.com/repo"))
    }

    @Test
    fun boyutlar_okunabilir_biçimde() {
        assertEquals("1.1 MB", humanSize(1_124_774L))
        assertEquals("19.1 MB", humanSize(20_000_000L))
        assertEquals("—", humanSize(null))
        assertEquals("512 B", humanSize(512L))
    }
}

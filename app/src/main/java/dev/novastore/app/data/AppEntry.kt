package dev.novastore.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.novastore.app.util.apkUrl
import dev.novastore.app.util.appDescription
import dev.novastore.app.util.appSummary
import dev.novastore.app.util.appTitle
import dev.novastore.app.util.collectScreenshots
import dev.novastore.app.util.iconUrl
import dev.novastore.app.util.pickLocalized
import dev.novastore.app.util.Screenshot
import java.util.Locale

/**
 * Depo indeksinden türetilen, arayüzün kullandığı zengin uygulama kaydı.
 *
 * [installedVersion] cihaz bilgisinden gelir ve [buildEntries] / VM tarafından
 * yeniden üretilerek (copy) ekrana yansıtılır.
 */
data class AppEntry(
    val repo: RepoConfig,
    val packageName: String,
    val title: String,
    val summary: String,
    val description: String,
    val categories: List<String>,
    val iconUrl: String?,
    val license: String?,
    val authorName: String?,
    val webSite: String?,
    val sourceCode: String?,
    val issueTracker: String?,
    val added: Long?,
    val lastUpdated: Long?,
    /** Sürüm kodu azalan sırada: en yenisi başta. */
    val versions: List<IndexPackage>,
    val whatsNew: String?,
    /** Ekran görüntüleri (tam URL'ler) — indeks ayrıştırıldıktan sonra üretilir ki
     *  tüm dillerin metinleri bellekte tutulmasın. */
    val screenshots: List<Screenshot> = emptyList(),
    /** Cihazda kurulu sürüm kodu; kurulu değilse null. */
    val installedVersion: Long? = null,
) {
    val latest: IndexPackage? get() = versions.firstOrNull()

    val latestVersionCode: Long? get() = latest?.versionCode

    val isInstalled: Boolean get() = installedVersion != null

    /** Cihazda kurulu sürüm, depodaki en yeni sürümden eskiyse güncelleme vardır. */
    val hasUpdate: Boolean
        get() = installedVersion != null &&
            (latestVersionCode?.let { latestCode -> installedVersion!! < latestCode } == true)

    fun apkUrlOf(version: IndexPackage? = latest): String? = version?.let { apkUrl(repo, it) }

    fun versionFor(code: Long): IndexPackage? = versions.firstOrNull { it.versionCode == code }
}

/** Bir indeks + cihaz dili ile tek depodaki uygulama kayıtlarını üretir. */
fun buildEntries(
    repo: RepoConfig,
    index: FdroidIndex,
    locale: Locale = Locale.getDefault(),
): List<AppEntry> {
    val packages = index.packages.orEmpty()
    return index.apps.orEmpty().mapNotNull { app ->
        val packageName = app.packageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val versions = packages[packageName]
            .orEmpty()
            .sortedByDescending { it.versionCode ?: 0L }
        val localized = pickLocalized(app, locale)
        AppEntry(
            repo = repo,
            packageName = packageName,
            title = appTitle(app, locale),
            summary = appSummary(app, locale),
            description = appDescription(app, locale),
            categories = app.categories.orEmpty().filter { it.isNotBlank() },
            iconUrl = iconUrl(repo, app, locale),
            license = app.license,
            authorName = app.authorName,
            webSite = app.webSite,
            sourceCode = app.sourceCode,
            issueTracker = app.issueTracker,
            added = app.added,
            lastUpdated = app.lastUpdated,
            versions = versions,
            whatsNew = localized?.whatsNew
                ?: versions.firstOrNull()?.releaseNotes
                ?: versions.firstOrNull()?.whatsNew,
            screenshots = collectScreenshots(repo, app, locale),
        )
    }.sortedBy { it.title.lowercase(locale) }
}

/** Kurulu sürüm bilgisini katalog listesine işler (yeni liste döner). */
fun withInstalledVersions(
    entries: List<AppEntry>,
    installed: Map<String, Long>,
): List<AppEntry> = entries.map { entry ->
    entry.copy(installedVersion = installed[entry.packageName])
}

/**
 * Katalog listesi için saf filtreleme (arama + kategori).
 * Saf olduğu için birim testi ile doğrulanabilir.
 */
fun filterEntries(
    entries: List<AppEntry>,
    query: String,
    category: String?,
): List<AppEntry> {
    val normalizedQuery = query.trim()
    val normalizedCategory = category?.takeIf { it.isNotBlank() && it != CATEGORY_ALL }
    return entries.filter { entry ->
        val matchesCategory =
            normalizedCategory == null ||
                entry.categories.any { it.equals(normalizedCategory, ignoreCase = true) }
        val matchesQuery = normalizedQuery.isEmpty() || entry.matchesQuery(normalizedQuery)
        matchesCategory && matchesQuery
    }
}

/** Tüm katalogda geçen kategori adları (alfabetik). */
fun allCategories(entries: List<AppEntry>): List<String> =
    entries.flatMap { it.categories }.distinct().sortedBy { it.lowercase(Locale.getDefault()) }

/** Kategori seçiminin temizlendiğini gösteren değer. */
const val CATEGORY_ALL = "Tümü"

/** Cihazda kurulu sürüm kodunu okur; kurulu değilse null. */
fun installedVersionCode(context: Context, packageName: String): Long? = try {
    val pm = context.packageManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(packageName, 0).longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0).versionCode.toLong()
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private fun AppEntry.matchesQuery(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        summary.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true) ||
        authorName.orEmpty().contains(query, ignoreCase = true) ||
        categories.any { it.contains(query, ignoreCase = true) }

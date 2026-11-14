package dev.novastore.app.util

import dev.novastore.app.data.IndexApp
import dev.novastore.app.data.IndexPackage
import dev.novastore.app.data.LocalizedStrings
import dev.novastore.app.data.RepoConfig
import java.util.Locale

/** Depo adresi ile göreli yolu birleştirir: `https://x/repo` + `/a.png`. */
fun joinAddress(address: String, path: String): String =
    address.trim().trimEnd('/') + "/" + path.trim().trimStart('/')

/** Ekran görüntüsü türünün index içindeki anahtar adı. */
enum class ScreenshotKind(val key: String, val label: String) {
    PHONE("phoneScreenshots", "Telefon"),
    SEVEN_INCH("sevenInchScreenshots", "7\" tablet"),
    TEN_INCH("tenInchScreenshots", "10\" tablet"),
    TV("tvScreenshots", "TV"),
    WEAR("wearScreenshots", "Sağlık");

    companion object {
        fun fromKey(key: String): ScreenshotKind? = entries.firstOrNull { it.key == key }
    }
}

data class Screenshot(
    val kind: ScreenshotKind,
    val locale: String,
    val file: String,
    val url: String,
)

/**
 * Cihaz diline göre doğru `localized` girdisini seçer.
 * Sıra: cihaz dili -> `en-US` -> `default` -> ilk girdi.
 */
fun pickLocalized(app: IndexApp, deviceLocale: Locale = Locale.getDefault()): LocalizedStrings? {
    val map = app.localized ?: return null
    if (map.isEmpty()) return null
    val languageTag = deviceLocale.toLanguageTag()          // örn. tr-TR
    val language = deviceLocale.language                     // örn. tr
    return map[languageTag]
        ?: map.entries.firstOrNull { it.key.equals(language, ignoreCase = true) }?.value
        ?: map["en-US"]
        ?: map["default"]
        ?: map.values.firstOrNull()
}

/** Uygulamanın görünen adı (önce dil, sonra üst düzey alan). */
fun appTitle(app: IndexApp, deviceLocale: Locale = Locale.getDefault()): String =
    pickLocalized(app, deviceLocale)?.name?.takeIf { it.isNotBlank() }
        ?: app.name?.takeIf { it.isNotBlank() }
        ?: app.packageName.orEmpty()

/** Uygulamanın kısa özeti. */
fun appSummary(app: IndexApp, deviceLocale: Locale = Locale.getDefault()): String =
    pickLocalized(app, deviceLocale)?.summary?.takeIf { it.isNotBlank() }
        ?: app.summary.orEmpty()

/** Uygulamanın uzun açıklaması (HTML içerebilir). */
fun appDescription(app: IndexApp, deviceLocale: Locale = Locale.getDefault()): String =
    pickLocalized(app, deviceLocale)?.description?.takeIf { it.isNotBlank() }
        ?: app.description.orEmpty()

/** Deponun ikon dosyasının tam URL'i. */
fun iconUrl(repo: RepoConfig, app: IndexApp, deviceLocale: Locale = Locale.getDefault()): String? {
    // Yerelleştirilmiş ikon: <depo>/<paket>/<locale>/<dosya>
    val localizedIcon = pickLocalized(app, deviceLocale)?.icon?.takeIf { it.isNotBlank() }
    if (localizedIcon != null) {
        return if (localizedIcon.contains("/")) {
            joinAddress(repo.address, localizedIcon)
        } else {
            joinAddress(repo.address, "${app.packageName.orEmpty()}/${deviceLocale.toLanguageTag()}/$localizedIcon")
        }
    }
    val icon = app.icon?.takeIf { it.isNotBlank() } ?: return null
    // Genel ikon: ya göreli yol ya da sadece dosya adı (<depo>/icons/<dosya>).
    return if (icon.contains("/")) joinAddress(repo.address, icon) else joinAddress(repo.address, "icons/$icon")
}

/** Ekran görüntüsünün tam URL'i. */
fun screenshotUrl(
    repo: RepoConfig,
    packageName: String,
    shot: Screenshot,
): String = if (shot.file.contains("/")) {
    joinAddress(repo.address, shot.file)
} else {
    joinAddress(repo.address, "$packageName/${shot.locale}/${shot.kind.key}/${shot.file}")
}

/** Uygulamaya ait tüm ekran görüntülerini (mümkün olan dillerde) toplar. */
fun collectScreenshots(
    repo: RepoConfig,
    app: IndexApp,
    deviceLocale: Locale = Locale.getDefault(),
): List<Screenshot> {
    val map = app.localized ?: return emptyList()
    val locales = listOfNotNull(deviceLocale.toLanguageTag(), "en-US", "default") +
        map.keys.toList()
    val result = LinkedHashMap<String, Screenshot>()
    for (locale in locales) {
        val entry = map[locale] ?: continue
        for (kind in ScreenshotKind.entries) {
            val files = when (kind) {
                ScreenshotKind.PHONE -> entry.phoneScreenshots
                ScreenshotKind.SEVEN_INCH -> entry.sevenInchScreenshots
                ScreenshotKind.TEN_INCH -> entry.tenInchScreenshots
                ScreenshotKind.TV -> entry.tvScreenshots
                ScreenshotKind.WEAR -> entry.wearScreenshots
            }.orEmpty()
            for (file in files) {
                if (file.isBlank()) continue
                val key = "${kind.key}|$file"
                if (result.containsKey(key)) continue
                val shot = Screenshot(kind, locale, file, "")
                result[key] = shot.copy(url = screenshotUrl(repo, app.packageName.orEmpty(), shot))
            }
        }
    }
    return result.values.toList()
}

/** APK indirme adresi. */
fun apkUrl(repo: RepoConfig, version: IndexPackage): String? {
    val apkName = version.apkName?.takeIf { it.isNotBlank() } ?: return null
    return joinAddress(repo.address, apkName)
}

/** `34` -> `34.0 MB` gibi insani boyutlar. */
fun humanSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

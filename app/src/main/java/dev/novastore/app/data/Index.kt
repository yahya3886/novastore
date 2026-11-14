package dev.novastore.app.data

import com.google.gson.annotations.SerializedName

/**
 * F-Droid uyumlu `index-v1.json` şemasının kök nesnesi.
 *
 * DİKKAT: Gson bu sınıfları Java Unsafe ile örneklediği için tüm alanlar nullable'tır;
 * eksik JSON anahtarları `null` gelir, Kotlin varsayılanları uygulanmaz.
 */
data class FdroidIndex(
    @SerializedName("repo") val repo: RepoInfo? = null,
    @SerializedName("apps") val apps: List<IndexApp>? = null,
    @SerializedName("packages") val packages: Map<String, List<IndexPackage>>? = null,
)

/** `index-v1.json -> repo` bloğu. */
data class RepoInfo(
    @SerializedName("name") val name: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("webSite") val webSite: String? = null,
    @SerializedName("webBaseUrl") val webBaseUrl: String? = null,
    @SerializedName("publicKey") val publicKey: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    /** İndeksin en fazla kaç saat taze sayılacağı (F-Droid: `maxage`, saat). */
    @SerializedName("maxage") val maxAgeHours: Int? = null,
)

/** `index-v1.json -> apps[]` girdisi. */
data class IndexApp(
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("categories") val categories: List<String>? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("sourceCode") val sourceCode: String? = null,
    @SerializedName("webSite") val webSite: String? = null,
    @SerializedName("authorName") val authorName: String? = null,
    @SerializedName("issueTracker") val issueTracker: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("featureGraphic") val featureGraphic: String? = null,
    @SerializedName("added") val added: Long? = null,
    @SerializedName("lastUpdated") val lastUpdated: Long? = null,
    @SerializedName("suggestedVersionCode") val suggestedVersionCode: String? = null,
    @SerializedName("suggestedVersionName") val suggestedVersionName: String? = null,
    @SerializedName("localized") val localized: Map<String, LocalizedStrings>? = null,
)

/**
 * Dil bazlı metinler (`apps[].localized.<locale>`).
 * Ekran görüntüleri dosya adı olarak tutulur; tam yol
 * `<depo>/<paket>/<locale>/<ekranGörüntüsüTürü>/<dosya>` kuralıyla üretilir.
 */
data class LocalizedStrings(
    @SerializedName("name") val name: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("whatsNew") val whatsNew: String? = null,
    @SerializedName("phoneScreenshots") val phoneScreenshots: List<String>? = null,
    @SerializedName("sevenInchScreenshots") val sevenInchScreenshots: List<String>? = null,
    @SerializedName("tenInchScreenshots") val tenInchScreenshots: List<String>? = null,
    @SerializedName("tvScreenshots") val tvScreenshots: List<String>? = null,
    @SerializedName("wearScreenshots") val wearScreenshots: List<String>? = null,
)

/** `index-v1.json -> packages.<paket>[]` girdisi: bir APK sürümü. */
data class IndexPackage(
    @SerializedName("packageName") val packageName: String? = null,
    @SerializedName("apkName") val apkName: String? = null,
    @SerializedName("versionCode") val versionCode: Long? = null,
    @SerializedName("versionName") val versionName: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("hash") val hash: String? = null,
    @SerializedName("hashType") val hashType: String? = null,
    @SerializedName("hashSha256") val hashSha256: String? = null,
    @SerializedName("added") val added: Long? = null,
    @SerializedName("minSdkVersion") val minSdkVersion: Int? = null,
    @SerializedName("targetSdkVersion") val targetSdkVersion: Int? = null,
    @SerializedName("features") val features: List<String>? = null,
    @SerializedName("nativecode") val nativeCode: List<String>? = null,
    @SerializedName("uses-permission") val usesPermission: List<List<String?>>? = null,
    @SerializedName("releaseNotes") val releaseNotes: String? = null,
    @SerializedName("whatsNew") val whatsNew: String? = null,
    @SerializedName("signer") val signer: String? = null,
) {
    /** SHA-256 özet; hem `hash`+`hashType` hem eski `hashSha256` biçimini destekler. */
    val sha256: String?
        get() = when {
            !hashSha256.isNullOrBlank() -> hashSha256
            hash.isNullOrBlank() -> null
            hashType.isNullOrBlank() || hashType.equals("sha256", ignoreCase = true) -> hash
            hash.length == 64 -> hash
            else -> null
        }

    /** `uses-permission: [["android.permission.X", null]]` listesinin sadece ad kısmı. */
    val permissionNames: List<String>
        get() = usesPermission.orEmpty().mapNotNull { it.firstOrNull() }.filter { it.isNotBlank() }
}

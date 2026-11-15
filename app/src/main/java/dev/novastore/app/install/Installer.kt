package dev.novastore.app.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.novastore.app.BuildConfig
import java.io.File

/**
 * APK kurulumu, açma ve kaldırma işlemlerini yürüten yardımcı.
 *
 * NovaStore bilinmeyen kaynaklardan APK kurabilmek için
 * `REQUEST_INSTALL_PACKAGES` iznini manifest'e yazar; kullanıcı yine de
 * "Bilinmeyen uygulamalara izin ver" ekranından onay vermelidir.
 */
object Installer {

    private val AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

    /** Paket cihazda kuruluysa sürüm kodunu döndürür. */
    fun installedVersion(context: Context, packageName: String): Long? = try {
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

    fun isInstalled(context: Context, packageName: String): Boolean =
        installedVersion(context, packageName) != null

    /** Paketin kurulup açılabileceği niyeti (yoksa null). */
    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Paketi kaldırmak için sistem niyeti.
     *
     * `ACTION_DELETE` tüm Android sürümlerinde paket kurulumucusu tarafından
     * ele alınır. Bazı ROM'larda yalnızca `ACTION_UNINSTALL_PACKAGE` çalıştığı
     * için [uninstallIntentAlt] ile yedeklenir (bkz. [NovaStoreRoot]).
     */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Bazı cihazlarda gereken yedek kaldırma niyeti. */
    fun uninstallIntentAlt(packageName: String): Intent =
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Son çare: uygulamanın sistem bilgi sayfası.
     *
     * Kaldırma ekranı hiç açılmıyorsa NovaStore burayı açar; sayfadaki
     * "Kaldır" düğmesi her koşulda çalışır.
     */
    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** İndirilen APK dosyasını sistem kurulum ekranına gönderir. */
    fun installIntent(context: Context, apkFile: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(context, AUTHORITY, apkFile),
                "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** Bu uygulamanın APK kurabilmek için izni var mı? */
    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Kullanıcıyı "Bilinmeyen uygulamalara izin ver" ekranına götürür. */
    fun requestInstallPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}

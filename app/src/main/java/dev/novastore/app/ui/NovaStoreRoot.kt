package dev.novastore.app.ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.novastore.app.R
import dev.novastore.app.data.AppEntry
import dev.novastore.app.install.Installer
import dev.novastore.app.ui.detail.AppDetailScreen
import dev.novastore.app.ui.home.HomeScreen
import dev.novastore.app.ui.installed.UpdatesScreen
import dev.novastore.app.ui.settings.SettingsScreen
import dev.novastore.app.viewmodel.CatalogViewModel
import dev.novastore.app.viewmodel.InstallEvent

private data class Destination(val route: String, val labelRes: Int, val icon: ImageVector)

/**
 * Sistem kaldırma ekranının açılması için beklenecek süre.
 *
 * Bu süre sonunda NovaStore hâlâ önündeyse niyetin açılmadığı (ya da anında
 * kapandığı) kabul edilir ve uygulama bilgi sayfasına düşülür.
 */
private const val SYSTEM_SCREEN_TIMEOUT_MS = 1_500L

/** Bu cihazda sistem kaldırma ekranının açılıp açılmadığının hatırlanması. */
private const val UNINSTALL_PREFS = "novastore_uninstall"
private const val KEY_SYSTEM_UNINSTALL = "system_uninstall"
private const val STATE_WORKS = "works"
private const val STATE_BROKEN = "broken"

private val destinations = listOf(
    Destination("home", R.string.nav_store, Icons.Outlined.Storefront),
    Destination("updates", R.string.nav_updates, Icons.AutoMirrored.Outlined.List),
    Destination("settings", R.string.nav_settings, Icons.Outlined.Tune),
)

/** NovaStore kök ekranı: alt gezinme + NavHost + kurulum niyetlerinin yürütülmesi. */
@Composable
fun NovaStoreRoot(
    viewModel: CatalogViewModel = viewModel(),
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    // Configuration değişikliklerine duyarlı (lint: LocalContextGetResourceValueCall)
    val uninstallFailedMessage = stringResource(R.string.uninstall_failed)
    val uninstallFallbackToast = stringResource(R.string.uninstall_fallback)

    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(UNINSTALL_PREFS, Context.MODE_PRIVATE) }

    /** Uygulamanın sistem bilgi sayfası: oradaki "Kaldır" her koşulda çalışır. */
    val openAppDetails: (String) -> Unit = { pkg ->
        Toast.makeText(context, uninstallFallbackToast, Toast.LENGTH_LONG).show()
        val ok = runCatching { context.startActivity(Installer.appDetailsIntent(pkg)) }.isSuccess
        if (!ok) {
            scope.launch { snackbarHostState.showSnackbar(uninstallFailedMessage) }
        }
    }

    /**
     * Kaldırma akışı — ViewModel/olay kanalına uğramaz, düğmeye basıldığı
     * anda çalışır.
     *
     * 1. Sistem kaldırma ekranı denenir (`ACTION_DELETE`, olmazsa
     *    `ACTION_UNINSTALL_PACKAGE`).
     * 2. Niyet hiç açılamazsa ya da açılır gibi olup ekran gelmezse
     *    (bazı ROM/AOSP yapılarında paket kurulumucusu niyeti alıp kendini
     *    kapatır) NovaStore bunu cihazda hatırlar ve sonraki işlemlerde
     *    doğrudan uygulama bilgi sayfasını açar — oradaki "Kaldır" her koşulda
     *    çalışır.
     */
    val openUninstaller: (AppEntry) -> Unit = { entry ->
        val packageName = entry.packageName
        val systemState = prefs.getString(KEY_SYSTEM_UNINSTALL, null)

        if (systemState == STATE_BROKEN) {
            // Bu cihazda sistem ekranı biliniyor ki açılmıyor: bekleme.
            openAppDetails(packageName)
        } else if (systemState == STATE_WORKS) {
            val ok = runCatching { context.startActivity(Installer.uninstallIntent(packageName)) }.isSuccess
            if (!ok) {
                prefs.edit().putString(KEY_SYSTEM_UNINSTALL, STATE_BROKEN).apply()
                openAppDetails(packageName)
            }
        } else {
            val opened = runCatching { context.startActivity(Installer.uninstallIntent(packageName)) }.isSuccess ||
                runCatching { context.startActivity(Installer.uninstallIntentAlt(packageName)) }.isSuccess

            if (!opened) {
                prefs.edit().putString(KEY_SYSTEM_UNINSTALL, STATE_BROKEN).apply()
                openAppDetails(packageName)
            } else {
                scope.launch {
                    delay(SYSTEM_SCREEN_TIMEOUT_MS)
                    // Hâlâ önündeysek sistem kaldırma ekranı açılmamış demektir.
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        prefs.edit().putString(KEY_SYSTEM_UNINSTALL, STATE_BROKEN).apply()
                        openAppDetails(packageName)
                    } else {
                        prefs.edit().putString(KEY_SYSTEM_UNINSTALL, STATE_WORKS).apply()
                    }
                }
            }
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val installStates by viewModel.installStates.collectAsStateWithLifecycle()

    // Kurulum / açma / kaldırma niyetlerini yürüt
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InstallEvent.OpenInstaller ->
                    runCatching { context.startActivity(Installer.installIntent(context, event.file)) }
                is InstallEvent.OpenApp ->
                    runCatching { context.startActivity(event.intent) }
                InstallEvent.RequestInstallPermission ->
                    runCatching { context.startActivity(Installer.requestInstallPermissionIntent(context)) }
            }
        }
    }

    // Uygulama öne geldiğinde kurulu sürümleri tazele
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || !currentRoute.startsWith("app/")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = null)
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    state = state,
                    entries = visible,
                    installStates = installStates,
                    onQuery = viewModel::setQuery,
                    onCategory = viewModel::setCategory,
                    onRefresh = { viewModel.refresh(force = true) },
                    onRetry = { viewModel.refresh(force = true) },
                    onOpen = { entry -> openDetail(navController::navigate, entry) },
                    onInstall = { viewModel.install(it) },
                    onLaunch = { viewModel.launch(it.packageName) },
                )
            }

            composable("updates") {
                UpdatesScreen(
                    state = state,
                    installStates = installStates,
                    onOpen = { entry -> openDetail(navController::navigate, entry) },
                    onInstall = { viewModel.install(it) },
                    onLaunch = { viewModel.launch(it.packageName) },
                    onUninstall = openUninstaller,
                    onGoToStore = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable("settings") {
                SettingsScreen(
                    state = state,
                    onAddRepo = viewModel::addRepo,
                    onRemoveRepo = viewModel::removeRepo,
                    onClearCache = viewModel::clearCache,
                )
            }

            composable(
                route = "app/{repoId}/{packageName}",
                arguments = listOf(
                    navArgument("repoId") { type = NavType.StringType },
                    navArgument("packageName") { type = NavType.StringType },
                ),
            ) { entry ->
                val repoId = entry.arguments?.getString("repoId").orEmpty()
                val packageName = entry.arguments?.getString("packageName").orEmpty()
                AppDetailScreen(
                    repoId = repoId,
                    packageName = packageName,
                    state = state,
                    installStates = installStates,
                    onBack = { navController.popBackStack() },
                    onRefresh = { viewModel.refresh(force = true) },
                    onInstall = { app, version -> viewModel.install(app, version) },
                    onLaunch = { viewModel.launch(it.packageName) },
                    onUninstall = openUninstaller,
                )
            }
        }
    }
}

private fun openDetail(navigate: (String) -> Unit, entry: AppEntry) {
    navigate("app/${entry.repo.id}/${entry.packageName}")
}

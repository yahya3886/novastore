package dev.novastore.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.novastore.app.R
import dev.novastore.app.data.AppEntry
import dev.novastore.app.viewmodel.CatalogUiState
import dev.novastore.app.data.IndexPackage
import dev.novastore.app.viewmodel.InstallState
import dev.novastore.app.ui.components.AppIcon
import dev.novastore.app.ui.components.InfoRow
import dev.novastore.app.ui.components.InstallActions
import dev.novastore.app.ui.components.LoadingState
import dev.novastore.app.ui.components.SectionTitle
import dev.novastore.app.ui.components.StatusBadge
import dev.novastore.app.ui.components.StoreCard
import dev.novastore.app.ui.components.repoDisplayName
import dev.novastore.app.util.Screenshot
import dev.novastore.app.util.humanSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uygulama detay ekranı: açıklama, ekran görüntüleri, izinler, sürüm geçmişi. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)
@Composable
fun AppDetailScreen(
    repoId: String,
    packageName: String,
    state: CatalogUiState,
    installStates: Map<String, InstallState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onInstall: (AppEntry, IndexPackage?) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
) {
    val entry = state.entries.firstOrNull { it.repo.id == repoId && it.packageName == packageName }
    val context = LocalContext.current

    /** Tıklanan ekran görüntüsünün tam ekran önizlemesi. */
    var preview by remember { mutableStateOf<Screenshot?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = entry?.repo?.let { repoDisplayName(it) } ?: stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        if (entry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) {
                    LoadingState()
                } else {
                    Text(
                        text = stringResource(R.string.empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
            return@Scaffold
        }

        val repoError = state.repoErrors[entry.repo.id]

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                DetailHeader(
                    entry = entry,
                    install = installStates[entry.packageName],
                    onInstall = { onInstall(entry, null) },
                    onLaunch = { onLaunch(entry) },
                    onUninstall = { onUninstall(entry) },
                )
            }

            if (repoError != null) {
                item(key = "repoError") {
                    Text(
                        text = stringResource(R.string.repo_error_fmt, repoDisplayName(entry.repo), repoError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item(key = "whatsNew") {
                val whatsNew = entry.whatsNew?.takeIf { it.isNotBlank() }
                if (whatsNew != null) {
                    StoreCard {
                        Column(Modifier.padding(14.dp)) {
                            SectionTitle(stringResource(R.string.whats_new))
                            Text(
                                text = AnnotatedString.fromHtml(whatsNew).text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            item(key = "description") {
                StoreCard {
                    Column(Modifier.padding(14.dp)) {
                        SectionTitle(stringResource(R.string.label_description))
                        Text(
                            text = if (entry.description.isBlank()) {
                                stringResource(R.string.no_description)
                            } else {
                                AnnotatedString.fromHtml(entry.description).text
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (entry.categories.isNotEmpty()) {
                item(key = "categories") {
                    StoreCard {
                        Column(Modifier.padding(14.dp)) {
                            SectionTitle(stringResource(R.string.label_categories))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(entry.categories) { category ->
                                    ChipLabel(category)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "screenshots") {
                val screenshots = remember(entry.packageName, entry.lastUpdated) {
                    dev.novastore.app.util.collectScreenshots(entry.repo, entry.app)
                }
                if (screenshots.isNotEmpty()) {
                    Column {
                        SectionTitle(stringResource(R.string.label_screenshots))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(screenshots, key = { it.url }) { shot ->
                                AsyncImage(
                                    model = shot.url,
                                    contentDescription = stringResource(R.string.screenshot_desc, entry.title),
                                    modifier = Modifier
                                        .size(width = 148.dp, height = 264.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { preview = shot },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "info") {
                StoreCard {
                    Column(Modifier.padding(14.dp)) {
                        val latest = entry.latest
                        InfoRow(stringResource(R.string.label_version), latest?.versionName ?: "—")
                        InfoRow(stringResource(R.string.label_version_code), latest?.versionCode?.toString() ?: "—")
                        InfoRow(stringResource(R.string.label_size), humanSize(latest?.size))
                        latest?.minSdkVersion?.let { InfoRow(stringResource(R.string.label_min_sdk), "Android $it") }
                        latest?.targetSdkVersion?.let { InfoRow(stringResource(R.string.label_target_sdk), "Android $it") }
                        entry.license?.takeIf { it.isNotBlank() }
                            ?.let { InfoRow(stringResource(R.string.label_license), it) }
                        InfoRow(stringResource(R.string.label_package), entry.packageName)
                        entry.authorName?.takeIf { it.isNotBlank() }
                            ?.let { InfoRow(stringResource(R.string.label_author), it) }
                        entry.added?.let {
                            InfoRow(stringResource(R.string.label_added), formatDate(it))
                        }
                        entry.lastUpdated?.let {
                            InfoRow(stringResource(R.string.label_updated), formatDate(it))
                        }
                    }
                }
            }

            item(key = "permissions") {
                val permissions = entry.latest?.permissionNames.orEmpty()
                StoreCard {
                    Column(Modifier.padding(14.dp)) {
                        SectionTitle(stringResource(R.string.label_permissions))
                        if (permissions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.permissions_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(permissions) { permission ->
                                    ChipLabel(permission.substringAfterLast('.'))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "links") {
                val links = listOfNotNull(
                    entry.sourceCode?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.label_source) to it },
                    entry.webSite?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.label_website) to it },
                    entry.issueTracker?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.label_issues) to it },
                )
                if (links.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        links.forEach { (label, url) ->
                            OutlinedButton(onClick = { openUrl(context, url) }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item(key = "versionsTitle") {
                SectionTitle(stringResource(R.string.versions_title))
            }

            if (entry.versions.isEmpty()) {
                item(key = "noVersions") {
                    Text(
                        text = stringResource(R.string.versions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(entry.versions.size, key = { "v:${entry.versions[it].versionCode ?: it}" }) { index ->
                    VersionRow(
                        entry = entry,
                        version = entry.versions[index],
                        install = installStates[entry.packageName],
                        isLatest = index == 0,
                        onInstall = { onInstall(entry, entry.versions[index]) },
                    )
                }
            }
        }
    }

    preview?.let { shot ->
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = {
                TextButton(onClick = { preview = null }) { Text(stringResource(R.string.close)) }
            },
            text = {
                AsyncImage(
                    model = shot.url,
                    contentDescription = stringResource(R.string.screenshot_desc, entry?.title.orEmpty()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
            },
        )
    }
}

@Composable
private fun DetailHeader(
    entry: AppEntry,
    install: InstallState?,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
) {
    StoreCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(
                    url = entry.iconUrl,
                    size = 72.dp,
                    contentDescription = entry.title,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.authorName?.takeIf { it.isNotBlank() }?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    StatusRow(entry)
                }
            }

            entry.summary.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            InstallActions(
                entry = entry,
                install = install,
                onInstall = onInstall,
                onLaunch = onLaunch,
                onUninstall = onUninstall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusRow(entry: AppEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            entry.hasUpdate -> StatusBadge(
                text = stringResource(R.string.update_available),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            entry.isInstalled -> StatusBadge(
                text = stringResource(R.string.installed),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            else -> StatusBadge(
                text = stringResource(R.string.not_installed_label),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.installedVersion != null) {
            Text(
                text = "v${entry.installedVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionRow(
    entry: AppEntry,
    version: IndexPackage,
    install: InstallState?,
    isLatest: Boolean,
    onInstall: () -> Unit,
) {
    val busy = install != null && isLatest
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "v${version.versionName ?: "?"} · ${version.versionCode ?: "?"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = listOfNotNull(
                        humanSize(version.size),
                        version.added?.let { formatDate(it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                version.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (busy && isLatest) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onInstall) {
                    Text(
                        text = stringResource(
                            if (entry.hasUpdate && isLatest) R.string.install_update else R.string.install,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

private fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

private fun openUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url),
    )
    runCatching { context.startActivity(intent) }
}

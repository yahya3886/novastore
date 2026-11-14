package dev.novastore.app.ui.installed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.novastore.app.R
import dev.novastore.app.data.AppEntry
import dev.novastore.app.viewmodel.CatalogUiState
import dev.novastore.app.viewmodel.InstallState
import dev.novastore.app.ui.components.AppIcon
import dev.novastore.app.ui.components.EmptyState
import dev.novastore.app.ui.components.InstallActions
import dev.novastore.app.ui.components.LoadingState
import dev.novastore.app.ui.components.SectionTitle
import dev.novastore.app.ui.components.StatusBadge
import dev.novastore.app.ui.components.StoreCard

/** Güncellemeler + kurulu uygulamalar ekranı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    state: CatalogUiState,
    installStates: Map<String, InstallState>,
    onOpen: (AppEntry) -> Unit,
    onInstall: (AppEntry) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onGoToStore: () -> Unit,
) {
    val updates = state.entries.filter { it.hasUpdate }
    val installed = state.entries.filter { it.isInstalled && !it.hasUpdate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_updates)) },
                actions = {
                    if (updates.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.update_count, updates.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading && state.entries.isEmpty()) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "updatesTitle") {
                if (updates.isNotEmpty()) {
                    SectionTitle(
                        stringResource(R.string.section_count_fmt, stringResource(R.string.updates_title), updates.size)
                    )
                }
            }

            if (updates.isEmpty()) {
                item(key = "updatesEmpty") {
                    StoreCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.updates_none),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = stringResource(R.string.updates_none_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(updates, key = { "u:${it.repo.id}:${it.packageName}" }) { entry ->
                    InstalledRow(
                        entry = entry,
                        install = installStates[entry.packageName],
                        onClick = { onOpen(entry) },
                        onInstall = { onInstall(entry) },
                        onLaunch = { onLaunch(entry) },
                        onUninstall = { onUninstall(entry) },
                    )
                }
            }

            item(key = "installedTitle") {
                SectionTitle(
                    stringResource(R.string.section_count_fmt, stringResource(R.string.installed_title), installed.size)
                )
            }

            if (installed.isEmpty()) {
                item(key = "installedEmpty") {
                    Column {
                        Text(
                            text = stringResource(R.string.installed_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onGoToStore) {
                            Text(stringResource(R.string.nav_store))
                        }
                    }
                }
            } else {
                items(installed, key = { "i:${it.repo.id}:${it.packageName}" }) { entry ->
                    InstalledRow(
                        entry = entry,
                        install = installStates[entry.packageName],
                        onClick = { onOpen(entry) },
                        onInstall = { onInstall(entry) },
                        onLaunch = { onLaunch(entry) },
                        onUninstall = { onUninstall(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledRow(
    entry: AppEntry,
    install: InstallState?,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
) {
    StoreCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(url = entry.iconUrl, size = 44.dp, contentDescription = entry.title)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val versions = buildString {
                    entry.installedVersion?.let { append("kurulu $it") }
                    entry.latestVersionCode?.let {
                        if (isNotEmpty()) append(" → ")
                        append("depoda $it")
                    }
                }
                Text(
                    text = versions,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.hasUpdate) {
                    StatusBadge(
                        text = stringResource(R.string.update_available),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                InstallActions(
                    entry = entry,
                    install = install,
                    onInstall = onInstall,
                    onLaunch = onLaunch,
                    onUninstall = onUninstall,
                    expanded = false,
                )
            }
        }
    }
}

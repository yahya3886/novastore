package dev.novastore.app.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.novastore.app.R
import dev.novastore.app.data.AppEntry
import dev.novastore.app.data.CATEGORY_ALL
import dev.novastore.app.viewmodel.CatalogUiState
import dev.novastore.app.viewmodel.InstallPhase
import dev.novastore.app.viewmodel.InstallState
import dev.novastore.app.ui.components.AppIcon
import dev.novastore.app.ui.components.EmptyState
import dev.novastore.app.ui.components.ErrorState
import dev.novastore.app.ui.components.InstallActions
import dev.novastore.app.ui.components.LoadingState
import dev.novastore.app.ui.components.RepoWarning
import dev.novastore.app.ui.components.StatusBadge
import dev.novastore.app.ui.components.StoreCard
import dev.novastore.app.util.humanSize

/** Mağaza ana ekranı: arama + kategori + uygulama vitrini. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: CatalogUiState,
    entries: List<AppEntry>,
    installStates: Map<String, InstallState>,
    onQuery: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (AppEntry) -> Unit,
    onInstall: (AppEntry) -> Unit,
    onLaunch: (AppEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQuery = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        CategoryRow(
            categories = state.categories,
            selected = state.category,
            onSelect = onCategory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )

        state.repoErrors.values.firstOrNull()?.let { message ->
            RepoWarning(
                message = stringResource(R.string.error_generic) + ": " + message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && entries.isEmpty() -> LoadingState(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = onRetry,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                entries.isEmpty() && state.query.isBlank() -> EmptyState(
                    title = stringResource(R.string.catalog_empty_title),
                    body = stringResource(R.string.catalog_empty_body),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                entries.isEmpty() -> EmptyState(
                    title = stringResource(R.string.empty_title),
                    body = stringResource(R.string.empty_body),
                    icon = Icons.Outlined.SearchOff,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 158.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries, key = { "${it.repo.id}:${it.packageName}" }) { entry ->
                        AppGridCard(
                            entry = entry,
                            install = installStates[entry.packageName],
                            onClick = { onOpen(entry) },
                            onInstall = { onInstall(entry) },
                            onLaunch = { onLaunch(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_hint))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun CategoryRow(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(CATEGORY_ALL) + categories
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { category ->
            val isSelected = if (category == CATEGORY_ALL) selected == null else selected == category
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (category == CATEGORY_ALL) null else category) },
                label = {
                    Text(
                        text = category,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Vitrin kartı: ikon + ad + özet + kurulum durumu. */
@Composable
private fun AppGridCard(
    entry: AppEntry,
    install: InstallState?,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StoreCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(
                    url = entry.iconUrl,
                    size = 44.dp,
                    contentDescription = entry.title,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    entry.latest?.versionName?.let { version ->
                        Text(
                            text = "v$version · ${humanSize(entry.latest?.size)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = entry.summary.ifBlank { stringResource(R.string.no_description) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(48.dp),
            )

            Spacer(Modifier.height(8.dp))

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
                else -> Text(
                    text = entry.categories.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (install != null && install.phase == InstallPhase.DOWNLOADING) {
                val fraction = install.progress.coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                InstallActions(
                    entry = entry,
                    install = install,
                    onInstall = onInstall,
                    onLaunch = onLaunch,
                    modifier = Modifier.fillMaxWidth(),
                    expanded = false,
                )
            }
        }
    }
}

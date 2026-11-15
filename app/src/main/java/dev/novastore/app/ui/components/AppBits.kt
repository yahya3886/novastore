package dev.novastore.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.novastore.app.R
import dev.novastore.app.data.AppEntry
import dev.novastore.app.data.RepoConfig
import dev.novastore.app.viewmodel.InstallPhase
import dev.novastore.app.viewmodel.InstallState
import dev.novastore.app.util.ScreenshotKind

/** Depo ikonu; yoksa/olmazsa yer tutucu gösterir. */
@Composable
fun AppIcon(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val fallback = painterResource(R.drawable.ic_app_placeholder)
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size.value * 0.24f)),
    )
}

/** Yuvarlak köşeli vitrin kartı iskeleti. */
@Composable
fun StoreCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val clickable = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickable),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        content()
    }
}

/** Bilgi satırı (detay ekranı): etiket + değer. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Bölüm başlığı. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

/** Yükleniyor durumu. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = stringResource(R.string.loading)) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Hata durumu + tekrar dene. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.error_title),
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

/** Boş durum. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_empty_box),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Çevrimdışı/depoya ulaşılamadı rozeti. */
@Composable
fun RepoWarning(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Küçük durum rozeti (Kurulu / Güncelleme var / vb.). */
@Composable
fun StatusBadge(text: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) {
    Surface(color = containerColor, shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Yatay kayan künye (kategori, izin…). */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Ortak kurulum/açma/kaldırma düğme kümesi.
 *  - İndirme sürüyorsa ilerleme çubuğu
 *  - Kurulu değilse "Yükle", güncelleme varsa "Güncelle", kuruluysa "Aç"
 */
@Composable
fun InstallActions(
    entry: AppEntry,
    install: InstallState?,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
) {
    val state = install
    when {
        state == null -> IdleActions(entry, onInstall, onLaunch, onUninstall, modifier, expanded)
        else -> when (state.phase) {
            InstallPhase.DOWNLOADING -> DownloadProgressRow(state, modifier)
            InstallPhase.VERIFYING -> WorkingRow(stringResource(R.string.install_verifying), modifier)
            InstallPhase.INSTALLING -> WorkingRow(stringResource(R.string.install_launching), modifier)
            InstallPhase.PERMISSION_NEEDED -> Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.install_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onInstall) { Text(stringResource(R.string.install_permission)) }
            }
            InstallPhase.FAILED -> Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.install_failed,
                        state.message ?: stringResource(R.string.error_generic),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onInstall) { Text(stringResource(R.string.retry)) }
                    // Kurulum başarısız olsa bile kullanıcı uygulamayı
                    // kaldırmak isteyebilir; butonu geri getir.
                    if (entry.isInstalled && onUninstall != null) {
                        UninstallButton(
                            appTitle = entry.title,
                            onConfirm = onUninstall,
                            outlined = false,
                        )
                    }
                }
            }
            InstallPhase.DONE -> IdleActions(entry, onInstall, onLaunch, onUninstall, modifier, expanded)
        }
    }
}

/** Kurulum işlemi sürmezken görünen düğmeler: Yükle / Güncelle / Aç / Kaldır. */
@Composable
private fun IdleActions(
    entry: AppEntry,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: (() -> Unit)?,
    modifier: Modifier,
    expanded: Boolean,
) {
    when {
        entry.hasUpdate -> Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onInstall) { Text(stringResource(R.string.install_update)) }
            if (expanded) {
                OutlinedButton(onClick = onLaunch) { Text(stringResource(R.string.install_open)) }
            }
            // Güncelleme varken de kaldırmak mümkün olmalı.
            if (expanded && onUninstall != null) {
                UninstallButton(appTitle = entry.title, onConfirm = onUninstall)
            }
        }
        entry.isInstalled -> Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilledTonalButton(onClick = onLaunch) {
                Text(stringResource(R.string.install_open))
            }
            if (expanded && onUninstall != null) {
                UninstallButton(appTitle = entry.title, onConfirm = onUninstall)
            }
        }
        else -> Button(onClick = onInstall, enabled = entry.latest != null, modifier = modifier) {
            Text(stringResource(R.string.install))
        }
    }
}

/**
 * Kaldır düğmesi + uygulama içi onay kutusu.
 *
 * Düğmeye basınca önce ne yapılacağı açıkça sorulur; onaydan sonra [onConfirm]
 * çağrılır ve kök ekran sistem kaldırma niyetini doğrudan yürütür (olay
 * kanalına uğramadan). Böylece her basışta kullanıcı mutlaka bir geri bildirim
 * görür.
 */
@Composable
fun UninstallButton(
    appTitle: String,
    onConfirm: () -> Unit,
    outlined: Boolean = true,
) {
    var confirming by remember { mutableStateOf(false) }

    if (outlined) {
        OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.install_remove)) }
    } else {
        TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.install_remove)) }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.uninstall_confirm_body, appTitle)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onConfirm()
                    },
                ) { Text(stringResource(R.string.install_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadProgressRow(state: InstallState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val fraction = state.progress.coerceIn(0f, 1f)
        if (state.progress < 0f) {
            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (state.progress < 0f) {
                stringResource(R.string.install_downloading)
            } else {
                "${(fraction * 100).toInt()}% · ${stringResource(R.string.install_downloading)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkingRow(label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ekran görüntüsü türü etiketi. */
@Composable
fun screenshotKindLabel(kind: ScreenshotKind): String = when (kind) {
    ScreenshotKind.PHONE -> stringResource(R.string.label_screenshots)
    else -> kind.label
}

/** Depo adı (depo indeksinde isim yoksa adres döner). */
fun repoDisplayName(repo: RepoConfig): String = repo.name.ifBlank { repo.address }

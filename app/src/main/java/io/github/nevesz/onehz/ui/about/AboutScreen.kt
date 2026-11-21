package io.github.nevesz.onehz.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.BuildConfig
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.update.ReleaseSelector
import io.github.nevesz.onehz.ui.components.OneHzCard
import io.github.nevesz.onehz.ui.components.SettingRow

private const val DEV_URL = "https://github.com/neveSZ"
private const val REPO_URL = "https://github.com/neveSZ/onehz"
private const val LICENSE_URL = "https://github.com/neveSZ/onehz/blob/main/LICENSE"
private const val SHIZUKU_URL = "https://github.com/RikkaApps/Shizuku"
private const val SHIZUKU_FORK_URL = "https://github.com/thedjchi/Shizuku/tree/v13.7.0-thedjchi"
private const val ADAPTIVE_URL = "https://github.com/mahmutaunal/Adaptive-Hz"
private const val ANDROIDX_URL = "https://github.com/androidx/androidx"
private const val SPONSORS_URL = "https://github.com/sponsors/neveSZ"
private const val LIVEPIX_URL = "https://livepix.gg/onehz"

@Composable
fun AboutScreen(
    diagnosticsEnabled: Boolean,
    onUnlockDiagnostics: () -> Unit
) {
    val resources = LocalResources.current
    var taps by remember { mutableIntStateOf(0) }
    var showLicenses by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            OneHzCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_onehz_mark),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable(enabled = !diagnosticsEnabled) {
                                taps++
                                if (taps >= 7) {
                                    taps = 0
                                    onUnlockDiagnostics()
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            OneHzCard {
                Text(stringResource(R.string.about_links), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                LinkRow(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.about_repo_title),
                    subtitle = stringResource(R.string.about_repo_subtitle),
                    url = REPO_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.NewReleases,
                    title = stringResource(R.string.about_releases_title),
                    subtitle = stringResource(R.string.about_releases_subtitle),
                    url = ReleaseSelector.RELEASES_PAGE
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.about_license, stringResource(R.string.credits_license_value)),
                    url = LICENSE_URL
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            OneHzCard {
                Text(
                    stringResource(R.string.about_support_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_support_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LinkRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.about_support_github_title),
                    subtitle = stringResource(R.string.about_support_github_subtitle),
                    url = SPONSORS_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.Payments,
                    title = stringResource(R.string.about_support_livepix_title),
                    subtitle = stringResource(R.string.about_support_livepix_subtitle),
                    url = LIVEPIX_URL
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_support_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            OneHzCard {
                Text(stringResource(R.string.credits_inspired), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_thanks_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LinkRow(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.about_made_by_handle),
                    subtitle = stringResource(R.string.about_dev_subtitle),
                    url = DEV_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.credits_adaptive),
                    subtitle = stringResource(R.string.credits_adaptive_subtitle),
                    url = ADAPTIVE_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.credits_shizuku),
                    subtitle = stringResource(R.string.credits_shizuku_subtitle),
                    url = SHIZUKU_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.credits_shizuku_fork),
                    subtitle = stringResource(R.string.credits_shizuku_fork_subtitle),
                    url = SHIZUKU_FORK_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LinkRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.credits_androidx),
                    subtitle = stringResource(R.string.credits_androidx_subtitle),
                    url = ANDROIDX_URL
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { showLicenses = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.about_third_party), Modifier.padding(start = 6.dp))
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            OneHzCard {
                Text(
                    stringResource(R.string.about_project_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_llm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_translation_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_not_affiliated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLicenses) {
        val notices = remember {
            resources.openRawResource(R.raw.third_party_notices)
                .bufferedReader()
                .use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(stringResource(R.string.about_third_party)) },
            text = {
                Text(
                    text = notices,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    url: String,
    subtitle: String? = null
) {
    val uriHandler = LocalUriHandler.current
    SettingRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { runCatching { uriHandler.openUri(url) } },
        trailing = {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

package io.github.nevesz.onehz.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.update.UpdateState
import io.github.nevesz.onehz.ui.components.BannerTone
import io.github.nevesz.onehz.ui.components.InfoBanner
import io.github.nevesz.onehz.ui.icons.AppIcons

/**
 * Dismissible banner shown at the top of Home. It never opens an arbitrary URL: the
 * checker only accepts release links from the OneHZ repository.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onDismiss: (String) -> Unit
) {
    val release = state.available ?: return
    if (!state.showBanner) return

    val uriHandler = LocalUriHandler.current
    val prerelease = release.isPrerelease
    val openRelease = {
        runCatching { uriHandler.openUri(release.url) }
        Unit
    }

    InfoBanner(
        title = stringResource(
            if (prerelease) R.string.update_banner_prerelease_title
            else R.string.update_banner_title
        ),
        body = stringResource(
            if (prerelease) R.string.update_banner_prerelease_body
            else R.string.update_banner_body,
            release.displayVersion
        ),
        tone = if (prerelease) BannerTone.WARN else BannerTone.INFO,
        icon = if (prerelease) AppIcons.science() else AppIcons.newReleases(),
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = openRelease) {
                    Text(stringResource(R.string.update_view_release))
                }
                TextButton(onClick = { onDismiss(release.tag) }) {
                    Text(stringResource(R.string.update_dismiss))
                }
            }
        }
    )
}

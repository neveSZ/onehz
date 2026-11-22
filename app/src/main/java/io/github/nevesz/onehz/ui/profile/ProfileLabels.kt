package io.github.nevesz.onehz.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileKind

fun ProfileKind.labelRes(): Int = when (this) {
    ProfileKind.ECONOMY -> R.string.profile_economy_title
    ProfileKind.BALANCED -> R.string.profile_balanced_title
    ProfileKind.PERFORMANCE -> R.string.profile_performance_title
    ProfileKind.CUSTOM -> R.string.profile_name
}

@Composable
fun profileLabel(profile: DeviceProfile): String =
    profile.name ?: stringResource(profile.kind.labelRes())

@Composable
fun profileSummary(profile: DeviceProfile): String {
    if (profile.frequency.steps.isEmpty()) return stringResource(R.string.profile_empty)
    if (profile.isFixed) return stringResource(R.string.profile_fixed, profile.frequency.steps[0].hz)
    val chain = profile.frequency.steps.joinToString(" → ") { "${it.hz}" }
    val holds = profile.frequency.steps.dropLast(1)
        .joinToString(" / ") { formatSeconds(it.holdMs ?: 0L) }
    return "$chain Hz · $holds"
}

fun formatSeconds(ms: Long): String =
    String.format(java.util.Locale.getDefault(), "%.1f s", ms / 1000f)

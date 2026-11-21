package io.github.nevesz.onehz.core.update

import java.net.URI

/**
 * Pure release-selection logic. Keeping it free of Android classes makes the update
 * behaviour unit testable, which matters because a wrong comparison would either miss a
 * release or nag users with an older one.
 */
object ReleaseSelector {
    /** Public page linked from Settings and About, independent from automatic checks. */
    const val RELEASES_PAGE = "https://github.com/neveSZ/onehz/releases"

    /**
     * Returns the newest release that is actually newer than [local] for [channel], or
     * null when the user is up to date. Drafts are filtered by the parser.
     */
    fun select(
        releases: List<ReleaseInfo>,
        local: SemanticVersion,
        channel: UpdateChannel
    ): ReleaseInfo? = releases.asSequence()
        .filter { channel == UpdateChannel.STABLE_AND_PRERELEASE || !it.isPrerelease }
        .mapNotNull { release ->
            SemanticVersion.parse(release.tag)?.let { version -> release to version }
        }
        .filter { (_, version) -> version > local }
        .maxByOrNull { (_, version) -> version }
        ?.first

    /**
     * Only release URLs from the OneHZ repository are accepted. A compromised or
     * unexpected response must not be able to send the user to another host.
     */
    fun isAllowedReleaseUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val path = uri.path ?: return false
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.port == -1 &&
            !path.contains("..") &&
            (path == "/neveSZ/onehz/releases" || path.startsWith("/neveSZ/onehz/releases/"))
    }.getOrDefault(false)
}

package io.github.nevesz.onehz.core.update

import io.github.nevesz.onehz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateFailure {
    NETWORK,
    RATE_LIMIT,
    HTTP,
    INVALID_RESPONSE,
    INVALID_VERSION
}

sealed interface UpdateCheckResult {
    /** GitHub answered 304; the caller should use its cached release, if any. */
    data object NotModified : UpdateCheckResult

    /** The repository has no published releases yet. */
    data object NoReleases : UpdateCheckResult

    /** A valid response was received but there is no version newer than the app. */
    data object UpToDate : UpdateCheckResult

    data class NewRelease(val release: ReleaseInfo, val etag: String?) : UpdateCheckResult

    data class Failed(val reason: UpdateFailure, val httpCode: Int? = null) : UpdateCheckResult
}

/**
 * Minimal GitHub Releases client. It deliberately uses the platform HTTP stack instead
 * of adding a networking dependency: the request is small, opt-in, and never runs in the
 * background.
 */
object UpdateChecker {
    private const val API_ROOT = "https://api.github.com/repos/neveSZ/onehz"
    private const val STABLE_ENDPOINT = "$API_ROOT/releases/latest"
    private const val ALL_ENDPOINT = "$API_ROOT/releases?per_page=30"
    private const val MAX_RESPONSE_BYTES = 1_000_000
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    private data class HttpResponse(
        val code: Int,
        val body: String?,
        val etag: String?
    )

    suspend fun check(
        localVersionName: String,
        channel: UpdateChannel,
        etag: String?
    ): UpdateCheckResult {
        val local = SemanticVersion.parse(localVersionName)
            ?: return UpdateCheckResult.Failed(UpdateFailure.INVALID_VERSION)

        val endpoint = when (channel) {
            UpdateChannel.STABLE -> STABLE_ENDPOINT
            UpdateChannel.STABLE_AND_PRERELEASE -> ALL_ENDPOINT
        }
        val response = request(endpoint, etag)

        return when {
            response.code == -1 -> UpdateCheckResult.Failed(UpdateFailure.NETWORK)
            response.code == HttpURLConnection.HTTP_NOT_MODIFIED -> UpdateCheckResult.NotModified
            response.code == HttpURLConnection.HTTP_NOT_FOUND -> UpdateCheckResult.NoReleases
            response.code == HttpURLConnection.HTTP_FORBIDDEN ||
                response.code == 429 -> UpdateCheckResult.Failed(UpdateFailure.RATE_LIMIT, response.code)
            response.code != HttpURLConnection.HTTP_OK ->
                UpdateCheckResult.Failed(UpdateFailure.HTTP, response.code)
            response.body == null -> UpdateCheckResult.Failed(UpdateFailure.INVALID_RESPONSE)
            else -> {
                val releases = try {
                    parseReleases(response.body)
                } catch (_: JSONException) {
                    return UpdateCheckResult.Failed(UpdateFailure.INVALID_RESPONSE)
                } catch (_: IOException) {
                    return UpdateCheckResult.Failed(UpdateFailure.NETWORK)
                }
                if (releases.isEmpty()) {
                    UpdateCheckResult.NoReleases
                } else {
                    val selected = ReleaseSelector.select(releases, local, channel)
                    if (selected == null) UpdateCheckResult.UpToDate
                    else UpdateCheckResult.NewRelease(selected, response.etag)
                }
            }
        }
    }

    private suspend fun request(endpoint: String, etag: String?): HttpResponse =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty(
                        "User-Agent",
                        "OneHZ/${BuildConfig.VERSION_NAME} (+https://github.com/neveSZ/onehz)"
                    )
                    if (!etag.isNullOrBlank()) setRequestProperty("If-None-Match", etag)
                }

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    val body = connection.inputStream.use { it.readLimited(MAX_RESPONSE_BYTES) }
                    HttpResponse(code, body, connection.getHeaderField("ETag"))
                } else {
                    runCatching { connection.errorStream?.close() }
                    HttpResponse(code, null, connection.getHeaderField("ETag"))
                }
            } catch (_: IOException) {
                HttpResponse(-1, null, null)
            } catch (_: SecurityException) {
                HttpResponse(-1, null, null)
            } finally {
                connection?.disconnect()
            }
        }

    private fun InputStream.readLimited(maxBytes: Int): String {
        val buffer = ByteArray(8 * 1024)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw IOException("GitHub response is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private fun parseReleases(body: String): List<ReleaseInfo> {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            return buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::parseRelease)?.let(::add)
                }
            }
        }
        return listOfNotNull(parseRelease(JSONObject(trimmed)))
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        if (json.optBoolean("draft", false)) return null

        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) return null
        val version = SemanticVersion.parse(tag) ?: return null

        val url = json.optString("html_url").trim()
        if (!ReleaseSelector.isAllowedReleaseUrl(url)) return null

        val name = json.optString("name").trim().takeIf { it.isNotEmpty() }
        return ReleaseInfo(
            tag = tag,
            url = url,
            name = name,
            prereleaseFlag = json.optBoolean("prerelease", false) || version.isPrerelease
        )
    }
}

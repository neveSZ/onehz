package io.github.nevesz.onehz.core.backup

import android.content.Context
import android.net.Uri
import io.github.nevesz.onehz.BuildConfig
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.core.profile.DeviceProfile
import io.github.nevesz.onehz.core.profile.ProfileCodec
import io.github.nevesz.onehz.core.profile.ProfileDefaults
import io.github.nevesz.onehz.core.profile.ProfileKind
import io.github.nevesz.onehz.core.profile.ProfileState
import io.github.nevesz.onehz.core.profile.ProfileStore
import java.io.ByteArrayOutputStream

data class ImportResult(
    val success: Boolean,
    val profiles: Int = 0,
    val assignments: Int = 0,
    val adapted: Int = 0,
    val missingAssignments: Int = 0,
    val sleep: Boolean = false,
    val error: String? = null
)

/**
 * Manual configuration backup. Files are plain JSON and are written through SAF, so the
 * app does not need storage permissions and never touches the network.
 */
object BackupManager {

    const val MAX_BYTES = 256 * 1024

    fun export(context: Context, uri: Uri, panelRates: List<Int>): Boolean {
        val state = ProfileStore.state(context, panelRates)
        val sleepWindow = SleepPreferences.window(context)
        // The Doze whitelist is system state managed through Shizuku; it is not exported.
        val data = BackupData(
            state = state,
            enabled = OneHzPreferences.isEnabled(context),
            aodEnabled = OneHzPreferences.aodEnabled(context),
            aodBrightnessEnabled = OneHzPreferences.aodBrightnessEnabled(context),
            aodBrightnessPct = OneHzPreferences.aodBrightnessPct(context),
            aodBrightnessMinPct = OneHzPreferences.aodBrightnessMinPct(context),
            aodBrightnessMaxPct = OneHzPreferences.aodBrightnessMaxPct(context),
            forcedVideoPackages = OneHzPreferences.forcedVideoPackages(context),
            sleep = SleepBackup(
                enabled = SleepPreferences.enabled(context),
                deep = SleepPreferences.deep(context),
                skipCharging = SleepPreferences.skipCharging(context),
                skipMusic = SleepPreferences.skipMusic(context),
                windowEnabled = SleepPreferences.windowEnabled(context),
                windowStartMinute = sleepWindow.startMinute,
                windowEndMinute = sleepWindow.endMinute
            ),
            appVersion = BuildConfig.VERSION_NAME,
            deviceRates = ProfileStore.rates(context)
        )
        val json = BackupCodec.encode(data)
        return runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
                true
            } ?: false
        }.getOrDefault(false)
    }

    fun import(context: Context, uri: Uri, panelRates: List<Int>): ImportResult {
        val raw = readLimited(context, uri)
            ?: return ImportResult(false, error = "file-too-large-or-unreadable")
        val data = BackupCodec.decode(raw)
            ?: return ImportResult(false, error = "invalid-backup")
        val rates = ProfileDefaults.normalizedRates(panelRates)
        val defaults = ProfileDefaults.forRates(rates).associateBy { it.kind }
        var adapted = 0

        val profiles = mutableListOf<DeviceProfile>()
        val seenIds = mutableSetOf<String>()
        data.state.profiles.forEach { imported ->
            val profile = if (imported.isBuiltIn && !imported.customized) {
                defaults[imported.kind] ?: imported
            } else {
                val frequency = ProfileCodec.adaptFrequency(imported.frequency, rates)
                if (frequency != imported.frequency) adapted++
                imported.copy(
                    id = if (imported.isBuiltIn) imported.kind.canonicalId else imported.id,
                    name = if (imported.isBuiltIn) null else imported.name,
                    frequency = frequency,
                    video = imported.video.copy(
                        minHz = ProfileDefaults.nearestRate(imported.video.minHz, rates)
                    )
                )
            }
            if (profile.id in seenIds) return@forEach
            seenIds += profile.id
            profiles += profile
        }
        ProfileKind.entries
            .filter { it != ProfileKind.CUSTOM }
            .forEach { kind ->
                if (profiles.none { it.kind == kind }) {
                    defaults[kind]?.let { profiles += it }
                }
            }

        val validIds = profiles.mapTo(mutableSetOf()) { it.id }
        val fallbackId = profiles.firstOrNull { it.kind == ProfileKind.BALANCED }?.id
            ?: profiles.first().id
        var missingAssignments = 0
        val assignments = data.state.assignments.map { assignment ->
            if (assignment.profileId in validIds) {
                assignment
            } else {
                missingAssignments++
                assignment.copy(profileId = fallbackId)
            }
        }
        val active = data.state.activeProfileId.takeIf { it in validIds } ?: fallbackId
        val state = ProfileState(
            profiles = profiles,
            activeProfileId = active,
            assignments = assignments.distinctBy { it.packageName },
            autoEconomyOnBatterySaver = data.state.autoEconomyOnBatterySaver
        )
        ProfileStore.save(context, state)

        OneHzPreferences.setEnabled(context, data.enabled)
        OneHzPreferences.setAodEnabled(context, data.aodEnabled)
        OneHzPreferences.setAodBrightnessEnabled(context, data.aodBrightnessEnabled)
        OneHzPreferences.setAodBrightnessPct(context, data.aodBrightnessPct)
        OneHzPreferences.setAodBrightnessBand(
            context,
            data.aodBrightnessMinPct,
            data.aodBrightnessMaxPct
        )
        OneHzPreferences.setForcedVideoPackages(context, data.forcedVideoPackages)
        SleepPreferences.setEnabled(context, data.sleep.enabled)
        SleepPreferences.setDeep(context, data.sleep.deep)
        SleepPreferences.setSkipCharging(context, data.sleep.skipCharging)
        SleepPreferences.setSkipMusic(context, data.sleep.skipMusic)
        SleepPreferences.setWindowEnabled(context, data.sleep.windowEnabled)
        SleepPreferences.setWindow(
            context,
            data.sleep.windowStartMinute,
            data.sleep.windowEndMinute
        )

        return ImportResult(
            success = true,
            profiles = profiles.size,
            assignments = assignments.size,
            adapted = adapted,
            missingAssignments = missingAssignments,
            sleep = true
        )
    }

    private fun readLimited(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()
}

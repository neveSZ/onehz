package io.github.nevesz.onehz.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile consumed by the release build.
 *
 * Run with a connected device (API 31+):
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * The result is written to app/src/release/generated/baselineProfiles/ and should be
 * committed together with the change that alters startup or the main screens.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = "io.github.nevesz.onehz",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // Wait for the home screen content instead of a fixed sleep.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
        device.waitForIdle()
    }
}

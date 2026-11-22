import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = versionProperties.getProperty("versionName")
val appVersionCode = versionProperties.getProperty("versionCode").toInt()

val localKeystoreCandidates = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), "keys/onehz/keystore.properties")
)
val keystorePropertiesFile = localKeystoreCandidates.firstOrNull { it.isFile }
val keystoreProperties = Properties().apply {
    keystorePropertiesFile?.let { file -> FileInputStream(file).use { load(it) } }
}

fun signingValue(property: String, env: String): String? =
    System.getenv(env) ?: keystoreProperties.getProperty(property)

val releaseStorePath = signingValue("storeFile", "ONEHZ_KEYSTORE_FILE")
val releaseStoreFile = releaseStorePath?.let { path ->
    val direct = File(path)
    when {
        direct.isAbsolute -> direct
        keystorePropertiesFile != null ->
            File(keystorePropertiesFile.parentFile, path).takeIf { it.isFile } ?: rootProject.file(path)
        else -> rootProject.file(path)
    }
}
val releaseStorePassword = signingValue("storePassword", "ONEHZ_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ONEHZ_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ONEHZ_KEY_PASSWORD")
val signingReady = releaseStoreFile?.isFile == true && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "io.github.nevesz.onehz"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.nevesz.onehz"
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        localeFilters += listOf("en", "pt-rBR", "es")
    }

    packaging {
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "META-INF/*.version"
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}

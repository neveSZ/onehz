# Building OneHZ

## Requirements

| Tool | Version |
|---|---|
| JDK | **25** (the Gradle daemon toolchain is set to 25) |
| Android SDK platform | **37** |
| Build tools | **36.0.0** |
| Gradle | wrapper included (9.7.1) |

The project uses AGP 9.4.0, Kotlin 2.4.20, Compose, and `compileSdk`/`targetSdk` 37.
`minSdk` is 31.

## Android SDK

Install the required packages with `sdkmanager`:

```powershell
sdkmanager "platforms;android-37" "build-tools;36.0.0" "platform-tools"
```

Create `local.properties` with your SDK path (never commit it):

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

## Build and test

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Release build, with R8 and resource shrinking:

```powershell
.\gradlew.bat assembleRelease
```

The release APK is written to `app/build/outputs/apk/release/`.

## Signing

The keystore and its password **must stay outside the repository**. The build looks for
signing values in this order:

1. environment variables;
2. `keystore.properties` in the repository root;
3. `%USERPROFILE%\keys\onehz\keystore.properties`.

Environment variables:

```text
ONEHZ_KEYSTORE_FILE
ONEHZ_KEYSTORE_PASSWORD
ONEHZ_KEY_ALIAS
ONEHZ_KEY_PASSWORD
```

A properties file looks like:

```properties
storeFile=onehz-release.jks
storePassword=...
keyAlias=onehz
keyPassword=...
```

`storeFile` may be absolute or relative to the properties file. See
[keystore.properties.example](../keystore.properties.example).

> Never commit `keystore.properties`, `*.jks`, or `*.keystore`. They are ignored by Git.

## Versioning

`version.properties` is the single source of truth:

```properties
versionName=1.0.0
versionCode=10000
```

The release workflow checks that the tag (`v1.0.0`) matches `versionName`.

## R8

Release builds use R8 full mode by default, plus resource shrinking. The rules in
`app/proguard-rules.pro` keep AIDL stubs, the UserService, Shizuku, and Binder plumbing.
Do not remove these keeps without testing a full release build on a device.

The current APK is about **3.1 MB**. `mapping.txt` is generated under
`app/build/outputs/mapping/release/`; keep it for crash deobfuscation and do not commit
it.

## Troubleshooting

- **Unsupported class file / JDK error:** install JDK 25 and check
  `gradle/gradle-daemon-jvm.properties`.
- **`platform android-37 not found`:** install the SDK platform above.
- **Release APK unsigned:** no signing values were found. Check the properties/env
  variables.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`:** the installed APK uses a different signing
  key. Uninstall it first.
- **Shizuku service keeps restarting:** increment `API_REVISION` in `ShizukuGateway`
  after changing the AIDL interface.
- **Build cache weirdness:** run `.\gradlew.bat clean` and rebuild.

# Notch — handoff

The in-app player is scaffolded and the debug build succeeds on this VM.

## Product

An Android app for a Samsung Galaxy S23. It plays audio inside the app and applies up to four true notches.

A normal app on a stock S23 cannot insert a real notch into every other app's audio (YouTube, Spotify, and the rest). That path was set aside. Rooting the phone would be required for a system-wide notch, and unlocking the bootloader trips Knox permanently.

## What is built

- Package `com.exoplanet.notch`.
- Open an audio file, or play a looping built-in test noise.
- Four notches, all off at launch, under a “Center Frequency Hz” header centered on the frequency column. Frequencies start at 1, 2, 4, and 10 kHz. Each has a round on/off button, and more than one can be on. Frequency and width can be typed or stepped with − and + (frequency 500 Hz–10 kHz; holding steps faster). The frequency fields do not repeat the Hz unit.
- Width starts at 50 Hz and accepts 5–100 Hz.
- Each enabled notch removes its center frequency completely. There is no depth control and no response graph.
- Background playback through a media notification. The notches apply only to sound this app plays.

## Filter

Four cascaded Robert Bristow-Johnson notches. The center is a zero. Width is the approximate −3 dB bandwidth in hertz. A disabled notch is an identity filter. Coefficients ramp over about 20 ms.

## Player wiring

- Media3 ExoPlayer 1.11.1 and media3-session 1.11.1.
- Custom `AudioProcessor` on `DefaultAudioSink`, with `ToInt16PcmAudioProcessor` in front of it.
- Float output stays off. Offload is disabled. Both skip audio processors.
- `MediaSessionService` with foreground service type `mediaPlayback`.

## Toolchain on this VM

Ubuntu 26.04 on the development VM. About 5.2 GB RAM. No `/dev/kvm`. `sudo` asks for a password, so nothing was installed with apt.

- JDK: `$HOME/jdks/jdk-21.0.13+9` (Temurin 21). Set `JAVA_HOME` to that path.
- Android SDK: `$HOME/Android/Sdk` (`local.properties` already points here).
- Installed: platform-tools, `platforms;android-37.0`, `platforms;android-37.2`, build-tools 37.0.0. AGP also installed build-tools 36.0.0.
- `compileSdk` is API 37.0 (`platforms/android-37.0`). There is no `platforms;android-37` package.
- AGP 9.4.1, Gradle 9.6.1, Kotlin 2.4.21, Compose BOM 2026.09.00, activity-compose 1.13.0.
- AGP 9 compiles Kotlin itself. Do not apply `org.jetbrains.kotlin.android`. Do apply `org.jetbrains.kotlin.plugin.compose`.
- minSdk 26, targetSdk 36. Google Play requires target 36 for a new app as of August 31, 2026.
- Play upload bundle: `app/build/outputs/bundle/release/app-release.aab` after `./gradlew :app:bundleRelease`. The upload keystore is `keystore/notch-upload.jks`, described by `keystore.properties`. Both are gitignored. Back them up. Losing the upload key blocks updates unless Play App Signing can reset it.
- Signed release APK: `app/build/outputs/apk/release/app-release.apk` after `./gradlew :app:assembleRelease`. Tag `v1.0` publishes `dist/notch-1.0.apk` through `.github/workflows/release.yml`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Rebuild:

```
export JAVA_HOME="$HOME/jdks/jdk-21.0.13+9"
export ANDROID_HOME="$HOME/Android/Sdk"
cd "$HOME/grok-audio"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Gradle heap is capped at 1280m in `gradle.properties` because of the RAM. Do not run Android Studio and a Gradle build at the same time.

## Phone and emulator

`adb` is `$HOME/Android/Sdk/platform-tools/adb`. Android udev rules are installed. Nested virtualization is unavailable because Windows 11 on the host already owns AMD-V, so the emulator stays off.

The debug APK was installed on the test phone and `MainActivity` launched on 2026-10-28. USB debugging must stay authorized for later installs. Auto Blocker blocks that toggle until it is turned off under Settings → Security and privacy. Reinstall with:

```
"$HOME/Android/Sdk/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

Android Studio can be unpacked under the home directory later. It was not installed.

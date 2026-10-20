# Build notes

- Android namespace/application ID: `ai.lailaba.mobile`.
- Android compile/target SDK: 35; minimum SDK: 26.
- Java source compatibility: 17.
- The accessibility service description must exist as a string resource referenced by `accessibility_service_config.xml`.
- Codemagic uses `mac_mini_m2` to avoid a Linux build-machine billing entitlement error on the intended personal plan. Account entitlements can change, so verify runner availability in Codemagic.
- The workflow downloads Gradle 8.9 and runs `assembleDebug`. A compatible Android SDK must be present in the runner environment.
- No successful compile is claimed until Codemagic or Android Studio produces `app/build/outputs/apk/debug/app-debug.apk`.

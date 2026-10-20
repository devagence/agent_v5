# Lailaba AI Device Agent for Android

Native Android companion app for permissioned, user-approved device assistance. This repository contains source code, not a prebuilt APK.

## Added in this upgrade

- Device model and Android version information.
- Battery percentage and charging state.
- Network transport/connectivity summary and approximate data-partition storage usage.
- Launchable-app discovery using Android's launcher intent and package-visibility rules (no broad `QUERY_ALL_PACKAGES` permission).
- Open validated HTTP/HTTPS links in a browser or compatible app.
- Share text through Android's system share sheet; the user chooses the destination.
- Browser web search, map/place search, and camera-app launch.
- User-reviewed alarm and timer creation, plus calendar event editor prefill.
- Existing contact lookup, optional inbox SMS lookup, dialer launch, SMS draft creation, settings navigation, app launching, and opt-in accessibility text tapping.
- Optional, user-enabled Notification Listener for reading up to 15 active notifications (no notification content is persisted by the service).
- Set media/ring/alarm/notification volume after the in-app approval dialog.
- Request app uninstall through Android system confirmation; protected/system apps may not be removable.
- Updated Codemagic workflow using the `mac_mini_m2` runner, shell fail-fast settings, Android SDK presence check, and multiline Gradle invocation.

## Build a debug APK

1. Open this `lailaba-android` folder in Android Studio and allow Gradle sync.
2. Install Android SDK Platform 35 and a compatible Android Build Tools version if Android Studio asks.
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The APK should be at `app/build/outputs/apk/debug/app-debug.apk`.

Alternatively, with a compatible Java 17 installation, Android SDK, and Gradle available:

```bash
gradle --no-daemon assembleDebug
```

The Codemagic workflow downloads Gradle 8.9 and expects an Android SDK on the selected runner. If the runner's environment does not expose `ANDROID_HOME` or an SDK platform, the workflow intentionally fails with a clear message rather than producing a misleading result.

## Current device capabilities

| Capability | Behavior |
|---|---|
| Device and battery information | Reads basic local status without a special permission. |
| Network status | Reports active transport and Android network capabilities; does not inspect traffic or credentials. |
| Storage status | Approximate total/free space for the data partition. |
| App discovery | Lists launchable apps visible through Android's package-visibility rules. |
| Launch app | Launches an installed app by package name when Android exposes a launch intent. |
| Open website | Accepts only valid `http`/`https` URLs and hands them to Android. |
| Share text | Opens Android's share sheet; the user chooses and confirms the destination. |
| Web search and maps | Opens browser search or a map app with a query; user controls subsequent actions. |
| Camera | Opens an installed camera app; capture and saving remain user-controlled. |
| Alarm and timer | Opens the system clock UI with values prefilled; user reviews the setup. |
| Calendar event | Opens an event editor; user reviews and saves the event. |
| Contacts | Requires the user to grant Contacts permission. Contact queries are capped. |
| Inbox SMS | Requires SMS permission and may be restricted by Android or distribution policy. Use only where appropriate. |
| Calls | Opens the system dialer with a number filled in; it does not place the call automatically. |
| SMS | Opens the messaging app with a draft; the user reviews and presses Send. |
| Settings | Opens supported Android Settings pages; the user makes protected changes themselves. |
| Accessibility | Optional. The user must enable the service in Android Settings. Interaction is limited to accessible UI exposed by the foreground app. |
| Active notifications | Requires explicit Notification Access in Android Settings; returns at most 15 currently active notifications and may expose private content. |
| Volume | Sets media, ring, alarm, or notification volume after approval; Do Not Disturb and OEM policies may affect results. |
| App uninstall request | Opens Android confirmation; system apps and protected packages may not be removable. |

All tool calls display an approval dialog before execution. The accessibility tap is immediate-only: if the requested text is not currently visible, no deferred tap is queued. Approval does not override operating-system restrictions or grant permissions that Android has not granted.

## Shell and unrestricted control are not available to ordinary apps

This app deliberately does **not** expose an arbitrary shell-command execution tool, root access, silent input injection, hidden monitoring, or security-control bypass. An ordinary Android application runs in an app sandbox and cannot acquire unrestricted system-wide control just by declaring permissions. Android's shell is a separate identity (for example, the ADB shell), not a shell that an ordinary app can legitimately impersonate. For managed devices or testing, use Android's documented device-owner APIs or a separately authorized ADB workflow with clear operator control; do not embed broad shell execution into the consumer app.

## Security notes

- API keys are encrypted with AES-GCM using an Android Keystore key.
- Configure only a provider URL you trust. The API key is sent to the HTTPS base URL entered in Provider setup.
- Every model-requested device tool requires a visible approval dialog. Do not remove this gate for sensitive actions.
- The model's output is untrusted input: tool arguments must be validated locally. URL opening restricts schemes to HTTP/HTTPS; SMS sending and calling remain user-confirmed system UI flows.
- Avoid putting private messages, contacts, credentials, or other sensitive content into model prompts unless needed and understood by the user.
- Public Play distribution of SMS-reading functionality can be restricted. Review current Android and Play policies before release.
- Add a privacy policy and test on real devices across Android versions and OEMs before publishing.

## Known limitations / verification

- This upgrade has not been compiled in an Android SDK build environment in this session. Treat the ZIP as updated source code pending a successful Android Studio or Codemagic build.
- Package visibility varies by Android version and device. The app list may not show every installed package.
- Accessibility cannot reliably operate every app and must never be used to defeat another app's security protections.
- The app depends on network access and an OpenAI-compatible API that supports `/models` and `/chat/completions` tool calling.


## Capability status — read before testing

The current source defines 23 model-callable tools. These are a mixture of read-only status queries and user-mediated actions. Camera, maps, browser, settings, alarm, timer, calendar, dialer, SMS composer, and share sheet actions open another Android interface; they do not mean the agent can silently control that app or guarantee the user completes the action. Contacts and inbox SMS require permissions and remain subject to Android OS and distribution-policy restrictions. The app does not have unrestricted root, shell, package-management, or system-setting privileges.

External activity launches now return a result from the UI-thread launch attempt and report unavailable handlers or launch failures rather than assuming every request succeeded. The project still requires an Android SDK build and on-device tests before it can be considered release-ready.


## Additional tools added in v4

- `get_active_notifications`: reads only currently active notifications after explicit Notification Access is enabled; results are not persisted by the service.
- `set_volume`: adjusts a supported audio stream after approval and reports the volume Android subsequently reports.
- `request_uninstall_app`: opens Android's uninstall confirmation and does not silently remove apps.

These additions have been source-reviewed and XML-parsed, but are not yet compiled or device-tested in this environment. See `docs/TOOL_AUDIT.md` for the per-tool behavior and verification limits. Notification access is highly sensitive; enable it only if you understand the data it exposes.

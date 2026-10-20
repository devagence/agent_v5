# Lailaba Device Agent — Tool Audit (v4)

This is a source-level inventory, not a claim of a successful Android build or on-device test. The environment used to prepare this archive has Java but no installed Gradle executable or Android SDK build setup.

## AI-callable tools (23)

| Tool | Implementation behavior | Permission / OS boundary | Verification status |
|---|---|---|---|
| `get_device_info` | Reads manufacturer, model, Android release/API level, app version and accessibility bridge status. | Basic app-readable information. | Source reviewed; not device-tested. |
| `get_battery_status` | Reads Android battery broadcast and reports percentage/state. | Basic app-readable information. | Source reviewed; not device-tested. |
| `get_network_status` | Reads active network transport and capabilities. | Does not inspect traffic or credentials. | Source reviewed; not device-tested. |
| `get_storage_status` | Reports data-partition total/available space. | Not a full disk inventory. | Source reviewed; not device-tested. |
| `list_apps` | Lists launchable apps visible through package-visibility rules. | May not show every installed package. | Source reviewed; not device-tested. |
| `open_url` | Validates HTTP/HTTPS URL and launches a handler. | User reviews the destination. | Source reviewed; not device-tested. |
| `share_text` | Opens Android share sheet with text. | User chooses recipient/app and confirms. | Source reviewed; not device-tested. |
| `search_web` | Opens browser search URL. | Does not submit credentials or make purchases. | Source reviewed; not device-tested. |
| `search_maps` | Opens geo search in a compatible map app. | User reviews destination. | Source reviewed; not device-tested. |
| `open_camera` | Opens a compatible camera app. | User controls capture and saving. | Source reviewed; not device-tested. |
| `set_alarm` | Opens clock UI prefilled with alarm. | Clock app availability and user confirmation apply. | Source reviewed; not device-tested. |
| `set_timer` | Opens clock UI prefilled with timer. | Clock app availability and user confirmation apply. | Source reviewed; not device-tested. |
| `create_calendar_event` | Opens calendar event editor prefilled with details. | User must review and save. | Source reviewed; not device-tested. |
| `get_contacts` | Queries contacts and returns limited matches. | Runtime Contacts permission required; output can be private. | Source reviewed; not device-tested. |
| `read_sms` | Reads up to 20 matching inbox SMS records. | Runtime SMS permission required; OS/distribution restrictions apply. | Source reviewed; not device-tested. |
| `dial_number` | Opens dialer with number. | Does not place the call. | Source reviewed; not device-tested. |
| `compose_sms` | Opens messaging composer with recipient/body. | User must review and send. | Source reviewed; not device-tested. |
| `open_settings` | Opens supported Settings page; includes notification-access page. | Protected settings are changed by user in Android Settings. | Source reviewed; not device-tested. |
| `open_app` | Uses a launch intent for supplied package. | App must expose a launch intent and be visible to Android. | Source reviewed; not device-tested. |
| `tap_text` | Attempts immediate click on exact matching accessible visible text. | User must enable accessibility; cannot guarantee app support; must not be used to bypass consent/security prompts. | Source reviewed; not device-tested. |
| `get_active_notifications` | Reads at most 15 active notification title/text/package entries. | Requires explicit Notification Access; notification contents may include sensitive information; service does not persist them. | Source reviewed; not device-tested. |
| `set_volume` | Sets media/ring/alarm/notification stream to normalized percentage and reports resulting volume. | DND/OEM policy may affect audible output; requires tool approval. | Source reviewed; not device-tested. |
| `request_uninstall_app` | Opens Android package uninstall confirmation. | OS/user confirmation required; protected/system apps may not be removable; cannot target the agent itself. | Source reviewed; not device-tested. |

## Checks performed

- 23 unique tool schemas found.
- Every schema has a corresponding dispatcher case.
- AndroidManifest.xml, strings.xml and accessibility service XML parse successfully.
- Java source brace counts balance.
- ZIP integrity check passes.

## Checks not performed

- Android Gradle compilation, lint, APK installation, runtime permissions, OEM-specific behavior and live provider tool-call tests have not been run.
- Therefore all rows are **implemented in source, not verified in a running app**.

## Explicit non-capabilities

This consumer app does not have arbitrary root/shell access, silent calls or SMS sending, general package install/uninstall without OS confirmation, access to secure screen content, or unrestricted control of protected system settings. Declaring privileged Android permissions would not grant those privileges.

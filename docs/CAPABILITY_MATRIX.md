# Device capability matrix

| Category | Available approach | User/system boundary |
|---|---|---|
| Basic device status | Android Build APIs | Read-only, local data |
| Battery | Sticky battery status broadcast | Read-only, local data |
| Network status | ConnectivityManager network capabilities | Read-only; no traffic inspection |
| Storage status | StatFs on data partition | Approximate total/free capacity only |
| Apps | Query launcher activities | Android package visibility applies |
| Open app | PackageManager launch intent | Only launchable and visible apps; launch failures are returned as tool results |
| Web | ACTION_VIEW with HTTP(S) validation | Android selects browser/handler; launch result is checked on the UI thread |
| Sharing | ACTION_SEND chooser | User selects destination and confirms; chooser launch is checked |
| Web search | Browser search URL | Opens browser; user controls next steps |
| Maps | geo: URI | Opens a map handler; user controls navigation |
| Camera | ACTION_IMAGE_CAPTURE | External camera UI; user controls capture |
| Alarm / timer | AlarmClock intents | Clock UI shown; user reviews |
| Calendar | ACTION_INSERT with CalendarContract | Event editor shown; user saves |
| Contacts | Contacts Provider | Runtime permission + tool approval |
| SMS inbox | SMS Provider | Runtime permission and platform/distribution restrictions |
| Phone call | ACTION_DIAL | User places the call in dialer |
| SMS compose | ACTION_SENDTO | User reviews and sends the draft |
| Settings | Supported Settings intents | Opens a supported settings screen; user changes protected settings manually |
| Visible UI | Opt-in Accessibility service | Immediate approved tap only; no deferred taps; UI must be exposed/accessibility-readable |
| Shell / root | Not provided by this app | Requires separate authorized debugging or managed-device mechanisms |

A tool approval dialog is an application-level confirmation, not a replacement for Android permission grants, roles, or system authorization.


## Important distinction

This is a tool-enabled Android assistant, not an unrestricted device-management controller. Some tools read status; others launch Android screens for the user to complete. Directly toggling protected settings, silently sending SMS, placing calls, reading all app-private data, force-stopping arbitrary apps, arbitrary shell/root execution, and privileged system operations are not implemented. Tool approval does not grant an Android permission by itself.


## v4 additions (implemented, not yet compiled/device-tested)

- Active notification read: user must enable Android Notification Access; capped at 15 active notifications, no persistent storage by this service.
- Audio volume: media, ring, alarm, and notification streams; 0–100% input; result reports Android's actual resulting volume.
- App uninstall request: launches Android's confirmation UI; OS/user can cancel or block it.

# Debloat list for the car unit (AC8227L, Android 8.1)

**Status: proposal only. Nothing has been applied to the unit.** Tick the boxes you approve, then I will run them.

How it works: each item is turned off with `pm disable-user --user 0 <package>`. This is fully reversible with `pm enable <package>`. Nothing is deleted, and `/system` is not touched.

Evidence this list is based on (from the check you ran earlier):
- Free RAM was only 39 MB, with 365 MB of swap in use, so the unit is under memory pressure.
- `/system` is 98% full, `/vendor` 95%, and the eMMC is 70-80% worn.
- CPU was 335% idle (of 400%) at the moment of the check, so the chip is not the main problem at rest.

## Tier A: low risk, recommended

| OK | Package | What it is | Why |
|---|---|---|---|
| [ ] | `com.google.android.googlequicksearchbox` | Google app / assistant (Velvet, 134 MB) | Runs two processes, about 114 MB of RAM (`:search` 92 MB + `:interactor` 22 MB). Not needed for maps or video. |
| [ ] | `com.adobe.reader` | Adobe Acrobat (data copy; a 22 MB system copy also exists) | PDF reader on a car screen. |
| [ ] | `com.mediatek.mtklogger` | MediaTek logger | Writes logs to the worn eMMC. |
| [ ] | `com.mediatek.mtklogger.proxy` | MediaTek logger helper | Same. |
| [ ] | `com.jan.autotest` | Factory test client (`ivi-autotest-Client`) | Factory use only. |
| [ ] | `com.google.android.feedback` | Google feedback | Not needed. |
| [ ] | `com.google.android.onetimeinitializer` | Google first-run setup | Not needed after setup. |
| [ ] | `com.google.android.partnersetup` | Google partner setup | Not needed after setup. |
| [ ] | `com.google.android.gms.setup` | Play services setup wizard | Not needed after setup. |
| [ ] | `com.google.android.configupdater` | Google config updater | Not needed. |
| [ ] | `com.google.android.backuptransport` | Google backup | Not needed. |
| [ ] | `com.google.android.syncadapters.calendar` | Calendar sync | Car unit does not use a calendar. |
| [ ] | `com.mediatek.calendarimporter` | Calendar importer | Same. |
| [ ] | `com.android.providers.calendar` | Calendar storage | Same. |
| [ ] | `com.android.sharedstoragebackup` | Backup helper | Not needed. |
| [ ] | `com.android.wallpaperbackup` | Wallpaper backup | Not needed. |

## Tier B: probably safe, but you decide

| OK | Package | What it is | Question for you |
|---|---|---|---|
| [ ] | `com.can.activity` (`ivi-canbus`, 26 MB RAM) | CAN bus screen | You said the steering wheel is not connected. Do you use the car-info/CAN pages? |
| [ ] | `com.jancar.steeringwheelkeys` | Steering wheel key learning | Same: no steering controls connected. |
| [ ] | `com.jancar.radio` (18 MB RAM) | FM/AM radio app | Do you use the radio? |
| [ ] | `com.touchtype.swiftkey` (43 MB RAM) | SwiftKey keyboard | The stock keyboard `MtkLatinIME` also exists. Keep only one. |
| [ ] | `org.telegram.messenger` (55 MB RAM) | Telegram | Your own app. Disable only if you do not use it on the unit. |
| [ ] | `com.google.android.syncadapters.contacts` | Contacts sync | Keep `com.android.providers.contacts`: Bluetooth phonebook needs it. |
| [ ] | `com.android.wallpaper.livepicker`, `com.android.wallpapercropper`, `com.android.wallpaperpicker` | Wallpaper pickers | Do you change wallpaper from the unit? |
| [ ] | `com.mediatek.engineermode` | Engineer/factory menu | Harmless, but you may want it for the factory menu. |
| [ ] | `com.jancar.manual` | On-screen manual | Only useful if you read the manual on the unit. |
| [ ] | Duplicates: your own `com.android.chrome` and `app.revanced.android.gms` | Browser and microG | Two Google Play services exist (real and microG). Which do your apps (YouTube ReVanced, Maps) use? Please tell me before I touch either. |

## Never touch (the unit or APK installing would break)

`com.android.packageinstaller` (this is what installs APKs), `com.android.settings` / `com.jancar.settings` / `MtkSettings`, `com.android.systemui` / `ivi-systemui`, `com.android.launcher3` (the unit's launcher), `com.jancar.services`, `com.jancar.btservice`, `com.jancar.bluetooth`, `com.android.bluetooth`, `com.autochips.backcarapp` (reverse camera), `com.autochips.quickbootmanager`, `com.autochips.android.atcLocationService`, `com.android.providers.*` (media, downloads, settings, contacts, telephony), `com.android.webview`, `com.android.keychain`, `com.android.defcontainer`, `com.android.phone`, `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending` (Play Store).

## The biggest single saving: TXZSmartWakeUp (236 MB)

- `/system/app/TXZSmartWakeUp` is 236 MB (package `com.txznet.txz`, a Chinese voice-assistant wake-up). Related packages are in `/vendor/app` (`com.txznet.aipal`, `com.txznet.smartadapter`).
- None of them appear in the unit's installed-package list from your earlier check. If that is right, they are not running, and disabling does nothing for speed.
- Their only cost is disk space: removing them is what would free `/system`. That needs the repacked firmware, not adb.
- **Not verified yet:** the unit is currently offline, so I could not re-check with `pm list packages`. I will confirm when it is connected.

## How I would apply it (not run yet)

```
adb shell pm disable-user --user 0 <package>      # turn off
adb shell pm enable <package>                      # turn back on
adb shell pm list packages -d                      # see what is disabled
```

Safety steps:
1. Apply Tier A in small groups, rebooting between groups.
2. Check that the reverse camera, Bluetooth calls, audio and APK install still work after each group.
3. Compare free RAM and swap before and after (`/proc/meminfo`) to see if it helped.

## Open questions

1. Which Tier B items do you want?
2. Which Google Play services do you use: real, microG, or both?
3. Is the unit able to stay powered while I test? You said you have no charger.

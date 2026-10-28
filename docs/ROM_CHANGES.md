# What was changed on the unit, and how

All of this was done in place over adb (root), not by flashing an image. The stock partitions are backed up
(kept off GitHub), so every step can be reversed.

## 1. The "hardware write-protect" is a software switch

Writes to `/system` were refused (`[MSDC_WP] the wp partition ANDROID ... force fail`). That check is the
MediaTek storage driver's software write-protect, controlled from debugfs:

```
echo 0 > /sys/kernel/debug/mmc0/sw_wp_en     # protection off
mount -o remount,rw /system
... change files ...
sync; sync
echo 1 > /sys/kernel/debug/mmc0/sw_wp_en     # protection back on
```

Every change is then verified with `cmp` against the source file and again after a reboot. No recovery
package or kernel change is involved. Bootloader, kernel, recovery and MCU were not touched.

## 2. Changes

| Change | Result |
|---|---|
| 29 bloat apps deleted from `/system` and `/vendor` (list in `docs/DEBLOAT_LIST.md`, Tier A and B) | `/system` 98% to 60% used |
| `/system/bin/preinstall.sh` replaced by a trimmed version | no package-manager checks and installs at every boot |
| `userdata` wiped through the stock recovery (`--wipe_data` in `/cache/recovery/command`) | clean data; saved Wi-Fi is lost |
| Car Home (`rom/carhome`) added as a system app | new home screen; the first tile opens Headunit Revived |
| Headunit Revived 3.5.0 (official release, AGPL-3.0) added as a system app | wired and wireless Android Auto |
| Clock screen saver (`rom/clockdream`) | black screen with only time, day and date; opened by hand |
| Bluetooth Guard (`rom/btguard`) | switches the unit's Bluetooth off while Android Auto runs over Wi-Fi Direct |

## 3. Things learned the hard way

- **Never set `screen_off_timeout` to a short value remotely.** A 30 s test value left the unit asleep with
  Wi-Fi off, and switching the car off did not help because the unit suspends (quick boot) instead of
  rebooting. Restore with a touch and a power cycle.
- **Package cache.** Overwriting an APK inside an existing `/system/app/<name>/` does not refresh Android's
  cached manifest (the folder's timestamp does not change), so manifest changes (exported services,
  permissions) are ignored while the new code runs. After replacing an APK: `rm -rf /data/system/package_cache`
  and reboot. `dumpsys package <pkg> | grep versionName` shows what Android believes.
- **Background apps are force-stopped at car-off** by `com.jancar.services`. A stopped app gets no boot
  broadcast. Anything that must run (Bluetooth Guard) is started by the launcher, which is always relaunched.
- **Android 8 background start limits:** a boot receiver may not `startService`; use `startForegroundService`
  and call `startForeground` on every start command.
- **System apps are non-privileged** (`/system/app`, not `priv-app`) so a permission not in the privapp
  whitelist cannot cause a boot loop.

## 4. Android Auto

- **AiPlay (own receiver):** the whole chain works up to the TLS step. The phone then shows "Communication
  error 7 - your car's software didn't pass Android Auto security checks": a self-signed head-unit identity is
  rejected. See the AiPlay repo (`CHANGELOG.md`, `docs/PROTOCOL.md`).
- **Headunit Revived** passes the phone's check. USB works; wireless works in Native mode (Bluetooth wake-up,
  then a Wi-Fi Direct network the unit creates).
- **Music pausing:** with the phone also connected over Bluetooth, a navigation prompt pauses the music
  instead of lowering it. Bluetooth Guard switches the unit's Bluetooth off 20 s after the phone joins the
  Wi-Fi Direct network, and back on 15 s after it leaves.
- Revived auto-start: on boot, on screen on, on the phone's Bluetooth address; auto-connect to the last session.
  The apps are on the battery whitelist (`cmd deviceidle whitelist +<package>`).

## 5. Applying the ROM-side apps

`rom/<app>/build.sh <versionCode> <versionName>` builds each APK without Gradle (Android SDK build-tools, a
platform jar and a JDK; it creates its own debug keystore, which is not committed).
`rom/apply_when_connected.sh <unit-ip>` writes them into `/system` with the steps above. It does not reboot.
`tools/device/` holds two small helpers run with `app_process` on the unit (`Hotspot`, `BtTool`).

## 6. Not done / open

- The status-bar Home and Recents icons do nothing with Car Home (the system UI is the vendor's; not fixed).
- A flashable update package was never built.
- Not tested: a full car-off, car-on cycle with Bluetooth Guard and Revived auto-start; the music behaviour
  with a real navigation prompt.

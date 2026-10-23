# Reverse engineering the stock ROM (JANCAR / Autochips AC8227L head unit)

Static analysis of the firmware pulled from the unit on 2026-10-22/23. Nothing here was run on the unit, and nothing was changed on it.

**Method and limits**
- Files analysed: a verified partition backup (kernel, recovery, `system`, `vendor`, `jancar`, bootloader areas) and the unpacked `system`/`vendor`/`jancar` trees.
- Tools: Python (header parsing, a small device-tree parser), `strings`, `aapt2`, `apksigner`, `openssl`. The REA analysis server was **not** installed, so no decompiler or native disassembler was used. App behaviour below comes from manifests, strings and scripts, not from decompiled code.
- Labels: **Observed** = read directly from a file. **Inferred** = a reasonable conclusion from observations. **Unknown** = not established.
- The firmware images themselves are not in this repo (proprietary code, and they hold the unit's calibration data).

---

## 1. Identity

| Item | Value | Status |
|---|---|---|
| SoC | Autochips AC8227L (`mediatek,ac8227l`), 4x Cortex-A7, GPU Mali-450 | Observed (device tree, kernel config) |
| Build host/user | `jacxp` / `jac0416`, built 2022-02-25 | Observed |
| Kernel | Linux 3.18.79+, `PREEMPT`, `HZ=100`, ARM 32-bit | Observed |
| Build type | `userdebug`, `test-keys`, `ro.debuggable=1`, `ro.secure=0` | Observed |
| Android | `ro.build.version.sdk=27` (8.1). Fingerprint text says 6.0; Settings and the boot header say 10 | Observed; the real framework level is 27 |

The "Android 10.1" shown to the user is a label. The framework is API 27.

## 2. Boot chain

Observed layout (`by-name` map, all on the eMMC `mmcblk0`):

`preloader (boot0/boot1) -> LK (bootloader) -> kernel+ramdisk (BOOTIMG)`, with `RECOVERY`, `TEE1/2`, `SECCFG`, `NVRAM`, `PRO_INFO`, `METAZONE`, `ODMDTBO`, `ARM2` as MediaTek support partitions.

- `ANDROID` = `/system` (1.5 GB, ext4), `VENDOR` = `/vendor` (512 MB), `jancar` = `/jancar` (512 MB, vendor config/logo/MCU), `userdata` = `/data` (12 GB), `CACHE`.
- Both `BOOTIMG` and `RECOVERY` use the same kernel image (5.9 MB, with a MediaTek 512-byte header), boot header v0, command line only `buildvariant=userdebug`.
- The kernel carries its device tree appended after the zImage. Its `chosen/bootargs` is just `earlyprintk`.
- No dm-verity in use was observed on `system` (the kernel supports it; whether the fstab enables it is not established).

## 3. Kernel configuration (from the embedded `IKCFG`)

Observed: SELinux compiled in (`DEFAULT_SECURITY_SELINUX`), `CONFIG_ZRAM=y` with `LZ4K`, Android low-memory killer, `CPU_FREQ_DEFAULT_GOV_INTERACTIVE`, `HZ_100`, `PREEMPT`, loadable modules, Mali-450 GPU.

Memory settings in `init.rc`: `vm.overcommit_memory=1`, `min_free_order_shift=4`, `dirty_expire_centisecs=200`, `dirty_background_ratio=5`. A 1 GiB zram swap is created by `fstab.enableswap` (`zramsize=1073741824`).

## 4. RAM: the "4 GB" cannot be real

This section replaces an earlier, weaker version that relied on the device tree alone. A MediaTek-style bootloader normally rewrites the device tree's memory node, so the device tree is not enough evidence. The kernel configuration is.

- **Observed (kernel config):** `# CONFIG_ARM_LPAE is not set`, `# CONFIG_ARCH_PHYS_ADDR_T_64BIT is not set`, `CONFIG_PHYS_OFFSET=0x80000000`, `CONFIG_VMSPLIT_3G=y` (`PAGE_OFFSET=0xC0000000`), `CONFIG_HIGHMEM=y`.
- **Inferred (hard limit):** with 32-bit physical addresses and RAM starting at `0x80000000`, the kernel can address at most `0x80000000-0xFFFFFFFF`, which is **2 GiB**. It cannot use 4 GiB.
- **Observed (running unit):** `/proc/meminfo` reports `MemTotal: 4194304 kB` (exactly 4 GiB), `LowTotal: 3929088 kB` and `HighTotal: 265216 kB`. With a 3G/1G split and `VmallocTotal: 245760 kB`, a real low-memory zone of 3.75 GB cannot exist (it would be about 760 MB at most).
- **Conclusion:** the `/proc/meminfo` numbers are inconsistent with this kernel. The 4 GB figure is almost certainly a patched or spoofed display value. Physical RAM is at most 2 GiB. The device tree's 1 GiB (`/memory reg = <0x80000000 0x40000000>`) makes 1 GiB the most likely value.
- **Unknown:** where the spoof is applied (kernel patch or a framework hook), and whether the real size is 1 GiB or 2 GiB.
- **Confirmed on the running unit (2026-10-23):** `/proc/iomem` lists "System RAM" only at `0x80000000-0x8fffffff`, `0x92100000-0x944fffff` and `0x95000000-0xbfafffff`, and the bootloader-written device-tree memory node is `0x80000000 + 0x20000000` and `0xa0000000 + 0x1fc00000`. That adds up to about **1 GiB (about 975 MB usable)**. The 4 GB shown in Settings is false.
- **Where the spoof is (observed 2026-10-23):** only the `/proc/meminfo` file lies. The `sysinfo` syscall (used by `free` and by Android's `Process.getTotalMemory()`) reports **954,672 kB (932 MB)**, and `/proc/zoneinfo` shows Normal 172,364 + HighMem 66,304 managed pages (= 932 MB). So the framework and any app using `sysinfo`/`sysconf` already see the true size. Only apps that parse `/proc/meminfo` see 4 GB.
- **Why it matters:** apps that trust `MemTotal` (browsers, Play services, video apps) may size their caches for 4 GB and then thrash. The Android low-memory-killer thresholds themselves are **not** affected: for any RAM above about 700 MB the framework picks its maximum levels (observed `minfree` = 72/90/108/126/144/180 MB), the same for 1 GiB and 4 GiB. An earlier draft of this report said otherwise; that was wrong.
- **To settle the exact size (read-only, on the unit):** `cat /proc/iomem` (the "System RAM" lines), `dmesg | grep -iE "memory:|Memory:"`, `cat /sys/firmware/devicetree/base/memory/reg | xxd`.

## 5. Boot-time scripts and services (`init.jancar.rc`, `system/bin/*`)

Observed, in order of importance:

1. **Wi-Fi adb on every boot.** `on post-fs-data: setprop service.adb.tcp.port 5555`. This is why port 5555 was already open. `ro.adb.secure` is not set, and no authentication prompt appeared.
2. **SELinux set to permissive.** `on property:sys.boot_completed=1: setenforce 0`.
3. **World-writable hardware access.** `chmod 0777` on `/jancar`, `/dev/i2c-1`, `/dev/gpios_ioctl`, `/data/update`; `0666` on `/dev/ttyMT0`, `/dev/ttyMT3` (the serial links to the MCU), `/sys/.../mt_gpio`, `/sys/.../pwm_debug`.
4. **`preinstall.sh`** runs at every boot as root with the `shell` SELinux domain. It calls `pm list package` about ten times and installs any missing APK from `/vendor/preinstall` (Google Maps, YouTube, Tima CarNet, Baidu Input, dofun market, Ximalaya, QQ Music, two dash-cam apps), plus anything dropped in `/data/thrid-app/`. That is extra work and I/O at each boot. Only `Googlemap.apk` and `youtube.apk` are present in `/vendor/preinstall` in this image.
5. **`adb-carnet`** (a 520 KB static ARM binary) is started as a root service in the `adbd` SELinux domain (`service carnet`, `service.carnet.enable=1`). `timacarnet_init.sh` copies `/data/local/tmp/adb-carnet` to `/system/bin` and sets mode `6755`. That is a second, setuid-root adb binary.
6. **`sd-link.sh`**: starts the wireless-CarPlay stack (`sd_mdnsd`, then `sd_carplay -c jinganchi` in a `while true ... sleep 1` loop) **only if** `com.suding.speedplay` is installed. It is not in the unit's package list, so the loop does not run there.
7. **`deamon_service`** (16 KB, 32-bit ARM) runs as root in the `adbd` domain when `service.install.jancar.deamon=1`. Its purpose is not established. It is probably the helper that installs APKs into `/system` for the updater.
8. **`copy_cmd.sh`** (used by the updater) installs APKs, unzips their `.so` files into `/system/lib`, and can overwrite apps in `/system/priv-app`, `/vendor/app` and `bootanimation.zip`.

## 6. Root, signing and trust

- **Observed:** `/system/xbin/su` exists with mode `4750` (setuid, owner root, group `shell`). Ordinary apps cannot run it (`error=13`), but `adb shell` (group `shell`) can. This explains "su present but denied" in the earlier app diagnostic.
- **Observed:** `adbd` runs as root (`uid=0 context u:r:su:s0`), consistent with `ro.secure=0`.
- **Observed:** the OTA certificate `/system/etc/security/otacerts.zip` and the recovery key `/res/keys` are the **public Android test key** (`CN=Android, emailAddress=android@android.com`). The recovery verifies update packages against it.
- **Observed:** `framework-res.apk`, `ivi-services.apk` and `ivi-settings.apk` share one signer: certificate SHA-256 `c8a2e9bc...2ab8`, subject `CN=Android`. This is the published fingerprint of the AOSP **platform test key** (recalled from memory; confirm it against AOSP `build/make/target/product/security/platform.x509.pem`).
- **Inferred:** because those keys are public, anyone can sign an OTA package or a "system" app that this unit trusts. For a modified firmware that is useful: a repacked update can be signed with the public test key and the unit's recovery should accept it. It is also a security weakness (see section 9).

## 7. How the unit updates itself (the flashing path)

Strings in `ivi-settings.apk` and `ivi-services.apk` (observed) show a USB-stick based updater:

- Search roots: `/storage/udisk0`, `/storage/udisk1`, `/storage/udisk2`.
- `/AutoUpdate/` with subfolders `apk`, `config`, `copyfile`, `logo`, `parameter`. Files named: `config/iviconfig.zip`, `config/JacLcdConfig.bin`, `parameter/metazone.bin`, `config/default_wallpaper.{bmp,jpg,png}`. This updates apps, settings, logos and screen parameters.
- `/RecoveryUpdate/system/update.img` and `/RecoveryUpdate/update/update.img`, plus `update.zip`: the full-system update, run through Android `RecoverySystem` (recovery mode).
- Version gate: `pcb_version.txt`, `upgrade_package_version_is_too_low`, `system_upgrade_error_invalid_file`, `ERROR_INVALID_UPGRADE_PACKAGE`. A package with a lower version number is rejected.
- MCU: `/jancar/mcu/jacmcu.bin` (9.8 KB) is the MCU firmware, updated separately through the service (`McuUpgradeErrorMsg`).
- Recovery (`sbin/recovery`) verifies packages ("Verifying update package...") with the test key from section 6.

**Decompiled with jadx 1.5.6 (`ivi-services` `SystemUpgrade`, `ivi-settings` `UpdateSystemActivity` / `UpdateUtil`) - observed:**

- **Format:** `update.zip` / `update.img` is a standard **Android OTA zip**. The app calls `RecoverySystem.verifyPackage(file, ...)` (signature check against `/system/etc/security/otacerts.zip`, the public test key) and then `RecoverySystem.installPackage(...)`, which writes `/cache/recovery/command` and reboots into the stock recovery.
- **Normal path:** USB stick `<root>/update/update.zip` (or `update.img`) plus a one-line text file `<root>/update/hwversion.txt`. The text is only displayed as the "new version"; the Update button needs it to be **non-empty**. No comparison with the current version was found in this path. The package is first copied to `/data/update/update.img` (fallback `/data/update.img`) by `CopyFileActivity`, then verified and installed.
- **Recovery path:** if `<root>/RecoveryUpdate/system/update.img` exists, a dialog offers a recovery update, which installs `/udisk/RecoveryUpdate/update/update.img` (note the different folder name, a quirk of the vendor code).
- **`pcb_version.txt`** belongs to the hardware/MCU version code (`HardwareVersionUtils` in `ivi-services`), not to the system update. The `upgrade_package_version_is_too_low` string is not used by the system-update path.
- **Recovery:** stock Android 8.1-style recovery. It extracts `META-INF/com/google/android/update-binary` from the zip to `/tmp/update-binary` and runs it, and it can read `META-INF/com/android/metadata`. The ramdisk has no shell and no `/system/bin`, but it **does** contain `mke2fs_static` and `e2fsdroid_static` (ARM, static).
- **Inferred:** because `update-binary` is simply an executable run by recovery, a package can carry its own small static ARM `update-binary` (the Android NDK on this Mac can compile one) instead of the AOSP edify updater. A file-based update (mount `/system` read-write, delete/copy files, set modes and SELinux labels) then needs no ext4 image build.
- **Unknown:** whether this recovery enforces a downgrade/timestamp check on packages that carry no `metadata` file. It can be tested safely with a do-nothing package: the unit's own updater verifies the signature first, and a rejected package changes nothing.

## 8. Application architecture

- **Vendor UI/services (observed):** about 20 `ivi-*` / `com.jancar.*` apps run as system apps: launcher (`com.android.launcher3` is the JANCAR launcher), settings, systemui, `com.jancar.services` (hub for the MCU, CAN and upgrade), Bluetooth (`jancar.bluetooth`, `btservice`), radio, music, video, gallery, car cameras (`com.autochips.backcarapp`), CAN bus (`com.can.activity`), steering-wheel learning, a float menu, a file manager, and an autotest client.
- **Hardware link (observed):** the MCU is reached over `/dev/ttyMT0` and `/dev/ttyMT3`, opened to everyone (section 5).
- **Configuration (observed):** `/jancar/config/*.ini|json|xml` (launcher layout, settings, customer branding, touch calibration, CAN).
- **Overlays (observed):** the `OP01*` apps are MediaTek operator plug-ins (China Mobile), kept for the telephony and settings stack.
- **Bundled but not present as installed packages (observed):** the TXZ voice-assistant APKs (`com.txznet.txz`, 151 MB, in `/system/app`; `com.txznet.aipal`, `com.txznet.smartadapter` in `/vendor/app`) have `minSdk 19`, so they look installable on API 27, yet none appears in the unit's package list. **Unknown** why. Check with `adb shell pm path com.txznet.txz`.

## 9. Network endpoints and security observations

External hosts found in the vendor apps' strings (observed; whether each is contacted at runtime is **not established**):

| App | Hosts |
|---|---|
| `ivi-canbus` | `adsapi.dou8.autoai.com` (and a `test-dou8` host): an advertising API |
| `EasyConnection` (`net.easyconn`) | `apiota/sapiota/apirecord.carbit.com.cn`, Tencent Bugly (`android.bugly.qq.com`, `rqd.uu.qq.com`) |
| TXZ apps | `register.txzing.com`, `abroad-license.txzing.com`, `abroad.txzing.com`, `oss.txzing.com` |
| `timacarnet` (`com.tima.carnet.vt`) | `mg/mg2/rg/fg/pay.91carnet.com`, `*.timanetwork.cn` |
| `ivi-settings` | `www.xygala.com` |

All are plain `http://` strings. None were checked by traffic capture.

Security observations (observed unless marked):
1. **Unauthenticated root shell over Wi-Fi.** adb listens on TCP 5555 on every boot, runs as root, and no pairing prompt was needed. Anyone on the same network can get a root shell. (Inferred: this also applies to a public hotspot.)
2. **SELinux permissive** after boot, and **userdebug/test-keys** build.
3. **Public signing keys** for OTA and platform apps (section 6), so a malicious app or update signed with the public key would be trusted.
4. **World-writable hardware nodes**, including the MCU serial ports, so any app can talk to the MCU.
5. **setuid-root `adb-carnet`** (inferred privilege-escalation path for apps that can execute it; not tested).

Practical advice for the owner: do not leave the unit on shared or public Wi-Fi, and consider turning Wi-Fi off, or disabling the adb port, when you are not using it.

## 10. What starts at boot (the lag map)

All figures from manifests and init files (observed). Process memory is from the unit's `dumpsys meminfo` snapshot taken earlier (PSS = memory the process actually uses).

**10.1 Always-on system apps (`persistent=true`, never killed by the memory manager):** `com.android.launcher3` (JANCAR launcher), `com.android.systemui`, `com.android.phone`, `com.jancar.services`, `com.jancar.settings`, `com.jancar.bluetooth`, `com.jancar.btservice`, `com.jancar.radio`, `com.jancar.floatmenu`, `com.can.activity`, `com.autochips.backcarapp`, `com.autochips.quickbootmanager`, `com.txznet.smartadapter` (listed in the APK only; see section 8).

**10.2 Boot-time broadcast receivers that wake large apps** (observed):

| App | Triggers | Manifest services | Note |
|---|---|---|---|
| `com.google.android.gms` (Play services) | boot, connectivity, user-present, package-added | 351 | 85 MB PSS when running |
| `com.google.android.googlequicksearchbox` (Google app) | boot, connectivity, package-added | 120 | 92 MB + 22 MB PSS in two processes |
| `com.android.vending` (Play Store) | boot, connectivity, package-added | 79 | 71 MB PSS |
| `com.android.chrome` | user-present | 81 | |
| `com.mediatek.mtklogger` | boot | 3 | writes logs to the worn eMMC |
| `com.google.android.syncadapters.calendar` / `.contacts` | boot | 4 / 5 | no calendar or contacts account on a car unit |
| `com.adobe.reader` | connectivity | 2 | |
| `com.txznet.txz`, `com.txznet.smartadapter`, `com.suding.speedplay` | boot | 6 / 2 / 4 | only if installed |

**10.3 Native daemons.** The init files define 142 services, of which 72 start automatically (32 `main`, 21 `hal`, 13 `core`, 5 `late_start`, 1 `early_hal`). Most are required (graphics, audio, Bluetooth, Wi-Fi, storage, back-camera HAL, NVRAM, power). These are the ones that look unnecessary or costly on a car unit; each is a *candidate*, to be checked with `ps` on the unit before removal:

| Service | Why it is a candidate |
|---|---|
| `mobile_log_d` (root) | MediaTek always-on logger; writes to flash |
| `storaged` (root) | disk-usage statistics; periodic writes |
| `perfprofd` (root) | performance profiling daemon |
| `netdiag`, `DMAgent` | network diagnostics / device-management agent |
| `pppd_btdun` | Bluetooth dial-up networking |
| `terservice` | MediaTek telephony helper (no SIM on this unit) |

Native daemons use little RAM compared with the apps in 10.2, so the gain from trimming them is small. The big levers are the app list and the memory configuration in 10.4.

**10.4 Memory configuration (observed).** `dalvik.vm.heapsize=256m`, `heapgrowthlimit=128m` (generous for a 1 GiB device at 1024x600), no `ro.config.low_ram`, 1 GiB zram on a device with at most 2 GiB of RAM, LMK thresholds left to the framework (which is told RAM is 4 GB; see section 4).

**10.5 Other (observed).** `preinstall.sh` runs on every boot (section 5); `HZ=100`; the CPU is capped at 1.12 GHz.

## 11. Dependency findings (what is safe to remove)

I searched the dex strings and manifests of the other system apps for each candidate package name (shared SDK name tables make some counts high; a count of 20+ means a name table, not a hard dependency).

- **No other app references them (safest):** `com.google.android.feedback`, `com.mediatek.calendarimporter`, `com.android.wallpaperpicker`, `com.android.wallpapercropper`, `com.google.android.tts`, `com.android.quicksearchbox`.
- **Referenced only by their own package or their setup partner:** `partnersetup`, `gms.setup`, `configupdater`, `backuptransport`, `syncadapters.calendar`, `managedprovisioning`, `storagemanager`.
- **Referenced by the launcher or settings UI (removal leaves a dead tile or settings entry, not a crash, as far as the strings show):** `com.jancar.steeringwheelkeys` (launcher, settings, wheel-key study), `com.android.soundrecorder` (launcher, music, video, gallery), `net.easyconn`, `com.tima.carnet.vt`, `com.android.chrome` (launcher), TXZ packages (settings, canbus).
- **Do not touch:** `com.android.vending` and `com.google.android.gms` (about 26-31 references each), `com.android.bluetooth`, `com.jancar.services`, `com.android.documentsui` (media apps use it), `com.android.providers.*`.

The launcher layout and many features are controlled by editable files in `/jancar/config` (`ivi-launcher.json`, `ivi-config.ini`, `ivi-customer.ini`, `ivi-settings.ini`), for example theme lists, storage paths, camera indexes, Bluetooth name, default language. A ROM can change behaviour by editing these, with no code changes.

## 12. Filesystem facts needed to rebuild the images

Read from the ext4 superblocks (observed):

| Image | Size | Block size | Inode size | Inodes | Compat / incompat / ro-compat features |
|---|---|---|---|---|---|
| `system` (`ANDROID`) | 1.5 GiB (393216 blocks) | 4096 | 256 | 98304 | `0x1c` (journal, ext_attr, resize_inode) / `0x42` (filetype, extents) / `0x13` (sparse_super, large_file, gdt_csum) |
| `vendor` | 512 MiB | 4096 | 256 | 32768 | same |
| `jancar` | 512 MiB | 4096 | 256 | 32768 | same (+ needs journal recovery) |

- `system` is only ~36 MB free (9357 free blocks of 393216).
- File modes, owners and SELinux labels for every file are recorded in the local manifests (`system.manifest.json`, `vendor.manifest.json`). A rebuilt image must keep them (file contexts such as `u:object_r:system_file:s0`, and setuid on `su`).
- **Tooling gap:** this Mac has no `mke2fs`/`make_ext4fs`, and the Homebrew route compiles dependencies from source for hours. A Linux environment (VM or container) is the practical place to build the image.

## 13. ROM design: what a smoother ROM would change

Ranked by expected benefit, with the risk. These are proposals, not yet built or tested.

| # | Change | Where | Expected effect | Risk |
|---|---|---|---|---|
| 1 | Set `ro.config.low_ram`-style tuning and lower `dalvik.vm.heapgrowthlimit` for the real 932 MB | `build.prop` | Fewer cached processes and smaller app heaps. The spoof itself (only in `/proc/meminfo`) matters little, because the framework already sees the real size | Low-medium |
| 2 | Remove the Google app (Velvet), Acrobat, loggers, calendar/contacts sync, setup wizards, feedback, wallpaper pickers | `/system`, `/vendor` | Frees ~400-500 MB of `/system`; stops ~250 MB of RAM from being used by boot-time apps | Low |
| 3 | Remove TXZ (236 MB) | `/system/app`, `/vendor/app` | Frees the most `/system` space | Low (not installed on the unit); the settings app references it |
| 4 | Reduce zram from 1 GiB to about 25-50% of real RAM | `fstab.enableswap` in the boot image | Less memory spent on compressed swap | Medium: needs a boot image change |
| 5 | Replace `preinstall.sh` with a one-time version that does nothing once the apps exist | `/system/bin/preinstall.sh` | Less work at each boot | Low |
| 6 | Trim resident daemons (10.3) | `/system/etc/init/*.rc` | Small | Low-medium |
| 7 | Close the security holes: remove the permanent TCP 5555 adb, restore SELinux enforcing, re-key (if the update path allows) | `init.jancar.rc` | No speed gain; safer | Medium: SELinux enforcing may break vendor apps |
| 8 | Kernel (`HZ`, governor, config) | boot image | Unproven | **High (not recommended)** |

Leave the bootloader, preloader, TEE, NVRAM and the MCU firmware alone.

**Flash package design (proposed, nothing built yet):**

1. **Safety net first.** Build `restore-stock.zip`: a signed OTA whose small static `update-binary` writes the saved `ANDROID.img` back to `by-name/ANDROID` (and verifies its checksum). Test its signature on the unit (the unit's own updater verifies before it installs). This is the way back, and it needs no ext4 build.
2. **Do-nothing test.** A signed zip whose `update-binary` only prints a message and exits, to prove the full chain (USB, copy, verify, recovery, run) on this unit with no change to the system.
3. **File-based ROM package.** `update-binary` mounts `/system` read-write, deletes the removed apps, copies replaced files (for example a trimmed `preinstall.sh`, `build.prop`), applies modes and SELinux labels from the manifest, syncs and unmounts. Only `/system` (and `/vendor` if needed) is touched. The bootloader, kernel, recovery and MCU stay untouched.
4. **Signing:** whole-file OTA signature with the public AOSP test key (SHA-1, RSA-2048, the key type the recovery's `/res/keys` holds). The private test key is published in the AOSP source tree; I would verify that it matches the unit's certificate before using it.
5. **Order of risk:** steps 1 and 2 change nothing on the unit; step 3 changes only files that can be restored by step 1.

**Build and test path:** (a) apply live over adb first (reversible), measure free memory and swap; (b) apply the same file edits in place over adb root (`mount -o remount,rw /system`, no flashing) once the live results are good; (c) only then wrap them in the signed package above, with the stock backup as the way back.

## 14. Live measurements from the unit (2026-10-23, before any change)

- Memory: `MemFree` about 37 MB, `MemAvailable` about 416 MB, zram swap 185 MB used of 1 GiB.
- The load average of about 12 is **not** real CPU load. About a dozen vendor kernel threads (touch panel, PWM, hang detector, entropy, display capture) sit permanently in uninterruptible sleep, which the load average counts. A 10-second idle sample showed 92% idle CPU.
- Since boot (1.5 days): about 45 GB read from the eMMC (`pgpgin`), 3.2 GB swapped in and 4.7 GB swapped out, about 9.7 million working-set refaults (code pages evicted and re-read), 8,282 direct-reclaim stalls. Disk writes average about 52 ms each (`/proc/diskstats`). That is thrashing on a 1 GiB device with slow, worn flash.
- Largest resident processes: Google Play services (about 370 MB across 3 processes), Play Store (about 370 MB across 4 processes), Chrome, Google text-to-speech (75 MB), `com.jancar.settings` (63 MB).

## 15. Open questions and read-only checks to run on the unit

| Question | Command (read-only) |
|---|---|
| Real RAM size | `cat /proc/iomem`; `dmesg \| grep -i "Memory:"`; `cat /sys/firmware/devicetree/base/memory/reg \| xxd` |
| Where is the 4 GB faked? | Located: only `/proc/meminfo` (see section 4). The exact kernel patch is not needed for the ROM |
| Is TXZ installed? | `pm path com.txznet.txz` |
| Is dm-verity on? | `getprop \| grep -i verity`; `grep system /proc/mounts` (mounted straight from `by-name/ANDROID`, so verity looks unused) |
| Which daemons actually run? | `ps -A` and compare with the 10.3 list |
| Does recovery enforce a downgrade check? | send a signed do-nothing package through the unit's updater (see section 7) |
| Are the vendor hosts contacted? | `cat /proc/net/tcp` / traffic capture while the unit is on Wi-Fi |

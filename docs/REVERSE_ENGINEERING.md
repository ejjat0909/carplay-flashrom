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
- **Why it matters:** Android sizes its low-memory-killer thresholds and cached-process limits from the RAM figure it is told. If it believes it has 4 GB, those limits are wrong for a 1 GiB device, so apps stay cached too long and the unit swaps heavily. A ROM that reports the true RAM size, plus `ro.config.low_ram`-style tuning, is the single most promising speed fix.
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

**Unknown:** the exact internal format of `update.img` (a recovery OTA zip, or a MediaTek scatter-style image) and the exact `pcb_version.txt` format. These must be established before building a flashable package.

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
| 1 | Report the real RAM size and set `ro.config.low_ram`-style tuning, lower `dalvik.vm.heapgrowthlimit` | the spoof location (unknown) + `build.prop` | Correct LMK thresholds and fewer cached apps; probably the largest gain | Medium: first find where the 4 GB is faked |
| 2 | Remove the Google app (Velvet), Acrobat, loggers, calendar/contacts sync, setup wizards, feedback, wallpaper pickers | `/system`, `/vendor` | Frees ~400-500 MB of `/system`; stops ~250 MB of RAM from being used by boot-time apps | Low |
| 3 | Remove TXZ (236 MB) | `/system/app`, `/vendor/app` | Frees the most `/system` space | Low (not installed on the unit); the settings app references it |
| 4 | Reduce zram from 1 GiB to about 25-50% of real RAM | `fstab.enableswap` in the boot image | Less memory spent on compressed swap | Medium: needs a boot image change |
| 5 | Replace `preinstall.sh` with a one-time version that does nothing once the apps exist | `/system/bin/preinstall.sh` | Less work at each boot | Low |
| 6 | Trim resident daemons (10.3) | `/system/etc/init/*.rc` | Small | Low-medium |
| 7 | Close the security holes: remove the permanent TCP 5555 adb, restore SELinux enforcing, re-key (if the update path allows) | `init.jancar.rc` | No speed gain; safer | Medium: SELinux enforcing may break vendor apps |
| 8 | Kernel (`HZ`, governor, config) | boot image | Unproven | **High (not recommended)** |

Leave the bootloader, preloader, TEE, NVRAM and the MCU firmware alone.

**Build and test path:** (a) apply 2 live over adb first (reversible), measure free memory and swap; (b) apply 1 and 5 live by editing `/system` in place over adb root (`mount -o remount,rw /system`, no flashing); (c) only when stable, build a `system` image in a Linux environment, sign the update with the public test key, and flash it from USB (section 7), with the verified stock backup as the way back.

## 14. Open questions and read-only checks to run on the unit

| Question | Command (read-only) |
|---|---|
| Real RAM size | `cat /proc/iomem`; `dmesg \| grep -i "Memory:"`; `cat /sys/firmware/devicetree/base/memory/reg \| xxd` |
| Where is the 4 GB faked? | compare `/proc/meminfo` with `/proc/iomem`; search `framework.jar` and `libc.so` for a `MemTotal` or `sysinfo` hook |
| Is TXZ installed? | `pm path com.txznet.txz` |
| Is dm-verity on? | `getprop \| grep -i verity`; `grep system /proc/mounts` (mounted straight from `by-name/ANDROID`, so verity looks unused) |
| Which daemons actually run? | `ps -A` and compare with the 10.3 list |
| What does `update.img` contain? | only test on the unit's own updater after a verified backup |
| Are the vendor hosts contacted? | `cat /proc/net/tcp` / traffic capture while the unit is on Wi-Fi |

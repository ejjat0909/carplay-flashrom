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

## 4. RAM: the device tree says 1 GiB, not 4 GiB

- **Observed:** the kernel's own device tree declares `/memory reg = <0x80000000 0x40000000>`, which is **1 GiB** at `0x80000000`. Its reserved regions (framebuffer, `arm2*`, `metazone`, camera buffers) all sit between `0x90000000` and `0x95000000`, inside that 1 GiB window.
- **Observed:** the running unit reports `MemTotal: 4194304 kB` (exactly 4 GiB), with `HighTotal 265216 kB`. The round number does not match the device tree.
- **Inferred:** the 4 GB figure is probably not the physical RAM, so the real memory is probably 1 GiB (or a bit more). This matches the symptoms: about 39 MB free, 1 GB swap partly used, 24 GB committed memory.
- **Unknown:** the bootloader (LK) can patch the memory node at boot, and the strings in LK and the preloader do not settle it.
- **How to settle it on the unit (read-only):** `adb shell cat /sys/firmware/devicetree/base/memory/reg | xxd`, `adb shell cat /proc/iomem`, `adb shell dmesg | grep -iE "memory|ram"`.

If the RAM really is about 1 GiB, a 1 GiB zram (compressed swap) competes with apps for the same memory. That is a plausible lag source (see section 10).

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

## 10. What this means for the lag (hypotheses)

1. **Memory pressure, probably from only ~1 GiB of real RAM** (section 4), with a 1 GiB zram on top. Heavy background apps (Google app, Play services, Telegram, a second keyboard) then force swapping.
2. **Worn, nearly full storage.** `/system` is 98% full, and the eMMC is 70-80% worn (see `device-info/`).
3. **Boot cost.** `preinstall.sh` plus about 20 vendor apps starting at boot.
4. **Kernel tuning.** `HZ=100` gives coarser timer ticks. The `interactive` governor is used, but the CPU is capped at 1.12 GHz.
5. **Preloaded user apps duplicated in `/data`** (real GMS and microG, Chrome, two Google apps).

These are hypotheses, not measurements. Phase 6 of the plan (disable apps live and compare memory) is the test.

## 11. Implications for the modified-ROM plan

- A repacked `system` image can be built from the unpacked tree. File modes and SELinux labels are recorded in `system.manifest.json` and `vendor.manifest.json` (kept locally).
- Signing: a modified update can use the public test key (section 6), and replaced system apps keep the same platform signature.
- The update path is a USB stick with `RecoveryUpdate/...` or `AutoUpdate/...` (section 7), gated by a version number that must not go down.
- The MCU firmware (`jacmcu.bin`) is separate and should be left untouched.
- Biggest safe wins: remove the unused apps from `/system` to free space, drop the boot-time work in `preinstall.sh`, and reduce the zram size or tune swap. Whether to touch `HZ`, the kernel or the bootloader is **not** recommended (high brick risk, no benefit proven).

## 12. Open questions and read-only checks to run on the unit

| Question | Command (read-only) |
|---|---|
| Real RAM | `cat /sys/firmware/devicetree/base/memory/reg \| xxd`; `cat /proc/iomem`; `dmesg \| grep -i memory` |
| Is TXZ installed? | `pm path com.txznet.txz` |
| Is dm-verity on? | `getprop \| grep -i verity`; `cat /proc/mounts \| grep system` |
| Which services use CPU/IO | `top -b -n 1`, `cat /proc/pressure/*` (if present), `dumpsys meminfo` |
| Network use | `cat /proc/net/tcp`, `dumpsys connectivity` |
| `update.img` format | try a package on the unit's own updater only after a verified backup |

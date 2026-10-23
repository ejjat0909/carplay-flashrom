# Plan and status

## Goals
1. Reduce lag on the car unit.
2. Keep APK installing working.
3. Deliver it as something flashable from USB/SD, with a way back to stock.

## What is and is not possible
- **Not possible:** a LineageOS-style custom ROM. There is no public source, device tree or driver set for this board.
- **Possible:** a modified copy of the unit's own stock firmware (remove bloat, trim services, tune settings). Expect a noticeable but limited gain, because the CPU is old.
- **Testing:** a VM can check that image files are valid. It cannot test the hardware. The only real test is the unit.

## Findings so far
- `/system` is 98% full and `/vendor` 95%. The eMMC is 70-80% worn.
- At the time of checking, free RAM was 39 MB, with 365 MB of swap used, while the CPU was mostly idle. The lag points to memory pressure and slow storage, not the CPU.
- `adb` runs as root, and `ro.secure=0`. The `su` binary is not usable by apps.
- Largest items in `/system`: TXZSmartWakeUp 236 MB (appears not to be installed), Google app (Velvet) 134 MB, Chrome 65 MB, Play Store 57 MB.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | Get adb working (USB was not detected, Wi-Fi adb was already open on 5555) | Done |
| 2 | Read-only check of the unit (`tools/check.sh`) | Done |
| 3 | Full backup of all partitions except `userdata` and `cache`, checksum-verified | Done (25 of 25 OK) |
| 4 | Unpack system, vendor and jancar images on the Mac (`tools/extract.py`) | Done |
| 5 | Build the debloat list (`docs/DEBLOAT_LIST.md`) | Done, approved by the owner |
| 6 | Apply the list live with `pm disable-user` (reversible), in two groups, with checks in between | **Next**, waiting for the unit to be online |
| 7 | Measure free RAM and swap before and after | After phase 6 |
| 8 | If it helps: repack the system image without the removed apps (frees `/system`), keep APK install enabled | Planned |
| 9 | Flash the repacked image through the unit's USB/SD update, with the stock images as the way back | Planned, needs the unit's recovery method confirmed |

## Checks after each group in phase 6
Reverse camera, Bluetooth calls, audio, and installing an APK.

## Risks
- A bad flash can brick the unit. The stock images (kept off GitHub) are the only way back, so keep a second copy.
- Disabling microG breaks ReVanced YouTube/YouTube Music. It is reversible with `pm enable`.
- The eMMC is worn: avoid heavy writes and large installs.

## Open questions
- Exact USB/SD update method of the unit.
- Which Google Play services the owner really uses (real, microG, or both).

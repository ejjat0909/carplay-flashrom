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
| 1 | Get adb working (Wi-Fi adb on 5555 was already open) | Done |
| 2 | Read-only check of the unit (`tools/check.sh`) | Done |
| 3 | Full partition backup, checksum-verified | Done (25 of 25 OK) |
| 4 | Unpack system, vendor and jancar images (`tools/extract.py`) | Done |
| 5 | Debloat list (`docs/DEBLOAT_LIST.md`) | Done, approved |
| 6 | Apply it live with `pm disable-user` | Done (29 apps), then made permanent in phase 8 |
| 7 | Measure memory before and after | Partly: free memory 37 MB to about 79 MB after phase 6 |
| 8 | Remove the apps from `/system` in place (software write-protect switched off, see `docs/ROM_CHANGES.md`) | Done; `/system` 98% to 60% |
| 9 | Flashable update package | Not built. In-place editing was used instead |
| 10 | New launcher, Android Auto (Headunit Revived), clock screen saver, Bluetooth Guard | Done; see `docs/ROM_CHANGES.md` |

## Risks
- A bad flash can brick the unit. The stock images (kept off GitHub) are the only way back, so keep a second copy.
- microG and ReVanced YouTube were removed with the data wipe.
- The eMMC is worn: avoid heavy writes and large installs.

## Open questions
- Full car-off/car-on test of the automatic Android Auto start (`docs/ROM_CHANGES.md`, section 6).
- Status-bar Home and Recents icons with the new launcher.

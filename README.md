# carplay-flashrom

Speeding up a laggy Android car head unit (JANCAR-style, Autochips AC8227L), and the tools used to study it.

Goal: make the unit less laggy and keep APK installing working. A from-scratch custom ROM is not possible on this chip (no public source or drivers), so the plan is a debloated, tuned version of the unit's **own** stock firmware.

- [`docs/PLAN.md`](docs/PLAN.md): goals, phases and current status
- [`docs/REVERSE_ENGINEERING.md`](docs/REVERSE_ENGINEERING.md): what the stock firmware contains and how it works
- [`docs/DEBLOAT_LIST.md`](docs/DEBLOAT_LIST.md): the list of apps to switch off
- [`device-info/`](device-info): read-only facts collected from the unit (serial number removed)
- [`tools/`](tools): scripts for the read-only check, partition backup and image extraction
- [`dev-launcher/`](dev-launcher): small Android app that opens hidden Settings screens (Developer options, Device info, Wi-Fi)

Not in this repo on purpose: the firmware images and unpacked ROM (proprietary code, and the images hold the unit's own calibration data), and the signing key.

## The unit

| | |
|---|---|
| Chip | Autochips AC8227L, 4x Cortex-A7, 1.12 GHz, 32-bit |
| Android | 8.1 (API 27), shown as "10.1" in Settings |
| Kernel | Linux 3.18.79 |
| Storage | eMMC about 15 GB, 70-80% of life used |
| RAM | reported as 4 GB (not proven) |
| adb | root over Wi-Fi on port 5555, already enabled |

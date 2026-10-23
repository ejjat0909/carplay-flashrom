#!/bin/bash
# Read-only backup of every partition except userdata/cache. Nothing is written to the unit.
cd ~/Documents/carplay/backup || exit 1
: > SHA.txt
while read -r name part; do
  f="$name.img"
  adb exec-out "dd if=$part bs=1048576 2>/dev/null" > "$f" </dev/null
  dev=$(adb shell "md5sum $part" </dev/null | awk '{print $1}')
  loc=$(md5 -q "$f")
  [ "$dev" = "$loc" ] && st=OK || st=MISMATCH
  echo "$st $loc $f $(stat -f %z "$f")" | tee -a SHA.txt
done <<'LIST'
PRO_INFO /dev/block/mmcblk0p1
NVRAM /dev/block/mmcblk0p2
PROTECT_F /dev/block/mmcblk0p3
PROTECT_S /dev/block/mmcblk0p4
SECCFG /dev/block/mmcblk0p5
LK /dev/block/mmcblk0p6
TEE1 /dev/block/mmcblk0p7
TEE2 /dev/block/mmcblk0p8
ARM2 /dev/block/mmcblk0p9
BOOTIMG /dev/block/mmcblk0p10
RECOVERY /dev/block/mmcblk0p11
SEC_RO /dev/block/mmcblk0p12
MISC /dev/block/mmcblk0p13
LOGO /dev/block/mmcblk0p14
METAZONE /dev/block/mmcblk0p15
ODMDTBO /dev/block/mmcblk0p16
EXPDB /dev/block/mmcblk0p18
KB /dev/block/mmcblk0p19
DKB /dev/block/mmcblk0p20
FRP /dev/block/mmcblk0p21
VENDOR /dev/block/mmcblk0p17
jancar /dev/block/mmcblk0p22
ANDROID /dev/block/mmcblk0p23
mmcblk0boot0 /dev/block/mmcblk0boot0
mmcblk0boot1 /dev/block/mmcblk0boot1
LIST
echo ALLDONE | tee -a SHA.txt

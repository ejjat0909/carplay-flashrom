#!/bin/bash
# Run when the unit is on the same network. Usage: ./apply_when_connected.sh <unit-ip>
set -u
IP=${1:?usage: apply_when_connected.sh <unit-ip>}; A="adb -s $IP:5555"
cd ~/Documents/carplay
adb connect $IP:5555 | tail -1
$A shell echo up </dev/null | grep -q up || { echo "unit not reachable"; exit 1; }

echo "== 1. screen settings (read first)"
T=$($A shell settings get system screen_off_timeout </dev/null | tr -d '\r')
C=$($A shell settings get secure screensaver_components </dev/null | tr -d '\r')
echo "timeout=$T  screensaver=$C"
if [ "$T" = "30000" ]; then
  $A shell settings put system screen_off_timeout 1879048191 </dev/null
  echo "restored timeout to 1879048191 (my 30 s test value was still set)"
fi

echo "== 2. bake CarHome v4 and ClockDream into /system"
$A push carhome/CarHome_v4.apk /data/local/tmp/CarHome.apk >/dev/null
$A push clockdream/ClockDream_v1.apk /data/local/tmp/ClockDream.apk >/dev/null
$A push btguard/BtGuard_v1.apk /data/local/tmp/BtGuard.apk >/dev/null
$A shell '
echo 0 > /sys/kernel/debug/mmc0/sw_wp_en; mount -o remount,rw /system
cat /data/local/tmp/CarHome.apk > /system/priv-app/CarHome/CarHome.apk
mkdir -p /system/app/ClockDream
cat /data/local/tmp/ClockDream.apk > /system/app/ClockDream/ClockDream.apk
mkdir -p /system/app/BtGuard
cat /data/local/tmp/BtGuard.apk > /system/app/BtGuard/BtGuard.apk
chmod 755 /system/app/BtGuard; chmod 644 /system/app/BtGuard/BtGuard.apk; chown root:root /system/app/BtGuard /system/app/BtGuard/BtGuard.apk; chcon u:object_r:system_file:s0 /system/app/BtGuard /system/app/BtGuard/BtGuard.apk
chmod 755 /system/app/ClockDream; chmod 644 /system/app/ClockDream/ClockDream.apk /system/priv-app/CarHome/CarHome.apk
chown root:root /system/app/ClockDream /system/app/ClockDream/ClockDream.apk /system/priv-app/CarHome/CarHome.apk
chcon u:object_r:system_file:s0 /system/app/ClockDream /system/app/ClockDream/ClockDream.apk /system/priv-app/CarHome/CarHome.apk
sync; sync
cmp /data/local/tmp/CarHome.apk /system/priv-app/CarHome/CarHome.apk && echo "CarHome VERIFIED"
cmp /data/local/tmp/ClockDream.apk /system/app/ClockDream/ClockDream.apk && echo "ClockDream VERIFIED"
cmp /data/local/tmp/BtGuard.apk /system/app/BtGuard/BtGuard.apk && echo "BtGuard VERIFIED"
echo 1 > /sys/kernel/debug/mmc0/sw_wp_en
dmesg | grep -iE "EXT4-fs (error|warning)|I/O error|MSDC_WP" | tail -3
rm /data/local/tmp/CarHome.apk /data/local/tmp/ClockDream.apk /data/local/tmp/BtGuard.apk' </dev/null
echo "== done. Reboot the unit yourself when convenient; nothing else was changed."

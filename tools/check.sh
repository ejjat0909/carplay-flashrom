
# Read-only checks. Changes nothing on the car unit.
adb devices
adb shell getprop > getprop.txt
adb shell cat /proc/meminfo > meminfo.txt
adb shell cat /proc/cpuinfo > cpuinfo.txt
adb shell cat /proc/version > version.txt
adb shell top -n 1 -m 25 > top.txt
adb shell df > df.txt
adb shell cat /proc/mounts > mounts.txt
adb shell cat /proc/partitions > partitions.txt
adb shell ls -l /dev/block/platform > blockplatform.txt
adb shell pm list packages -f > packages.txt
adb shell dumpsys meminfo > dumpsys_meminfo.txt
adb shell cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq > freq.txt
adb shell cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq >> freq.txt
adb shell id > id.txt
adb root >> id.txt
echo Done

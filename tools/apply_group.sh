#!/bin/bash
# Disable the given packages for user 0 (reversible with: adb shell pm enable <pkg>). Skips packages that are not installed.
for p in "$@"; do
  if adb shell "pm list packages $p" </dev/null | tr -d '\r' | grep -qx "package:$p"; then
    echo "$(date +%T) $p: $(adb shell "pm disable-user --user 0 $p" </dev/null | tr -d '\r')"
  else
    echo "$(date +%T) $p: not installed, skipped"
  fi
done

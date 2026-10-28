#!/bin/bash
# Re-enable every package disabled by apply_group.sh (Tier A + Tier B).
for p in com.google.android.googlequicksearchbox com.adobe.reader com.mediatek.mtklogger com.mediatek.mtklogger.proxy com.jan.autotest com.google.android.feedback com.google.android.onetimeinitializer com.google.android.partnersetup com.google.android.gms.setup com.google.android.configupdater com.google.android.backuptransport com.google.android.syncadapters.calendar com.mediatek.calendarimporter com.android.providers.calendar com.android.sharedstoragebackup com.android.wallpaperbackup com.android.chrome com.touchtype.swiftkey org.telegram.messenger com.google.android.syncadapters.contacts com.android.wallpaper.livepicker com.android.wallpapercropper com.android.wallpaperpicker com.mediatek.engineermode com.jancar.manual app.revanced.android.gms com.can.activity com.jancar.steeringwheelkeys com.jancar.radio; do
  echo "$p: $(adb shell "pm enable $p" </dev/null | tr -d '\r')"
done

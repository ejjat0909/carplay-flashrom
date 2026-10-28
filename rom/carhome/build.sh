#!/bin/bash
# Builds and signs CarHome.apk without Gradle. Raise VERSION_CODE on every rebuild.
set -e
cd "$(dirname "$0")"
BT=~/Library/Android/sdk/build-tools/36.1.0
PL=~/Library/Android/sdk/platforms/android-34/android.jar
VC=${1:-1}; VN=${2:-1.0}
rm -rf build; mkdir -p build/classes build/dex
$BT/aapt2 link -o build/base.apk -I $PL --manifest AndroidManifest.xml --min-sdk-version 19 --target-sdk-version 27 --version-code $VC --version-name $VN
javac --release 8 -cp $PL -d build/classes src/com/cloone/carhome/MainActivity.java 2>&1 | grep -v "^Note:" || true
$BT/d8 --min-api 19 --lib $PL --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/app.apk && (cd build/dex && zip -q ../app.apk classes.dex)
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias dbg -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=CarHome" 2>/dev/null
$BT/zipalign -f 4 build/app.apk build/aligned.apk
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --out CarHome_v$VC.apk build/aligned.apk 2>&1 | grep -v WARNING || true
ls -l CarHome_v$VC.apk

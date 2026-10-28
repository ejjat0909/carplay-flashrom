#!/bin/bash
set -e
cd "$(dirname "$0")"
BT=~/Library/Android/sdk/build-tools/36.1.0
PL=~/Library/Android/sdk/platforms/android-34/android.jar
VC=${1:-1}; VN=${2:-1.0}
rm -rf build; mkdir -p build/classes build/dex
$BT/aapt2 link -o build/base.apk -I $PL --manifest AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 27 --version-code $VC --version-name $VN
javac --release 8 -cp $PL -d build/classes src/com/cloone/btguard/*.java 2>&1 | grep -v -E "^(Note:|warning|[0-9]+ warning)" || true
$BT/d8 --min-api 24 --lib $PL --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/app.apk && (cd build/dex && zip -q ../app.apk classes.dex)
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias dbg -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=BtGuard" 2>/dev/null
$BT/zipalign -f 4 build/app.apk build/aligned.apk
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --out BtGuard_v$VC.apk build/aligned.apk 2>&1 | grep -v -E "WARNING|^$" || true
ls -l BtGuard_v$VC.apk

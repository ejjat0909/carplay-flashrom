#!/bin/bash
# Builds and signs DevLauncher.apk without Gradle. Needs the Android SDK build-tools, a platform jar and a JDK.
# Raise VERSION_CODE every time, or the head unit will keep the old copy.
set -e
BT=~/Library/Android/sdk/build-tools/36.1.0
PL=~/Library/Android/sdk/platforms/android-34/android.jar
VERSION_CODE=${1:-3}; VERSION_NAME=${2:-1.2}
mkdir -p build/classes build/dex
$BT/aapt2 link -o build/base.apk -I $PL --manifest AndroidManifest.xml \
  --min-sdk-version 19 --target-sdk-version 27 --version-code $VERSION_CODE --version-name $VERSION_NAME
javac --release 8 -cp $PL -d build/classes src/com/example/devlauncher/MainActivity.java
$BT/d8 --min-api 19 --lib $PL --output build/dex build/classes/com/example/devlauncher/*.class
cp build/base.apk build/app.apk && (cd build/dex && zip -q ../app.apk classes.dex)
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias dbg -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Debug"
$BT/zipalign -f 4 build/app.apk build/aligned.apk
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --out DevLauncher_v$VERSION_CODE.apk build/aligned.apk

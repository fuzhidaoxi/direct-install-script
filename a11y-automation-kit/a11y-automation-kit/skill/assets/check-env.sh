#!/usr/bin/env bash
# 环境自检：编 APK + 反编译要用的工具是否齐全。用法：bash check-env.sh
SDK="${SDK:-$HOME/android-sdk}"
BT="${BT:-33.0.2}"
PLATFORM="${PLATFORM:-android-33}"
MISS=0

ok()   { echo "  [OK]   $1"; }
bad()  { echo "  [缺少] $1"; echo "         → $2"; MISS=1; }

echo "== 编译链 =="
[ -x "$SDK/build-tools/$BT/aapt2" ]    && ok "aapt2"    || bad "aapt2（build-tools/$BT）"    "sdkmanager \"build-tools;$BT\""
[ -x "$SDK/build-tools/$BT/d8" ]       && ok "d8"       || bad "d8（build-tools/$BT）"       "同上"
[ -x "$SDK/build-tools/$BT/zipalign" ] && ok "zipalign" || bad "zipalign（build-tools/$BT）" "同上"
[ -x "$SDK/build-tools/$BT/apksigner" ]&& ok "apksigner"|| bad "apksigner（build-tools/$BT）" "同上"
[ -f "$SDK/platforms/$PLATFORM/android.jar" ] && ok "android.jar" || bad "platforms/$PLATFORM" "sdkmanager \"platforms;$PLATFORM\""

echo "== Java =="
if command -v javac >/dev/null 2>&1; then ok "javac $(javac -version 2>&1)";
elif [ -f "${ECJ:-$HOME/apkbuild/ecj.jar}" ]; then ok "ecj.jar（无 javac 时可用）";
else bad "Java 编译器" "装 JDK（adoptium.net）或放 ecj.jar 到 \$HOME/apkbuild/ecj.jar"; fi
command -v keytool >/dev/null 2>&1 && ok "keytool" || bad "keytool（JDK 自带）" "装 JDK"

echo "== 反编译（可选，没有就走运行时 dump）=="
[ -f "$HOME/apktool/apktool.jar" ] && ok "apktool.jar" || echo "  [可选缺] apktool.jar → github.com/iBotPeaches/Apktool/releases"
[ -x "$SDK/build-tools/$BT/dexdump" ] && ok "dexdump" || echo "  [可选缺] dexdump（build-tools 里）"
command -v adb >/dev/null 2>&1 && ok "adb" || echo "  [可选缺] adb → developer.android.com/studio/releases/platform-tools"

echo ""
if [ "$MISS" = "0" ]; then
  echo "编译链齐全，可以跑 assets/a11y-apk-template/build.sh"
else
  echo "有必需项缺失，按上面提示补齐（详细下载地址见 references/setup.md）"
fi
exit $MISS

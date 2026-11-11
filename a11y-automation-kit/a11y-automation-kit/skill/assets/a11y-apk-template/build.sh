#!/usr/bin/env bash
# 无 Gradle 打包。依赖：Android build-tools（aapt2/d8/zipalign/apksigner）+ JDK（javac）
# 用法：SDK=/path/to/android-sdk BT=33.0.2 bash build.sh
set -e

SDK="${SDK:-$HOME/android-sdk}"
BT="${BT:-33.0.2}"
PLATFORM="${PLATFORM:-android-33}"
AAPT2="$SDK/build-tools/$BT/aapt2"
D8="$SDK/build-tools/$BT/d8"
ZIPALIGN="$SDK/build-tools/$BT/zipalign"
APKSIGNER="$SDK/build-tools/$BT/apksigner"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"
PACKAGE="com.example.autoclick"
APKNAME="${APKNAME:-自动点击器.apk}"

# ---- 缺工具时直接告诉你去哪下，而不是报一行看不懂的错 ----
need() {
  if [ ! -e "$1" ]; then
    echo "[缺少] $2"
    echo "        期望路径：$1"
    echo "        下载说明：见技能目录 references/setup.md"
    MISSING=1
  fi
}
need "$AAPT2"           "Android build-tools 里的 aapt2（sdkmanager \"build-tools;$BT\"）"
need "$D8"              "build-tools 里的 d8"
need "$ZIPALIGN"        "build-tools 里的 zipalign"
need "$APKSIGNER"       "build-tools 里的 apksigner"
need "$ANDROID_JAR"     "platforms/$PLATFORM/android.jar（sdkmanager \"platforms;$PLATFORM\"）"
if ! command -v javac >/dev/null 2>&1 && [ ! -f "${ECJ:-$HOME/apkbuild/ecj.jar}" ]; then
  echo "[缺少] Java 编译器：装 JDK（javac）或把 ecj.jar 放到 \$HOME/apkbuild/ecj.jar"
  echo "        下载：https://adoptium.net/temurin/releases/ 或 https://download.eclipse.org/eclipse/downloads/"
  MISSING=1
fi
if [ "${MISSING:-0}" = "1" ]; then
  echo ""
  echo "先把上面缺的补上再跑；或者用 SDK=/path/to/sdk BT=版本 PLATFORM=android-版本 指定已有环境。"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
rm -rf build out && mkdir -p build/classes build/dex out

echo "== 1 资源 =="
"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/base.apk -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R build/res.zip --auto-add-overlay

echo "== 2 编译 Java =="
SRCS=$(find src -name '*.java')
if command -v javac >/dev/null 2>&1; then
  javac -source 1.8 -target 1.8 -nowarn -encoding UTF-8 -classpath "$ANDROID_JAR" -d build/classes $SRCS
elif [ -f "${ECJ:-$HOME/apkbuild/ecj.jar}" ]; then
  # 没有完整 JDK 时用 Eclipse 编译器（只需 JRE）
  java -jar "${ECJ:-$HOME/apkbuild/ecj.jar}" -source 1.8 -target 1.8 -nowarn -encoding UTF-8 \
    -classpath "$ANDROID_JAR" -d build/classes $SRCS
else
  echo "缺少编译器：装 JDK（javac），或下载 ecj.jar 放到 \$HOME/apkbuild/ecj.jar 后用 ECJ=/path/to/ecj.jar 指定"; exit 1
fi

echo "== 3 dex =="
"$D8" --min-api 24 --lib "$ANDROID_JAR" --output build/dex $(find build/classes -name '*.class')

echo "== 4 合成 =="
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)

echo "== 5 对齐+签名 =="
"$ZIPALIGN" -f 4 build/unsigned.apk build/aligned.apk
KS="out/ks.jks"
if [ ! -f "$KS" ]; then
  if ! command -v keytool >/dev/null 2>&1; then
    echo "[缺少] keytool（属于 JDK），用它生成签名证书；装 JDK 后再跑"; exit 1
  fi
  keytool -genkeypair -keystore "$KS" -alias app -storepass 123456 -keypass 123456 \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=demo" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:123456 --key-pass pass:123456 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "out/$APKNAME" build/aligned.apk
"$APKSIGNER" verify "out/$APKNAME" >/dev/null && echo "签名校验通过"
ls -l "out/$APKNAME"

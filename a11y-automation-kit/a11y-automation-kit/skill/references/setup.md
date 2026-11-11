# 环境准备：工具从哪下载、装在哪、怎么验证

目标：**一条命令能编出可安装的 APK**，不用装 Android Studio。全部工具加起来不到 200MB。

约定目录（`build.sh` 默认就用这些路径，不想改就照这个放）：

```
$HOME/android-sdk/
   build-tools/33.0.2/     aapt2、d8、zipalign、apksigner、dexdump
   platforms/android-33/   android.jar
$HOME/apkbuild/ecj.jar      （没有 JDK 时的编译器回退）
$HOME/apktool/apktool.jar   （反编译用，可选但强烈建议）
```

---

## 1 最小必需（只编译不反编译）

| 工具 | 从哪拿 | 放哪 |
|---|---|---|
| **Android build-tools** | 官方命令行工具包 <https://developer.android.com/studio#command-line-tools-only> 解压后 `<sdk>/cmdline-tools/bin/sdkmanager` 安装：`sdkmanager "build-tools;33.0.2" "platforms;android-33"` | `$HOME/android-sdk/` |
| **JDK（含 javac）** | Adoptium Temurin <https://adoptium.net/temurin/releases/>（选 17 或 11，Linux x64 tar.gz；Windows 选 .msi） | 系统 PATH |
| **ecj.jar（JRE 应急编译器）** | <https://download.eclipse.org/eclipse/downloads/> 找 `ecj-*.jar`；或 Maven 中央仓 `org.eclipse.jdt:ecj` | `$HOME/apkbuild/ecj.jar` |

**验证装好了**：

```bash
aapt2 version
d8 --version
apksigner --version
javac -version          # 没有 javac 但有 ecj.jar 也能编
ls $HOME/android-sdk/platforms/android-33/android.jar
```

四条都能输出，就能跑 `build.sh`。

## 2 反编译用（要取控件 ID 才需要）

| 工具 | 从哪拿 | 说明 |
|---|---|---|
| **apktool** | <https://github.com/iBotPeaches/Apktool/releases> 下 `apktool_*.jar`，重命名成 `apktool.jar` | 需要 Java 8+；解包：`java -jar apktool.jar d -f -s app.apk -o out` |
| **jadx**（可选，看 Java 代码） | <https://github.com/skylot/jadx/releases> | 脱壳 dex 常大面积 decode failed，失败就改用 `dexdump -d` |
| **dexdump** | 已在 build-tools 里 | `dexdump -d classes.dex > dump.txt`，在指令流里抓字符串常量 |

**apktool 装不上怎么办**：直接用 `assets/NodeDump.java` 在真机上 dump 控件树，绕开解包。这条路能拿到所有需要的 id 名，只是不能批量预读布局。

## 3 真机调试用（可选）

| 工具 | 从哪拿 |
|---|---|
| **platform-tools（adb）** | <https://developer.android.com/studio/releases/platform-tools> 解压即用，无需安装 |
| 手机端管理器（看资源） | MT 管理器 / NP 管理器（应用商店或官网） |

`adb devices` 有设备后再 `adb logcat -s DumpNode` 看控件树。

## 4 云端（Linux）一次装完

```bash
# 1) 命令行工具
mkdir -p $HOME/android-sdk/cmdline-tools && cd $HOME/android-sdk/cmdline-tools
# 下载 commandlinetools-linux-*.zip 后解压成 latest/
# unzip commandlinetools-linux-*.zip && mv cmdline-tools latest

# 2) 组件
yes | $HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/android-sdk \
  "build-tools;33.0.2" "platforms;android-33"

# 3) 验证
$HOME/android-sdk/build-tools/33.0.2/aapt2 version
```

下载慢时换镜像：`sdkmanager --sdk_root=... --proxy=http --proxy_host=... ` 或设置 `HTTP_PROXY`；国内也可用第三方 SDK 镜像站，但注意校验来源。

## 5 Windows 上要什么

- `javac`：装 JDK（Temurin .msi）
- build-tools / platform：装 Android Studio 后在 `C:\Users\<你>\AppData\Local\Android\Sdk` 里，或者在 `build.sh` 同目录建一个 `sdk.path` 指向它
- **`build.sh` 是 bash 脚本**：Windows 上用 Git Bash 或 WSL 跑；不方便就直接用 Android Studio 打开模板工程编译（`assets/a11y-apk-template` 结构简单，也可用 Gradle 包一层）

## 6 常见安装坑

| 现象 | 原因 | 处理 |
|---|---|---|
| `aapt2: cannot execute binary file` | 下成了别的平台的包 | 按系统架构重下（linux-x64 / windows） |
| `d8: command not found` | build-tools 没进 PATH | 用绝对路径（`build.sh` 已经写死绝对路径） |
| `javac: command not found` | 只装了 JRE | 装 JDK，或放 `ecj.jar`（`build.sh` 会自动回退） |
| `sdkmanager` 报 Java 版本错 | JDK 版本太新/太旧 | 用 JDK 11 或 17 |
| `apksigner verify` 报 `Missing AndroidManifest` | 打包时漏了 manifest | 检查 `aapt2 link` 那步的参数 |
| 装到手机提示"解析包错误" | `resources.arsc` 被压缩了 | 先 `aapt2 link` 出 base.apk，再 `zipalign` 后签名，别手工 zip 重压 |

## 7 目标：交付时告诉用户什么

在交付说明里固定写这四行，能省掉一半的来回：

```
安装包：<文件名 / 链接>
安装后要做：设置 → 无障碍 → 找到「<应用名>」→ 打开（每次重装都要重开一次）
运行前提：目标应用停在 <页面>
出问题看：状态栏第一行是否显示「无障碍：已连接」
```

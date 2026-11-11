# 直装式自动化脚本套件（a11y-automation-kit）

给 Android 应用或网页做「装上就能跑」的自动化脚本：从安装包反编译出控件 ID，再产出**自制无障碍 APK**、**AutoX.js 脚本**或**篡改猴/脚本猫用户脚本**三种形态之一。

这不是又一个连点器。连点器是固定坐标点击，一遇到加载、弹窗、列表位移就点错；这里定位靠**控件语义**，等待靠**状态轮询**，失败靠**重试 + 熔断**。

## 适合谁

- 会折腾、有自己的 AI 或云端环境（能跑 apktool / aapt2 / build-tools）
- 想要「某个 App 里那几步重复操作」自动跑，但不打算学一门脚本语言
- 接受真机验证：定位能靠工具，界面行为只能真机看

**不适合**：只想双击安装、不打算碰命令行的；想自动化游戏的（自绘界面读不到控件）；想绕过付费墙或验证码的（这不是点击能解决的事）。

## 能做什么

| 目标 | 能不能 |
|---|---|
| 原生 / Material 控件应用 | 能做，最稳 |
| Jetpack Compose | 能做，id 少，多靠文字 |
| WebView / H5 套壳 / RN | 部分能做，常要退到文字或坐标 |
| Flutter | 勉强，界面基本只有一个节点 |
| Unity / 自绘游戏 | 不行，只能另上图像识别 |
| 网页（任意站点） | 能做，而且最容易，源码明文可读 |

## 目录结构

```
skill/
  SKILL.md                         总纲：判断 → 取控件 → 写 → 验收 → 性能 → 免责
  references/
    decompile-and-ids.md           反编译取控件（含无环境时的运行时 dump 兜底）
    setup.md                       工具从哪下、装哪、怎么验证、装不上的坑
    worked-example.md              端到端示例：从安装包到可用脚本
    shell-recipes.md               两种壳的写法（自制 APK / AutoX.js）
    web-userscripts.md             网页用户脚本（篡改猴 / 脚本猫）
    pitfalls.md                    长列表自动化 20+ 条避坑（现象 → 根因 → 修法）
  assets/
    a11y-apk-template/             可编译的无障碍 APK 模板（含 build.sh）
    check-env.sh                   环境自检
    NodeDump.java                  取控件工具：把节点树打成 id / text / bounds
    userscript-template.js         用户脚本模板
    交付说明模板.md                 交付文档填空模板
```

## 快速开始

```bash
# 1) 自检
bash skill/assets/check-env.sh

# 2) 看一份完整示例
less skill/references/worked-example.md

# 3) 复制模板，只改 AutoService 顶部的"必改区"（包名 + 6 个 id + 弹窗文字）
cp -r skill/assets/a11y-apk-template my-task && cd my-task
SDK=$HOME/android-sdk BT=33.0.2 bash build.sh
# → out/自动点击器.apk，末行「签名校验通过」
```

模板里的 `build.sh` 是纯命令行链路（aapt2 → javac/ecj → d8 → zipalign → apksigner），不依赖 Gradle，缺工具时会打印期望路径和下载地址。

## 三个必须先知道的事实

1. **重装 APK 会重置无障碍授权**。每次换版本都要去设置里关一次再打开，否则点开始毫无反应。
2. **界面行为只能真机验证**。控件 ID 能从安装包里读出来，但「弹窗几秒后出现」「列表一次滚多远」「键盘会不会挡住按钮」只能跑一遍才知道。
3. **这类脚本是持续消耗型**。单次连续别超过 20–30 项；低配机 10–15 项；不要夜间挂机——被系统回收后不会自己恢复。

## 常见问题

**Q：为什么不用 AutoX.js / AutoJs6？**
可以，而且它们能力更强。本仓库的价值不在引擎，在**方法**：怎么从安装包把控件 ID 读出来、怎么在长列表里逐行点而不越点越偏、弹窗慢半拍怎么处理。这些用哪个壳都一样会遇到，`references/pitfalls.md` 就是为此写的。

**Q：能自动领奖励、签到吗？**
技术上能做。但平台用户协议多数禁止自动化操作，账号可能被风控。风险自担，本仓库不提供任何规避风控的手段。

**Q：为什么不做游戏？**
游戏是自绘画面，系统里没有控件树，点按钮的逻辑无处可依。只能走图像识别，那是另一个量级的工程，且涉及破坏公平性，本仓库不做。

## 许可与免责

代码部分 MIT。使用前请阅读 [DISCLAIMER.md](DISCLAIMER.md)：仅限学习与个人效率用途，不得用于侵权、绕付费墙、刷量刷单等场景；账号与设备风险由使用者承担。

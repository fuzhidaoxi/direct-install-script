# 反编译取控件 ID（含没有反编译能力时的兜底）

## 1 先判架构

```bash
aapt2 dump badging target.apk | head -3      # 包名、版本
unzip -l target.apk | grep -E "classes|lib/" # 有没有 lib/*.so
```

- 有 `assets/index.android.bundle` → React Native
- 有 `lib/<abi>/libapp.so` → Flutter（进去就是「控件很少」，预期要退到文字/坐标）
- 有 `lib/<abi>/libil2cpp.so` + `assets/bin/Data` → Unity，直接劝退
- 只有 `classes*.dex` + 大量 `res/layout/*.xml` → 原生，最好做

**id 密度速判**：解包后看 `res/layout` 里的 `android:id="@+id/xxx"` 数量。命名是 `textInputEditText1`、`rv`、`more` 这种语义名的，说明开发者留了 id，后面会很顺；如果大量 `view1`、`view2` 甚至没有 id，说明靠代码动态生成，要做好退到文字的打算。

## 2 取 id（四步）

```bash
# ① 解包（-s 只解资源，速度快，不碰 dex）
apktool d -f -s target.apk -o out

# ② 导出 id 名字 ↔ 数值 全表（这一步是把 0x7f0801ad 反查成 more 的关键）
aapt2 dump resources target.apk > res.txt

# ③ 看目标页面的布局结构
ls out/res/layout | grep -i 目标页面关键字
sed -n '1,200p' out/res/layout/目标页面.xml
```

`res.txt` 里每条形如：

```
resource 0x7f0801ad id/more
```

对着布局里出现的 `@id/more`，就能确认「这个数值 id 就是行内那个下载按钮」。

### 挑节点的四条规则

1. **优先语义名**：`rv`（列表）、`more`（行内按钮）、`name`（行标题）、`textInputEditText1`（输入框）
2. **避开容器**：`cardview`、`coordinator`、`toolbar` 这类是布局容器，点了没反应
3. **确认唯一性**：同一个 id 名如果在列表行里出现，它是「每行一个」，要按行的范围去找，不能全局取第一个
4. **记名字不记数值**：代码里用 `:id/more` 匹配名字，数值会因为重新打包而变化

### 拿不到 id 时

有些 App 直接把 id 抹掉（`android:id` 全删）。这时按顺序退：

- 找 **contentDescription**（无障碍描述，图标按钮常留）
- 找 **唯一的文字**（按钮文案、标题）
- 找 **class + 序号**（`android.widget.Button` 的第 3 个）
- 最后才是坐标

## 3 没有反编译环境时的兜底（重点）

不是每个运行环境都能装 apktool / aapt2。两条替代路线，优先第一条：

### 3A 运行时 dump 控件树（最可靠，不需要反编译）

现成工具：`assets/NodeDump.java`，复制进模板工程后启用，`adb logcat -s DumpNode` 直接看：
`className | id名 | text | desc | [l,t][r,b] | clickable | scrollable`。第二列就是要的 id 名。


写一个最小的无障碍 App（或者用现成壳的日志），把当前界面的节点打出来：

```
className | viewIdResourceName | text | contentDescription | boundsInScreen | clickable
```

只要一行 `android.widget.ImageButton | com.xxx:id/more |  | 下载 | [x,y][x2,y2] | true`，你要的 id 名就拿到了。

AutoX.js 系里等价做法是遍历 `className("...").find()` 并 `console.log` 每个节点的 `id()`、`text()`、`bounds()`。

**这条路线的好处**：拿到的是真机上的真实 id，绕过了「解包失败 / 混淆 / 脱壳」所有坑。

### 3B 手机端直接看

MT 管理器 / NP 管理器可以打开 APK 里的 `resources.arsc` 与 layout（文本形式看），适合快速对名字，不适合批量。

## 4 脱壳 App 怎么办

加固过的 App，`apktool` 出来的 smali 只有壳代码，业务 dex 是加密的。这时：

- **先用 3A 路线（运行时 dump）**，绝大多数定位需求在这一步就解决了，根本不需要读业务代码
- 确实要读业务逻辑（找接口、看判断条件）才需要脱壳：BlackDex 之类在真机上 dump，再 `dexdump -d` 反汇编
- 脱壳 dex 用 jadx 常常大面积 decode failed，改用 `dexdump -d` 在指令流里抓字符串常量更稳

## 5 常见卡点

| 现象 | 原因 | 处理 |
|---|---|---|
| `apktool d` 报错 | 资源混淆 / 加固 / 分包异常 | 加 `-r` 跳过资源，或直接走 3A |
| `res.txt` 里 id 一堆重复 | 名字被复用 | 用「所在页面 + 上下级关系」区分 |
| 布局里没有目标控件 | 动态 inflate 或 Compose | 走 3A 运行时 dump |
| id 名对了但点不动 | 节点 clickable=false | 往上找最近的可点击父节点，或按坐标点 |

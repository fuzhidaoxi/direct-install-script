# 两种壳的写法（同一份控件清单，两套代码）

前提：已完成 `decompile-and-ids.md`，手上有一份「控件清单」：

```
包名: com.example.app
输入框: textInputEditText1
提交按钮: fab
列表: rv
行内按钮: more
行内标题: name
行内副标题: content
确认弹窗文字: 本地下载
```

---

## A. 自制无障碍执行器 APK

### A1 最小骨架

```xml
<!-- AndroidManifest.xml -->
<service android:name=".AutoService"
         android:exported="true"
         android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
  <intent-filter>
    <action android:name="android.accessibilityservice.AccessibilityService"/>
  </intent-filter>
  <meta-data android:name="android.accessibilityservice"
             android:resource="@xml/accessibility_service_config"/>
</service>

<queries><package android:name="com.example.app"/></queries>
```

```xml
<!-- res/xml/accessibility_service_config.xml -->
<accessibility-service
    android:accessibilityEventTypes="typeAllMask"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"/>
```

### A2 三件必备能力

```java
// 找：限定包名 + 按 id 名匹配（.endsWith(":id/" + name) 兼容重打包后的包名变化）
List<AccessibilityNodeInfo> findById(String name)

// 找文字：text 或 contentDescription 精确匹配；弹窗常常只有文字没有 id
List<AccessibilityNodeInfo> findByText(String text)

// 点：优先节点 ACTION_CLICK，往上爬 4 层找可点击父节点，最后才用当代坐标 tap()
boolean clickNode(AccessibilityNodeInfo n)
```

### A3 主循环结构（照这个骨架写，别写成一长串 if）

```
每项开始前:  关残留弹窗 → 列表回顶 → 等输入框（4s，可重试1次）
写入数据:    清空 → 写 → 重新抓节点回读 → 读回不对就清空重来（3次）
触发:        记录旧列表顶部 key → 点提交按钮
等结果:      轮询「行数连续两次不变 且 顶部换人」，超时再比对一次旧 key
逐行处理:    每轮重抓全部按钮 → 按 Y 排序 → 只取「整行完整可见」且未处理过的行
             点前先探测弹窗是否已在 → 点 → 轮询等确认（3.5s）→ 成功就标记该行
             取不到可点的行 → 小步滑（1/3 屏高，600ms+，等 1.3s）→ 顶部两次没变视为到底
熔断:        单行最多 3 次尝试（间隔递增）；同一行连续失败 3 次主动等 6s；
             连续 2 项零产出 → 停，写明停因和第几项
结束:        列表回顶；状态按「完成 / 手动停止 / 熔断原因」三态输出
```

### A4 没有 Gradle 时的打包

```
ecj 编译 → d8 生成 dex → aapt2 link 生成 base.apk
→ 加入 dex → zipalign -f 4 → apksigner sign（v1+v2+v3）
```

`resources.arsc`、`AndroidManifest.xml`、`.so` 必须 Stored 不压缩，否则装机报「安装包损坏 / 不兼容」。

---

## B. 现成壳：AutoX.js / AutoJs6 / Hamibot

### B1 关键差异（先看这个，别照抄 A 的代码）

| 自制执行器 | 现成壳 |
|---|---|
| 持有 `AccessibilityNodeInfo`，用完即弃 | 选择器每次 `findOne()` 重新查 |
| `performAction(ACTION_SET_TEXT)` 写文本 | `setText()` / `input()` |
| `dispatchGesture` 做滑动 | `swipe(x1,y1,x2,y2,duration)` |
| 自己维护状态字符串 | `toast()` / `console.log()` 输出到浮窗或日志页 |
| 代码即流程 | 脚本即流程，可被用户替换 |

### B2 同一份清单在壳里的写法（骨架）

```js
// 1) 每项开始前收拾现场
function prepare() {
  // 关残留弹窗：只有确认是弹窗才用返回
  if (text("确认文字").exists()) back(); sleep(700);
  // 列表回顶
  var list = id("rv").findOne(1000);
  if (list) { list.scrollBackward(); sleep(400); }
}

// 2) 写入：清空 → 写 → 回读校验（对应避坑 11/12）
function fill(word) {
  for (var i = 0; i < 3; i++) {
    var box = id("textInputEditText1").findOne(1000);
    if (!box) return false;
    box.setText("");
    sleep(250);
    box.setText(word);
    sleep(300);
    var got = id("textInputEditText1").findOne(500);
    if (got && got.text().replace(/\s/g, "") === word.replace(/\s/g, "")) return true;
  }
  return false;
}

// 3) 等结果：行数稳定 + 顶部换人（对应避坑 9/10）
function waitResults(prevTop, timeout) {
  var last = -1, t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    var n = id("more").find().size();
    var top = topKey();
    if (n > 0 && n === last && top !== prevTop) return n;
    last = n;
    sleep(500);
  }
  return 0;
}

// 4) 逐行：只点完整可见的行（对应避坑 2/4/5）
function rowKey(btn) {
  var row = btn.parent();          // 往上找到含 name 的那层
  var name = row.findOne(id("name"));
  return name ? name.text() : null;
}

function clickRows(max) {
  var done = 0, seen = {}, idle = 0;
  while (done < max) {
    var btns = id("more").find();
    btns.sort((a, b) => a.bounds().centerY() - b.bounds().centerY());
    var hit = null, key = null;
    for (var i = 0; i < btns.length; i++) {
      var b = btns[i], r = b.bounds();
      if (r.top < 60 || r.bottom > device.height - 60) continue;   // 被裁掉的行跳过
      var k = rowKey(b);
      if (!k || seen[k]) continue;
      hit = b; key = k; break;
    }
    if (!hit) {
      swipe(device.width / 2, device.height * 0.78, device.width / 2, device.height * 0.45, 700);
      sleep(1300);
      if (++idle > 2) break;
      continue;
    }
    idle = 0;
    // 先探测弹窗，再点行（对应避坑 6）
    if (text("确认文字").exists()) { text("确认文字").findOne(500).click(); }
    else { hit.click(); }
    if (text("确认文字").findOne(3500)) { done++; seen[key] = 1; }
    else { seen[key] = 1; }
    sleep(600);
  }
  return done;
}
```

### B3 在壳上的额外注意事项

- **保活**：国产 ROM 会把壳进程回收，需要电池白名单 + 前台服务；跑长任务前先手动试 5 分钟。
- **无障碍授权**：壳本身要开无障碍；重装壳 = 重新授权。
- **脚本超时**：壳一般有单次运行时长上限，长任务要分段。
- **日志**：一定要打「第几项、哪一步、失败原因」，否则用户报 bug 时无从定位。

---

## C. 选哪条

| 场景 | 选 |
|---|---|
| 只做一件事、要给不懂技术的人用 | A（自制 APK） |
| 多任务共存、用户愿意装壳 | B（现成壳） |
| 目标 App 频繁改版、要快速改 | B（脚本改起来快） |
| 需要读本地文件、批量数据、复杂计算 | A（能写完整业务逻辑） |

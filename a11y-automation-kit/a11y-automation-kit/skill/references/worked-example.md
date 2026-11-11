# 端到端示例：从一个安装包到可交付的脚本（约 30 分钟）

示例应用叫「应用D」，需求是：**输入关键词 → 点搜索 → 结果列表里把前 10 行逐个点右侧按钮 → 弹窗点「确认」→ 下一项**。
真实项目里把名字、id、按钮文案换掉即可，流程结构完全一致。

---

## 第 0 步 环境自检

```bash
bash assets/check-env.sh
```

全绿才继续。缺什么按输出里的提示补（详见 `references/setup.md`）。

---

## 第 1 步 解包并确认架构

```bash
aapt2 dump badging app.apk | head -3
# package: name='com.example.app'
unzip -l app.apk | grep -E "classes|lib/" | head
apktool d -f -s app.apk -o out
ls out/res/layout | wc -l        # 原生应用通常几十上百个；若只有个位数，走运行时 dump
```

命中 `lib/arm64/libapp.so` 且 layout 极少 → Flutter，别硬做，改走 `assets/NodeDump.java` 看有没有可用文字节点。

---

## 第 2 步 取控件

```bash
aapt2 dump resources app.apk > res.txt
grep -n "id/" res.txt | head -50        # 看有哪些语义化名字
grep -n "输入" out/res/layout/activity_main.xml
sed -n '1,120p' out/res/layout/activity_main.xml
```

对着布局找这几类节点，得到一份清单：

| 用途 | id 名 | 类型 | 备注 |
|---|---|---|---|
| 输入框 | `textInputEditText1` | EditText | 有 `@id/textInputEditText1` |
| 提交按钮 | `fab` | ExtendedFAB | 在 `appbar` 底部 |
| 结果列表 | `rv` | RecyclerView | 每行一个 item 布局 |
| 行内按钮 | `more` | MaterialButton | 行右侧，44dp |
| 行标题 | `name` | TextView | 用于去重 |
| 行副标题 | `content` | TextView | 歌手/来源 |
| 弹窗确认 | — | Button | 无 id，靠文字「确认」 |

**没有 id 的那一行（弹窗）**：用文字定位，写在 `CONFIRM_TEXT`。

> 解包失败或 App 加固时：跳过 1、2 步，装 `NodeDump` 到真机，操作一遍，从 logcat 抄 id 名。**结果一样，只是不能批量预读。**

---

## 第 3 步 填模板

复制 `assets/a11y-apk-template/`，只改 `AutoService.java` 顶部必改区：

```java
private static final String TARGET_PKG  = "com.example.app";
private static final String ID_INPUT    = "textInputEditText1";
private static final String ID_SUBMIT   = "fab";
private static final String ID_LIST     = "rv";
private static final String ID_ROWBTN   = "more";
private static final String ID_TITLE    = "name";
private static final String ID_SUB      = "content";
private static final String CONFIRM_TEXT= "确认";
```

`runTask()` 里把 `items` 换成自己的数据源（读 txt / 从输入框取），流程骨架不用动。

---

## 第 4 步 编译

```bash
SDK=$HOME/android-sdk BT=33.0.2 bash build.sh
# 输出 out/自动点击器.apk，末行「签名校验通过」
```

## 第 5 步 真机三件事

1. 安装 → 设置→无障碍→打开（**重装后必须重开**）
2. 手动打开目标应用、停在目标页面
3. 点开始，只看 3 项

**通过标准**：3 项连续成功、单项 <10 秒、状态栏能显示「第几项/哪一步」。

---

## 第 6 步 换成网页版（同一份清单）

如果目标其实是网页，不走反编译，一个 `.user.js` 就够：

```js
// ==UserScript==
// @name         应用D-批量
// @match        https://example.com/search
// @grant        GM_setValue
// @grant        GM_getValue
// ==/UserScript==

(async () => {
  const q = '关键词';
  const input = document.querySelector('#search-input');
  input.value = q;
  input.dispatchEvent(new Event('input', { bubbles: true }));
  document.querySelector('#search-btn').click();

  await new Promise(r => setTimeout(r, 3000));       // 或等接口返回
  const rows = [...document.querySelectorAll('.result-row')];
  const seen = new Set();
  let done = 0;

  for (const row of rows) {
    if (done >= 10) break;
    const key = row.querySelector('.title')?.textContent?.trim();
    if (!key || seen.has(key)) continue;
    seen.add(key);
    row.querySelector('.download-btn')?.click();      // 先探测弹窗再点，见模板
    await new Promise(r => setTimeout(r, 900));
    const ok = [...document.querySelectorAll('button')]
      .find(b => b.textContent.trim() === '确认');
    if (ok) { ok.click(); done++; }
  }
  GM_setValue('done', done);
  console.log('[task] 完成', done);
})();
```

**能调接口就别点按钮**：先在 Network 里找到那个 `POST`，用 `GM_xmlhttpRequest` 直接发，稳定得多。

---

## 第 7 步 交付

按 `assets/交付说明模板.md` 填四行 + 状态栏语义表 + 性能提醒，一起给对方。

**这份示例的意义**：任何"搜索→列表→逐行点→弹窗确认"的任务，只是换了 id 和文案。流程不要重新设计，改配置即可。

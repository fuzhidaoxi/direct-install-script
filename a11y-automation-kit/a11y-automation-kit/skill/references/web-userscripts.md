# 第三种形态：网页用户脚本（篡改猴 / 脚本猫 / 油猴）

网页侧的路径和 App 侧完全不同：**这里没有"反编译"，只有"读源码"**。HTML/CSS/JS 本来就是明文，F12 打开就能看，难度比 APK 低一个数量级——所以同样的定位逻辑，网页端应该先做、做得更细。

---

## 1 判断阶梯：先接口，后 UI

```
能直接调页面自己的接口吗？
   ├─ 能 → 直接发请求（最稳、最快、不怕改版 UI）
   └─ 不能（要鉴权/加密签名）→ 才动手点界面
```

网页端尤其如此：很多"点按钮"其实是在调用一个 `POST /api/xxx`，把它抠出来直接调用，比模拟点击稳定百倍。

**这条和 App 侧同一个原则**：能发请求就别驱动 UI。

---

## 2 定位优先级（网页版）

1. **稳定的 id / 自定义属性**：`#app`、`data-testid`
2. **结构路径**：`div.list > item:nth-child(3)`
3. **固定 class**：注意 CSS Modules / styled-components 会把 class 变成 `css-1x2y3z` 这种哈希，**每次构建都变**——这种不能当锚点
4. **文本内容**：`[...] 里 textContent 精确匹配
5. **坐标**：网页里几乎用不到，除非元素被遮挡

查找 DOM 的顺序固定为：`querySelector` → 多次重试 → 放弃该次任务。

---

## 3 时序：网页最常见的失败原因

网页是异步渲染的，**元素"现在没有"不代表"永远没有"**。三条规矩：

```js
// ① 等元素出现（轮询，带超时）
function waitFor(sel, timeout = 8000) {
  return new Promise((res, rej) => {
    const t0 = Date.now();
    (function loop() {
      const el = document.querySelector(sel);
      if (el) return res(el);
      if (Date.now() - t0 > timeout) return rej(new Error('timeout: ' + sel));
      setTimeout(loop, 150);
    })();
  });
}

// ② 等元素"稳定"（连续两次检查拿到的不是同一个替换过的节点）
// ③ 等请求返回：优先 hook fetch/XHR 拿到 done 事件，别硬 sleep
```

**别用固定 `sleep(3000)` 代替等待**：机器快的时候白等，机器慢的时候不够。这是和 App 侧"等结果稳定"完全同源的问题。

---

## 4 常见形态：列表逐行处理（和 App 侧同一套逻辑）

虚拟列表（只渲染可视区）和"走马灯式重渲染"是最容易出事的地方：

```js
async function eachRow(handler) {
  const seen = new Set();
  let idle = 0;
  while (idle < 3) {
    const rows = [...document.querySelectorAll('.row')];
    let did = false;
    for (const r of rows) {
      const key = r.querySelector('.title')?.textContent?.trim();
      if (!key || seen.has(key)) continue;
      if (!isFullyVisible(r)) continue;      // 被容器裁掉的跳过
      seen.add(key);
      await handler(r);
      did = true;
      break;                                  // 一次只处理一行，处理完重新取列表
    }
    if (!did) {
      scrollBy(0, container.clientHeight * 0.6);
      await sleep(900);
      idle++;
    } else idle = 0;
  }
}

function isFullyVisible(el) {
  const r = el.getBoundingClientRect();
  const box = el.closest('.list')?.getBoundingClientRect() || { top: 0, bottom: innerHeight };
  return r.top >= box.top + 4 && r.bottom <= box.bottom - 4 && r.height > 0;
}
```

要点与 App 侧一字不差：**处理完一行就重新取列表**（元素可能已被替换）、**只处理完整可见的行**、**小步滚动**、**连续多次没新行视为到底**。

---

## 5 脚本头部（篡改猴 / 脚本猫 通用）

```js
// ==UserScript==
// @name         示例任务
// @namespace    local.tools
// @version      1.0
// @match        https://example.com/*
// @run-at       document-idle
// @grant        GM_setValue
// @grant        GM_getValue
// @grant        GM_xmlhttpRequest
// @connect      example.com
// ==/UserScript==
```

- `@match` 写窄一点，别用 `*://*/*`，否则每个网站都跑
- 跨域请求必须 `GM_xmlhttpRequest` + `@connect`，`fetch` 会被 CORS 拦
- 要读页面里的变量用 `unsafeWindow`（部分环境需要额外 grant）
- **脚本猫**（ScriptCat）兼容大部分 GM API，但个别接口签名不同（如 `GM_xmlhttpRequest` 的回调风格），写之前按目标环境的文档核对
- 单文件 `.user.js`，用户拖进篡改猴即可，**不需要打包、不需要签名、不需要重装**

---

## 6 网页侧 12 条避坑

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | 点了一次就失效 | 页面重渲染，旧元素引用成孤儿 | 每次操作前重新 `querySelector` |
| 2 | class 换了就全挂 | CSS Modules 哈希 class | 换 id / data 属性 / 结构路径 / 文本 |
| 3 | 脚本在页面刷新后不生效 | 单页应用路由变化没有重新初始化 | 监听 `popstate` / `MutationObserver` 重建 |
| 4 | 一直等下不到元素 | 数据在 iframe 或异步分页里 | 定位到正确的 frame/容器，或等接口返回 |
| 5 | 请求跨域失败 | 用了 `fetch` | 改 `GM_xmlhttpRequest` 并声明 `@connect` |
| 6 | 频繁请求被限流/风控 | 无间隔并发 | 加随机间隔、串行、失败退避 |
| 7 | 点完没反应 | 元素被遮挡或需要 `dispatchEvent` | 先 `scrollIntoView`，再 `el.click()`，最后才 `dispatchEvent(new MouseEvent(...))` |
| 8 | 虚拟列表只处理到 6 行 | 列表只渲染可视区 | 小步滚动 + 每轮重取 |
| 9 | 登录态拿不到 | Cookie 是 HttpOnly | 只能走页面自身的请求通道，不要试图读 Cookie |
| 10 | 页面一改版就废 | 依赖了文案或层级 | 优先接口；DOM 定位放在最后 |
| 11 | 脚本相互打架 | 同一页面装了两个脚本 | `@match` 收窄 + 命名空间隔离 |
| 12 | 用户报错无法定位 | 没有日志 | 关键步骤 `console.log` 带步骤名与 id，或 GM_setValue 记录最近状态 |

---

## 6B 网页侧的性能与长时间运行

- **定时器泄漏**：`setInterval` 忘记清，切页面/刷新后旧实例还在跑，CPU 会一路涨。用 `clearInterval` + 页面卸载钩子。
- **MutationObserver 泄漏**：观察整个 `document.body` 的子树变化，页面一动就回调，长列表页会非常烫；要把观察范围收窄到目标容器，并在结束时 `disconnect`。
- **日志堆积**：控制台里存几万条字符串同样吃内存，长任务只保留最近 N 条。
- **轮询频率**：`setTimeout(150)` 全页面查询在低端机上很重；优先用事件驱动（请求完成回调、MutationObserver），轮询只作兜底。
- **建议**：单次运行不超过 30 分钟；批量任务分批做，每批之间刷新页面释放内存。

## 7 网页侧的红线

- 不绕过登录、不伪造鉴权、不抓取他人隐私数据
- 平台风控与用户协议风险由使用者承担，不宣称"不会封号"
- 只对公开页面做交互自动化；涉及支付、验证码、实名的一律说明做不到
- 不写"自动刷量、自动领奖励"这类模板供他人直接使用，只提供方法

---

## 8 和 App 侧共用什么

| 共用 | 不同 |
|---|---|
| 判断阶梯（能调接口就别点 UI） | App 要解包取 id，网页直接读 DOM |
| 定位优先级（稳定标识 > 文本 > 坐标） | App 用 `:id/xxx`，网页用选择器 |
| 时序等待（等稳定，不等固定时间） | App 用节点树轮询，网页用 DOM 轮询 |
| 长列表逐行处理的四个要点 | 网页多一个"虚拟列表/重渲染"问题 |
| 熔断、重试递增、状态可读 | 网页要额外注意 iframe、CORS、限流 |

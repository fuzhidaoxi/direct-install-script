// ==UserScript==
// @name         任务模板（改三处即可用）
// @namespace    local.tools
// @version      1.0
// @match        https://example.com/*
// @run-at       document-idle
// @grant        GM_setValue
// @grant        GM_getValue
// @grant        GM_xmlhttpRequest
// @connect      example.com
// ==/UserScript==

(function () {
  'use strict';

  // ============ 必改区 ============
  const CFG = {
    listSel: '.row',          // 列表行选择器
    titleSel: '.title',       // 行内标题（用于去重）
    btnSel: '.btn',           // 行内按钮
    confirmText: '确认',      // 弹窗确认按钮文字（没有就留空）
    max: 10,                  // 最多处理几行
    gapMs: 600                // 每行之间的间隔
  };
  // ===============================

  const log = (...a) => console.log('[task]', ...a);
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  /** 等元素出现（轮询带超时，别用固定 sleep 代替） */
  function waitFor(sel, timeout = 8000) {
    return new Promise((resolve, reject) => {
      const t0 = Date.now();
      (function loop() {
        const el = document.querySelector(sel);
        if (el) return resolve(el);
        if (Date.now() - t0 > timeout) return reject(new Error('timeout: ' + sel));
        setTimeout(loop, 150);
      })();
    });
  }

  /** 只处理完整落在可视容器内的行，被裁掉的跳过 */
  function fullyVisible(el, container) {
    const r = el.getBoundingClientRect();
    const box = container ? container.getBoundingClientRect()
                          : { top: 0, bottom: window.innerHeight };
    return r.height > 0 && r.top >= box.top + 4 && r.bottom <= box.bottom - 4;
  }

  /** 点之前先探测弹窗是否已在，避免重复点把弹窗顶掉 */
  async function confirmIfPresent() {
    if (!CFG.confirmText) return false;
    const btn = [...document.querySelectorAll('button, div, span')]
      .find((n) => n.textContent && n.textContent.trim() === CFG.confirmText && n.offsetParent !== null);
    if (!btn) return false;
    btn.click();
    await sleep(400);
    return true;
  }

  async function main() {
    try {
      await waitFor(CFG.listSel, 10000);
    } catch (e) {
      log('列表没出现，检查是否已登录/页面是否变了');
      return;
    }

    const seen = new Set();
    let done = 0;
    let idle = 0;

    while (done < CFG.max && idle < 3) {
      const rows = [...document.querySelectorAll(CFG.listSel)];
      let hit = null, key = null;

      for (const row of rows) {
        if (!fullyVisible(row)) continue;
        const t = row.querySelector(CFG.titleSel);
        const k = t ? t.textContent.trim() : null;
        if (!k || seen.has(k)) continue;
        hit = row; key = k; break;
      }

      if (!hit) {
        // 小步滚动，别整页跳（会漏行）
        const container = document.querySelector(CFG.listSel)?.parentElement;
        window.scrollBy(0, (container?.clientHeight || window.innerHeight) * 0.6);
        await sleep(900);
        idle++;
        continue;
      }

      idle = 0;
      if (!(await confirmIfPresent())) {
        const btn = hit.querySelector(CFG.btnSel);
        if (!btn) { seen.add(key); continue; }
        btn.scrollIntoView({ block: 'center' });
        await sleep(150);
        btn.click();
      }

      if (CFG.confirmText) {
        // 等确认按钮出现（3.5 秒窗口）
        const t0 = Date.now();
        let ok = false;
        while (Date.now() - t0 < 3500) {
          if (await confirmIfPresent()) { ok = true; break; }
          await sleep(200);
        }
        if (ok) done++;
      } else {
        done++;
      }

      seen.add(key);
      log('完成', done, key);
      await sleep(CFG.gapMs);
    }

    log('结束，共处理', done, '项');
    GM_setValue('lastRun', JSON.stringify({ at: Date.now(), done }));
  }

  main();
})();

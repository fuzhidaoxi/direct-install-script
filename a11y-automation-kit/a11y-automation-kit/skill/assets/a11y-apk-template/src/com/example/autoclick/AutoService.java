package com.example.autoclick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 最小可用骨架：只保留「找节点 / 点 / 滑动 / 等待 / 重试 / 熔断」。
 * 使用时只需要改下面 CONFIG 区和 runTask() 里的流程。
 */
public class AutoService extends AccessibilityService {

    public static AutoService instance;
    public static volatile boolean running = false;
    public static volatile String status = "空闲";
    public static volatile long lastBeat = 0;

    // ========================= 必改区（换应用只改这里） =========================
    private static final String TARGET_PKG = "TARGET_PKG";   // 目标应用包名
    private static final String ID_INPUT  = "textInputEditText1"; // 输入框（没有就填 null）
    private static final String ID_SUBMIT = "fab";            // 提交/搜索按钮
    private static final String ID_LIST   = "rv";             // 列表容器
    private static final String ID_ROWBTN = "more";           // 行内操作按钮
    private static final String ID_TITLE  = "name";           // 行标题
    private static final String ID_SUB    = "content";        // 行副标题（可为 null）
    private static final String CONFIRM_TEXT = "确认";         // 弹窗里的确认按钮文字
    // ===========================================================================

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        toast("无障碍已连接");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }

    public void startRun() {
        // 上一轮线程已死时 running 会一直挂着 true，导致"点开始没反应"
        if (running && System.currentTimeMillis() - lastBeat < 15000) { toast("正在跑"); return; }
        running = true;
        lastBeat = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                try { runTask(); }
                catch (Throwable t) { setStatus("出错：" + t); }
                finally { running = false; }
            }
        }, "worker").start();
    }

    // ========================= 流程（照这个结构改） =========================
    private void runTask() throws Exception {
        if (waitForId(ID_INPUT, 8000) == null && waitForId(ID_SUBMIT, 2000) == null) {
            setStatus("没进目标界面：先手动打开目标应用");
            return;
        }
        String stopReason = null;

        // 示例：处理 1 项。多项目时把 items 换成从文件/输入读出来的列表
        String[] items = {"示例项1", "示例项2"};
        int zeroStreak = 0;

        for (int i = 0; i < items.length; i++) {
            if (!running) break;
            String item = items[i];

            prepare();                                        // 收拾现场：关弹窗、列表回顶
            if (!fillInput(item)) {                           // 写入 + 回读校验
                setStatus("填不进：" + item); zeroStreak++; continue;
            }
            sleep(400);
            String prevTop = topKey();                        // 点提交前记旧列表顶部
            if (!clickId(ID_SUBMIT)) { setStatus("找不到提交按钮"); continue; }

            int rows = waitResults(prevTop, 16000);           // 等结果稳定
            if (rows <= 0) {
                setStatus("没等到结果：" + item); zeroStreak++;
                if (zeroStreak >= 2) { stopReason = "连续两处没结果，停在第 " + (i + 1) + " 项"; break; }
                continue;
            }

            int got = clickRows(10);                          // 逐行处理
            setStatus(item + " 完成 " + got + " 项");
            if (got == 0) { zeroStreak++; if (zeroStreak >= 2) { stopReason = "连续两处零产出，停在第 " + (i + 1) + " 项"; break; } }
            else zeroStreak = 0;
        }
        if (stopReason != null) setStatus(stopReason);
        else if (!running) setStatus("已手动停止");
        else setStatus("全部完成");
    }

    /** 每项开工前：关残留弹窗 → 列表回顶 → 等输入框 */
    private void prepare() {
        if (!findText("下载方式").isEmpty() || !findText("提示").isEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK);   // 只在确认是弹窗时才用返回
            sleep(700);
        }
        AccessibilityNodeInfo rv = findId(ID_LIST);
        for (int i = 0; i < 6 && rv != null; i++) {
            if (!rv.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break;
            sleep(250);
        }
        waitForId(ID_INPUT, 4000);
    }

    /** 清空 → 写 → 重新抓节点回读（防"填两遍"） */
    private boolean fillInput(String text) {
        if (ID_INPUT == null) return true;
        for (int a = 0; a < 3 && running; a++) {
            AccessibilityNodeInfo ed = findId(ID_INPUT);
            if (ed == null) return false;
            setRaw(ed, "");
            sleep(220);
            setRaw(findId(ID_INPUT), text);
            sleep(300);
            String got = readInput();
            if (got != null && norm(got).equals(norm(text))) return true;
            setRaw(findId(ID_INPUT), "");
            sleep(200);
        }
        return false;
    }

    private String readInput() {
        AccessibilityNodeInfo n = findId(ID_INPUT);
        try { return (n != null && n.getText() != null) ? n.getText().toString() : null; }
        catch (Throwable t) { return null; }
    }

    private static String norm(String s) { return s == null ? "" : s.replaceAll("\\s+", "").trim(); }

    /** 轮询等结果：行数连续两次不变 且 顶部换人 */
    private int waitResults(String prevTop, long timeoutMs) {
        long t0 = System.currentTimeMillis();
        int last = -1;
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            int now = rowButtons().size();
            String top = topKey();
            boolean changed = prevTop == null || top == null || !prevTop.equals(top);
            if (now > 0 && now == last && changed) return now;
            last = now;
            sleep(500);
        }
        if (prevTop != null && prevTop.equals(topKey())) return 0;  // 还是旧列表
        return rowButtons().size();
    }

    /** 逐行处理：每次重抓、只取完整可见、处理过的跳过、小步滑 */
    private int clickRows(int max) {
        int done = 0, idle = 0;
        Set<String> seen = new HashSet<String>();
        while (done < max && running) {
            List<AccessibilityNodeInfo> btns = rowButtons();
            sortByY(btns);
            String key = null;
            AccessibilityNodeInfo hit = null;
            for (AccessibilityNodeInfo b : btns) {
                if (!fullyVisible(b)) continue;
                String k = rowKey(b);
                if (k == null || seen.contains(k)) continue;
                hit = b; key = k; break;
            }
            if (hit == null) {
                if (!scrollStep() || ++idle > 2) break;
                continue;
            }
            idle = 0;
            // 点之前先探测弹窗是否已经在，避免重复点把弹窗顶掉
            if (findText(CONFIRM_TEXT).isEmpty()) {
                if (!clickNode(hit)) { seen.add(key); continue; }
            }
            if (waitText(CONFIRM_TEXT, 3500)) {
                clickText(CONFIRM_TEXT);
                done++;
            }
            seen.add(key);
            sleep(600);
        }
        return done;
    }

    // ========================= 基础能力 =========================
    private AccessibilityNodeInfo findId(String idName) {
        List<AccessibilityNodeInfo> l = findIdList(idName);
        return l.isEmpty() ? null : l.get(0);
    }

    private List<AccessibilityNodeInfo> findIdList(String idName) {
        List<AccessibilityNodeInfo> out = new ArrayList<AccessibilityNodeInfo>();
        if (idName == null) return out;
        collectId(getRootInActiveWindow(), idName, out);
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) collectId(r, idName, out);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private void collectId(AccessibilityNodeInfo n, String idName, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        try {
            if (isTarget(n)) {
                CharSequence v = n.getViewIdResourceName();
                if (v != null && v.toString().endsWith(":id/" + idName)) out.add(n);
            }
            for (int i = 0; i < n.getChildCount(); i++) collectId(n.getChild(i), idName, out);
        } catch (Throwable ignored) { }
    }

    private List<AccessibilityNodeInfo> findText(String text) {
        List<AccessibilityNodeInfo> out = new ArrayList<AccessibilityNodeInfo>();
        collectText(getRootInActiveWindow(), text, out);
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null) collectText(r, text, out);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private void collectText(AccessibilityNodeInfo n, String text, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        try {
            if (isTarget(n)) {
                CharSequence t = n.getText(), d = n.getContentDescription();
                if ((t != null && t.toString().trim().equals(text)) || (d != null && d.toString().trim().equals(text))) out.add(n);
            }
            for (int i = 0; i < n.getChildCount(); i++) collectText(n.getChild(i), text, out);
        } catch (Throwable ignored) { }
    }

    private boolean isTarget(AccessibilityNodeInfo n) {
        try { return TARGET_PKG.equals(String.valueOf(n.getPackageName())); } catch (Throwable t) { return false; }
    }

    /** 节点点击优先；列表按钮常常 clickable=false，往上爬 4 层找可点父节点；最后才坐标 */
    private boolean clickNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 4 && cur != null; i++) {
            try {
                if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { sleep(200); return true; }
            } catch (Throwable ignored) { }
            cur = cur.getParent();
        }
        Rect r = bounds(n);
        if (r.width() <= 0) return false;
        return tap(r.centerX(), r.centerY());
    }

    private boolean clickId(String id) { return clickNode(findId(id)); }
    private boolean clickText(String text) {
        List<AccessibilityNodeInfo> l = findText(text);
        if (l.isEmpty()) return false;
        Collections.sort(l, new Comparator<AccessibilityNodeInfo>() {
            @Override public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) { return bounds(b).centerY() - bounds(a).centerY(); }
        });
        return clickNode(l.get(0));
    }

    private List<AccessibilityNodeInfo> rowButtons() {
        List<AccessibilityNodeInfo> all = findIdList(ID_ROWBTN);
        List<AccessibilityNodeInfo> keep = new ArrayList<AccessibilityNodeInfo>();
        for (AccessibilityNodeInfo n : all) { Rect r = bounds(n); if (r.width() > 0 && r.height() > 0) keep.add(n); }
        return keep;
    }

    /** 行的唯一标识：标题 + 副标题；滚动后坐标会变，不能用坐标 */
    private String rowKey(AccessibilityNodeInfo btn) {
        try {
            AccessibilityNodeInfo row = btn.getParent();
            for (int up = 0; up < 4 && row != null; up++) {
                List<AccessibilityNodeInfo> names = new ArrayList<AccessibilityNodeInfo>();
                collectId(row, ID_TITLE, names);
                if (names.size() == 1 && names.get(0).getText() != null) {
                    String t = names.get(0).getText().toString().trim();
                    String sub = "";
                    if (ID_SUB != null) {
                        List<AccessibilityNodeInfo> cs = new ArrayList<AccessibilityNodeInfo>();
                        collectId(row, ID_SUB, cs);
                        if (cs.size() == 1 && cs.get(0).getText() != null) sub = cs.get(0).getText().toString().trim();
                    }
                    return t + "|" + sub;
                }
                row = row.getParent();
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private String topKey() {
        List<AccessibilityNodeInfo> btns = rowButtons();
        sortByY(btns);
        for (AccessibilityNodeInfo b : btns) { String k = rowKey(b); if (k != null) return k; }
        return null;
    }

    private boolean fullyVisible(AccessibilityNodeInfo n) {
        Rect r = bounds(n);
        int h = getResources().getDisplayMetrics().heightPixels;
        return r.width() > 0 && r.top > 60 && r.bottom < h - 60;
    }

    private boolean scrollStep() {
        AccessibilityNodeInfo rv = findId(ID_LIST);
        if (rv == null) return false;
        Rect r = bounds(rv);
        if (r.height() <= 0) return false;
        boolean ok = swipe(r.centerX(), r.top + (int) (r.height() * 0.78),
                           r.centerX(), r.top + (int) (r.height() * 0.45), 700);
        sleep(1300);   // 等惯性停稳，别立刻抓
        return ok;
    }

    private boolean waitText(String text, long ms) {
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < ms) {
            if (!findText(text).isEmpty()) return true;
            sleep(200);
        }
        return false;
    }

    private AccessibilityNodeInfo waitForId(String id, long ms) {
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < ms) {
            AccessibilityNodeInfo n = findId(id);
            if (n != null) return n;
            sleep(250);
        }
        return null;
    }

    private boolean tap(int x, int y) {
        Path p = new Path(); p.moveTo(x, y);
        return dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 120)).build(), null, null);
    }

    private boolean swipe(int x1, int y1, int x2, int y2, long ms) {
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
        return dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null);
    }

    private boolean setRaw(AccessibilityNodeInfo n, String text) {
        if (n == null) return false;
        try {
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        } catch (Throwable t) { return false; }
    }

    private static Rect bounds(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        try { n.getBoundsInScreen(r); } catch (Throwable ignored) { }
        return r;
    }

    private static void sortByY(List<AccessibilityNodeInfo> l) {
        Collections.sort(l, new Comparator<AccessibilityNodeInfo>() {
            @Override public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) { return bounds(a).centerY() - bounds(b).centerY(); }
        });
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }


    /** 系统设置里这个无障碍服务是否被勾选（重装会重置，必须能自检） */
    public static boolean switchOnInSettings(android.content.Context c) {
        try {
            String flat = android.provider.Settings.Secure.getString(
                    c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (flat == null) return false;
            String me = c.getPackageName() + "/" + AutoService.class.getName();
            return flat.toLowerCase().contains(me.toLowerCase());
        } catch (Throwable t) { return false; }
    }

    private void setStatus(String s) { status = s; lastBeat = System.currentTimeMillis(); }

    private void toast(final String s) {
        try {
            new android.os.Handler(getMainLooper()).post(new Runnable() {
                @Override public void run() { Toast.makeText(AutoService.this, s, Toast.LENGTH_SHORT).show(); }
            });
        } catch (Throwable ignored) { }
    }
}

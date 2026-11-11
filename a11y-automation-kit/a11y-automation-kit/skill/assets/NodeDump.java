package com.example.autoclick;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.util.Log;

/**
 * 取控件工具：把这个类加进模板工程，在无障碍里启用它，然后操作目标 App，
 * 每次界面变化都会把节点树打到 logcat（过滤 TAG=DumpNode）。
 *
 * 用法：
 *   1) 复制到 src/com/example/autoclick/NodeDump.java
 *   2) AndroidManifest 里照 AutoService 的写法再加一个 <service android:name=".NodeDump" ...>
 *      注意 meta-data 用一个单独的 xml（把 accessibilityEventTypes 改成 typeWindowStateChanged）
 *   3) adb logcat -s DumpNode
 *
 * 输出格式：
 *   className | id名 | text | desc | [l,t][r,b] | clickable | scrollable
 * 你要的 id 名就在第二列（形如 com.xxx:id/more → 取 more 即可）。
 */
public class NodeDump extends AccessibilityService {

    private static final String TAG = "DumpNode";
    private long lastAt = 0;

    @Override protected void onServiceConnected() { super.onServiceConnected(); Log.i(TAG, "connected"); }
    @Override public void onInterrupt() { }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 界面变化太频繁，2 秒内只打一次，避免刷爆日志
        long now = System.currentTimeMillis();
        if (now - lastAt < 2000) return;
        lastAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        Log.i(TAG, "==== dump start ====");
        dump(root, 0);
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r != root) {
                    Log.i(TAG, "---- window " + w.getType() + " ----");
                    dump(r, 0);
                }
            }
        } catch (Throwable ignored) { }
        Log.i(TAG, "==== dump end ====");
    }

    private void dump(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 14) return;
        try {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            String id = String.valueOf(n.getViewIdResourceName());
            if (id.endsWith("/null")) id = "";
            else if (id.contains(":id/")) id = id.substring(id.indexOf(":id/") + 4);

            Log.i(TAG, pad(depth) + n.getClassName()
                    + " | " + id
                    + " | " + safe(n.getText())
                    + " | " + safe(n.getContentDescription())
                    + " | [" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]"
                    + " | click=" + n.isClickable()
                    + " scroll=" + n.isScrollable()
                    + (n.isEnabled() ? "" : " disabled"));

            for (int i = 0; i < n.getChildCount(); i++) dump(n.getChild(i), depth + 1);
        } catch (Throwable ignored) { }
    }

    private static String safe(CharSequence cs) {
        if (cs == null) return "";
        String s = cs.toString().replace('\n', ' ');
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private static String pad(int d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d; i++) sb.append("  ");
        return sb.toString();
    }
}

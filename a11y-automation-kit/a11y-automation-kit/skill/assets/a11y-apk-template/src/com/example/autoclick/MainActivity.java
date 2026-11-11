package com.example.autoclick;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

/** 最小界面：开始 / 停止 / 打开无障碍设置 / 状态回显 */
public class MainActivity extends Activity {

    private TextView tv;
    private Timer timer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override public void run() {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        String svc = AutoService.instance != null ? "无障碍：已连接" : "无障碍：未连接";
                        String s = AutoService.status;
                        if (AutoService.running) s = "运行中 · " + s;
                        tv.setText(svc + "\n" + s);
                    }
                });
            }
        }, 400, 500);
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 48, 36, 36);

        TextView tip = new TextView(this);
        tip.setText("先手动打开目标应用并停到目标页面，再回来点开始。");
        tip.setTextSize(13);
        root.addView(tip);

        Button start = new Button(this);
        start.setText("开始");
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AutoService.running = false; }
        });
        root.addView(stop);

        Button set = new Button(this);
        set.setText("打开无障碍设置");
        set.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        });
        root.addView(set);

        tv = new TextView(this);
        tv.setText("空闲");
        tv.setTextSize(13);
        tv.setPadding(0, 24, 0, 0);
        root.addView(tv);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        return sv;
    }

    private void start() {
        if (AutoService.instance == null) {
            for (int i = 0; i < 30 && AutoService.instance == null; i++) {
                try { Thread.sleep(100); } catch (Exception ignored) { }
            }
        }
        if (AutoService.instance == null) {
            Toast.makeText(this, AutoService.switchOnInSettings(this)
                    ? "设置里已开启但服务没连上：进去关掉再打开一次"
                    : "无障碍没开：点「打开无障碍设置」打开", Toast.LENGTH_LONG).show();
            if (!AutoService.switchOnInSettings(this)) startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        AutoService.status = "准备中…";
        AutoService.instance.startRun();
    }

    @Override protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }
}

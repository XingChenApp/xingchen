package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.utils.Notify;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fongmi.android.tv.R;

/**
 * 运行记录页（定稿 lab-log.png）：
 * 标题＝服务名，副标题＝运行中（绿）/已停止；
 * 记录卡：每行一个点（绿/黄/红按内容）＋时间＋正文；
 * 底部两白钮：清空记录 / 导出记录（导出进下载目录）。
 * 顶栏只留标题，走系统返回。
 */
public class LabLogActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "id";
    private static final String EXTRA_NAME = "name";
    private static final Pattern TIME_HEAD = Pattern.compile("^(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+(.*)$");

    private String pkgId;
    private String svcName;
    /** 上次已展示的日志全文：定时刷新时内容没变就跳过重建，仅追加时只补新行 */
    private String lastShownLog;
    private TextView stateLine;
    private LinearLayout logBox;
    private ScrollView scroll;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1500);
        }
    };

    public static void start(Activity activity, String pkgId, String name) {
        Intent i = new Intent(activity, LabLogActivity.class);
        i.putExtra(EXTRA_ID, pkgId);
        i.putExtra(EXTRA_NAME, name);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pkgId = getIntent().getStringExtra(EXTRA_ID);
        svcName = getIntent().getStringExtra(EXTRA_NAME);
        LabSkin.edgeToEdge(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 顶栏：只留标题（霜条打底）
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        
        nav.setPadding(dp(18), dp(14) + LabSkin.statusBarHeight(this), dp(18), dp(2));
        TextView title = new TextView(this);
        title.setText(svcName == null ? "运行记录" : svcName);
        title.setTextSize(20);
        title.setTextColor(LabSkin.TEXT_MAIN);
        title.getPaint().setFakeBoldText(true);
        nav.addView(title);
        root.addView(nav);

        stateLine = new TextView(this);
        stateLine.setTextSize(14);
        
        stateLine.setPadding(dp(18), dp(2), dp(18), dp(8));
        root.addView(stateLine);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, 0, 0, dp(16) + LabSkin.navBarHeight(this));

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setBackground(LabSkin.cardBackground(this, false));
        logBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(18), dp(4), dp(18), 0);
        logBox.setLayoutParams(clp);
        wrap.addView(logBox);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(18), dp(14), dp(18), 0);
        btns.setLayoutParams(blp);
        TextView clear = whiteButton("清空记录");
        clear.setOnClickListener(v -> {
            LabRunner.clearLog(this, pkgId);
            refresh();
            Notify.show("清掉了");
        });
        TextView export = whiteButton("导出记录");
        export.setOnClickListener(v -> exportLog());
        btns.addView(clear);
        btns.addView(export);
        wrap.addView(btns);

        scroll.addView(wrap, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private int dp(int v) {
        return LabSkin.dp(this, v);
    }

    private void refresh() {
        boolean running = LabRunner.isRunning(this, pkgId);
        stateLine.setText(running ? "运行中" : "已停止");
        stateLine.setTextColor(running ? LabSkin.GREEN : LabSkin.TEXT_DIM);

        String log = LabRunner.getLog(this, pkgId);
        if (log == null) log = "";
        // 内容没变就不重建：定时刷新 1.5 秒一轮，此前每轮全量重建整页 +
        // 无条件 fullScroll，用户上翻看旧记录会被反复拽回底部
        if (log.equals(lastShownLog)) return;
        boolean appendOnly = lastShownLog != null && !lastShownLog.isEmpty()
                && log.startsWith(lastShownLog);
        int prevLines = appendOnly ? lastShownLog.split("\n").length : 0;
        lastShownLog = log;
        // 改动前测用户是否停在底部附近：只有在底部才跟随滚动
        boolean nearBottom = scroll.getChildCount() == 0
                || scroll.getScrollY() + scroll.getHeight()
                >= scroll.getChildAt(0).getHeight() - dp(48);

        if (log.isEmpty()) {
            logBox.removeAllViews();
            TextView empty = new TextView(this);
            empty.setText("还没有记录，启动一次就有");
            empty.setTextSize(15);
            empty.setTextColor(LabSkin.TEXT_DIM);
            empty.setPadding(0, dp(6), 0, dp(6));
            logBox.addView(empty);
            return;
        }
        String[] lines = log.split("\n");
        int start;
        if (!appendOnly || lines.length - prevLines > 300) {
            logBox.removeAllViews();
            start = Math.max(0, lines.length - 300);
        } else {
            start = prevLines; // 仅追加：只补新行，不重建旧行
        }
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            logBox.addView(logRow(line));
        }
        if (nearBottom) scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /** 记录行：点＋时间＋正文（颜色按内容：红错/黄警告/绿正常） */
    private View logRow(String line) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, dp(7));

        View dot = new View(this);
        dot.setBackground(LabSkin.dot(this, dotColor(line)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.topMargin = dp(6);
        dlp.rightMargin = dp(10);
        dot.setLayoutParams(dlp);
        row.addView(dot);

        String time = null;
        String text = line;
        Matcher m = TIME_HEAD.matcher(line);
        if (m.matches()) {
            time = m.group(1);
            text = m.group(2);
        }
        if (time != null) {
            TextView tv = new TextView(this);
            tv.setText(time);
            tv.setTextSize(14);
            tv.setTextColor(LabSkin.TEXT_DIM);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.rightMargin = dp(12);
            tv.setLayoutParams(tlp);
            row.addView(tv);
        }
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextSize(15);
        body.setTextColor(isSystemLine(text) ? LabSkin.TEXT_DIM : LabSkin.TEXT_MAIN);
        body.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(body);
        return row;
    }

    private int dotColor(String line) {
        String l = line.toLowerCase(Locale.US);
        if (l.contains("失败") || l.contains("错误") || l.contains("异常")
                || l.contains("error") || l.contains("exception") || l.contains("退出")) {
            return LabSkin.RED;
        }
        if (l.contains("重试") || l.contains("警告") || l.contains("warn")) {
            return LabSkin.AMBER;
        }
        return LabSkin.GREEN_DOT;
    }

    private boolean isSystemLine(String text) {
        return text.startsWith("$ ") || text.startsWith("[进程退出");
    }

    private TextView whiteButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(LabSkin.TEXT_MAIN);
        b.setBackground(LabSkin.cardBackground(this, false));
        b.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = dp(7);
        lp.leftMargin = m;
        lp.rightMargin = m;
        b.setLayoutParams(lp);
        return b;
    }

    /** 导出记录：写进下载目录（Q+ 走 MediaStore，Q 以下走 Download 文件） */
    private void exportLog() {
        String log = LabRunner.getLog(this, pkgId);
        if (TextUtils.isEmpty(log)) {
            Notify.show("还没有记录可导出");
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String fileName = "运行记录-" + (svcName == null ? pkgId : svcName) + "-" + stamp + ".txt";
        new Thread(() -> {
            boolean ok = writeDownload(fileName, log);
            runOnUiThread(() -> Notify.show(ok ? "已存到下载目录：" + fileName : "导出失败，换个方式试试"));
        }).start();
    }

    private boolean writeDownload(String fileName, String content) {
        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/星辰实验室");
                ContentResolver cr = getContentResolver();
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (OutputStream out = cr.openOutputStream(uri)) {
                    if (out == null) return false;
                    out.write(data);
                }
                return true;
            }
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "星辰实验室");
            if (!dir.exists() && !dir.mkdirs()) return false;
            try (OutputStream out = new FileOutputStream(new File(dir, fileName))) {
                out.write(data);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

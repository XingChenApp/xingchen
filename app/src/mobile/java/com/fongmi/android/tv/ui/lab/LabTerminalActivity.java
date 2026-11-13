package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.utils.Notify;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 交互终端页（上游 LabTerminalActivity 口径，星辰皮）：
 * 连包的 shell 交互：快捷键条（ESC/TAB/CTRL/↑↓←→）、输入历史上下翻、原始字节写入。
 * 走系统返回；页关了 shell 进程照旧在后台跑（随「全部停止」一起停）。
 */
public class LabTerminalActivity extends AppCompatActivity {

    private static final String EXTRA_PKG = "pkg";
    private static final String EXTRA_TITLE = "title";

    private String pkgId;
    private String runKey;
    /** 当前 shell 进程：pump 线程与 UI 线程都读写，标 volatile；身份判断按实例比对 */
    private volatile Process process;
    /** shell 正在启动中（防重复拉起多个 shell） */
    private volatile boolean shellStarting;
    /** shell 没起来时先存下的待写字节，起来后按序补写（不再回显了却把命令丢掉） */
    private final java.util.List<byte[]> pendingWrites = new java.util.ArrayList<>();
    private TextView output;
    private ScrollView scroll;
    private EditText input;
    private TextView prompt;
    private boolean ctrlMode;
    private int historyIndex = -1;
    private final List<String> commandHistory = new ArrayList<>();
    private final StringBuilder buf = new StringBuilder();

    public static void start(Activity activity, String pkgId, String title) {
        Intent i = new Intent(activity, LabTerminalActivity.class);
        i.putExtra(EXTRA_PKG, pkgId);
        i.putExtra(EXTRA_TITLE, title == null ? "终端" : title);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pkgId = getIntent().getStringExtra(EXTRA_PKG);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "终端";
        LabSkin.edgeToEdge(this);
        buildUi(title);
        append("实验室终端（交互式 shell）\n输入命令后回车执行，例如：ls、echo hello\n\n");
        startShell();

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    @Override
    protected void onDestroy() {
        // 进程留后台，不随页面关
        super.onDestroy();
    }

    private int dp(int v) {
        return LabSkin.dp(this, v);
    }

    private void buildUi(String title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        
        nav.setPadding(dp(18), dp(14) + LabSkin.statusBarHeight(this), dp(18), dp(6));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(20);
        t.setTextColor(LabSkin.TEXT_MAIN);
        t.getPaint().setFakeBoldText(true);
        nav.addView(t);
        root.addView(nav);

        // 输出区
        output = new TextView(this);
        output.setTextSize(13);
        output.setTextColor(LabSkin.TEXT_BODY);
        output.setMovementMethod(new ScrollingMovementMethod());
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LabSkin.cardBackground(this, false));
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        clp.setMargins(dp(18), dp(4), dp(18), 0);
        card.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.addView(card, clp);
        root.addView(scroll);

        // 快捷键条：ESC/TAB/CTRL/↑↓←→
        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        shortcuts.setPadding(dp(18), dp(8), dp(18), 0);
        String[] keys = {"ESC", "TAB", "CTRL", "↑", "↓", "←", "→"};
        for (String k : keys) {
            TextView b = new TextView(this);
            b.setText(k);
            b.setTextSize(12);
            b.setTextColor(LabSkin.TEXT_SUB);
            b.setBackground(LabSkin.buttonGray(this));
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> shortcut(k));
            shortcuts.addView(b);
        }
        root.addView(shortcuts);

        // 输入行：$ ＋ 输入框 ＋ 发送
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(8), dp(18), dp(18) + LabSkin.navBarHeight(this));
        prompt = new TextView(this);
        prompt.setText("$ ");
        prompt.setTextSize(16);
        prompt.setTextColor(LabSkin.GREEN);
        row.addView(prompt);
        input = new EditText(this);
        input.setTextSize(15);
        input.setTextColor(LabSkin.TEXT_MAIN);
        input.setHintTextColor(LabSkin.TEXT_DIM);
        input.setHint("输入命令");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_GO) {
                runInput();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                navigateHistory(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                navigateHistory(1);
                return true;
            }
            return false;
        });
        row.addView(input);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(15);
        send.setTextColor(LabSkin.ON_YELLOW);
        send.setBackground(LabSkin.buttonYellow(this));
        send.setPadding(dp(16), dp(10), dp(16), dp(10));
        send.setOnClickListener(v -> runInput());
        row.addView(send);
        root.addView(row);
        setContentView(root);
    }

    private void startShell() {
        if (shellStarting) return;
        shellStarting = true;
        new Thread(() -> {
            try {
                Process p = LabRunner.startTerminal(this, pkgId);
                process = p;
                runKey = "term/" + pkgId;
                shellStarting = false;
                flushPending();
                pump(p);
            } catch (Exception e) {
                shellStarting = false;
                String m = e.getMessage();
                runOnUiThread(() -> append("[终端起不来：" + (m == null ? "未知原因" : m) + "]\n"));
            }
        }).start();
    }

    /** shell 起来后把排队等写的字节按序补写进去 */
    private void flushPending() {
        java.util.List<byte[]> copy;
        synchronized (pendingWrites) {
            if (pendingWrites.isEmpty()) return;
            copy = new java.util.ArrayList<>(pendingWrites);
            pendingWrites.clear();
        }
        for (byte[] b : copy) {
            try {
                LabRunner.writeStdin(runKey, b);
            } catch (Exception ignored) {
            }
        }
    }

    private void pump(Process p) {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    p.getInputStream(), StandardCharsets.UTF_8))) {
                char[] cbuf = new char[4096];
                int n;
                while ((n = br.read(cbuf)) > 0) {
                    final String s = new String(cbuf, 0, n);
                    runOnUiThread(() -> append(s));
                }
            } catch (Exception ignored) {
            }
            // 只清自己这一代的进程引用：新 shell 已接管时，旧 pump 退出不许把新进程置空
            if (process == p) {
                process = null;
                runOnUiThread(() -> append("\n[shell 退出了，输命令会自动重开]\n"));
            }
        }).start();
    }

    private void append(String s) {
        if (s == null || s.isEmpty()) return;
        buf.append(s);
        if (buf.length() > 200_000) buf.delete(0, buf.length() - 200_000);
        output.setText(buf.toString());
        scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN));
    }

    private void shortcut(String key) {
        switch (key) {
            case "ESC":
                writeRaw(new byte[]{0x1b});
                break;
            case "TAB":
                writeRaw(new byte[]{0x09});
                break;
            case "CTRL":
                ctrlMode = !ctrlMode;
                prompt.setTextColor(ctrlMode ? LabSkin.AMBER : LabSkin.GREEN);
                Notify.show(ctrlMode ? "CTRL 已按下，再输一个字母" : "CTRL 已松开");
                break;
            case "↑":
                navigateHistory(-1);
                break;
            case "↓":
                navigateHistory(1);
                break;
            case "←":
                writeRaw(new byte[]{0x1b, 0x5b, 0x44});
                break;
            case "→":
                writeRaw(new byte[]{0x1b, 0x5b, 0x43});
                break;
            default:
                break;
        }
    }

    private void runInput() {
        String command = input.getText() == null ? "" : input.getText().toString();
        if (command.isEmpty()) return;
        input.setText("");
        if (ctrlMode && command.length() > 0) {
            char c = Character.toLowerCase(command.charAt(0));
            if (c >= 'a' && c <= 'z') {
                writeRaw(new byte[]{(byte) (c - '`')});
                append("^" + Character.toUpperCase(c) + "\n");
            }
            ctrlMode = false;
            prompt.setTextColor(LabSkin.GREEN);
            return;
        }
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
            if (commandHistory.size() > 50) commandHistory.remove(0);
        }
        historyIndex = commandHistory.size();
        append("$ " + command + "\n");
        byte[] bytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            if (process != null && process.isAlive()) {
                LabRunner.writeStdin(runKey, bytes);
            } else {
                // shell 还没起来：命令先排队，起来后补写（此前只回显、命令直接丢）
                synchronized (pendingWrites) {
                    pendingWrites.add(bytes);
                }
                startShell();
            }
        } catch (Exception e) {
            append("[写入失败]\n");
        }
    }

    private void writeRaw(byte[] bytes) {
        try {
            if (process != null && process.isAlive()) {
                LabRunner.writeStdin(runKey, bytes);
            } else {
                synchronized (pendingWrites) {
                    pendingWrites.add(bytes);
                }
                startShell();
            }
        } catch (Exception ignored) {
        }
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        historyIndex += direction;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= commandHistory.size()) {
            historyIndex = commandHistory.size();
            input.setText("");
        } else {
            String text = commandHistory.get(historyIndex);
            input.setText(text);
            input.setSelection(text.length());
        }
    }
}

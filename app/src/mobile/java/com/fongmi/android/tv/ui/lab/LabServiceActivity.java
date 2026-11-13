package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fongmi.android.tv.R;

/**
 * 服务详情页（定稿 lab-design-sets2/a2/service-detail）：
 * 标题＝服务名＋右侧金字环境名；
 * 大深卡＝状态徽＋地址盒＋停止/启动大钮＋运行记录行；
 * 卡下：输出动作黄钮（按产出声明；没声明→「当直播源用/当点播源用」）、
 * 重启/分享/二维码一行深底卡、高级折叠（命令列表＋添加命令＋包设置＋终端＋实时输出＋导出包）。
 * 点地址复制；点行弹变量表运行；自定义命令长按改/删。
 * 顶栏只留标题，走系统返回。
 */
public class LabServiceActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "id";

    private String pkgId;
    /** 包说明读坏只提示一次（peekManifest 多处调用，别每进一处都弹一遍） */
    private boolean manifestBadNotified;
    private LinearLayout body;
    private TextView navTitle;
    private TextView navEnv;

    public static void start(Activity activity, String pkgId) {
        Intent i = new Intent(activity, LabServiceActivity.class);
        i.putExtra(EXTRA_ID, pkgId);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pkgId = getIntent().getStringExtra(EXTRA_ID);
        LabSkin.edgeToEdge(this);
        buildUi();

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
        reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 变量表里的文件/目录选择回填
        LabVarDialog.onPickResult(requestCode, resultCode, data, this);
    }

    private int dp(int v) {
        return LabSkin.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 标题行：服务名＋右侧金字环境名（字直接落在壁纸上，带衬影）
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(18), dp(9) + LabSkin.statusBarHeight(this), dp(18), dp(20));
        navTitle = new TextView(this);
        navTitle.setTextSize(24);
        navTitle.setTextColor(LabSkin.WALL_TITLE);
        navTitle.getPaint().setFakeBoldText(true);
        LabSkin.halo(navTitle);
        navTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nav.addView(navTitle);
        navEnv = new TextView(this);
        navEnv.setTextSize(13);
        navEnv.setTextColor(LabSkin.GOLD_TAG);
        navEnv.getPaint().setFakeBoldText(true);
        LabSkin.halo(navEnv);
        nav.addView(navEnv);
        root.addView(nav);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(46) + LabSkin.navBarHeight(this));
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void reload() {
        body.removeAllViews();
        LabRegistry.Svc s = LabRegistry.get(this).svc(pkgId);
        if (s == null) {
            finish();
            return;
        }
        navTitle.setText(s.name);
        navEnv.setText(LabRegistry.envLabel(LabRegistry.envOf(s)));
        boolean running = LabRunner.isRunning(this, pkgId);
        LabManifest manifest = peekManifest();
        int port = LabRunner.portOf(this, pkgId);
        String addr = manifest == null ? "" : manifest.humanAddress(port);

        body.addView(bigCard(s, running, addr));
        if (!TextUtils.isEmpty(s.needsReview)) {
            body.addView(warnLine(s.needsReview));
        }
        addMainAction(manifest, port, s, running);
        body.addView(utilRow(manifest, port, s));
        body.addView(advancedBox(manifest, s));
    }

    // ---------------- 大深卡（稿 .tile） ----------------

    private View bigCard(LabRegistry.Svc s, boolean running, String addr) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LabSkin.glassDark(this));
        card.setPadding(dp(20), dp(21), dp(20), dp(21));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), 0, dp(18), 0);
        card.setLayoutParams(lp);

        // 状态徽
        TextView chip = new TextView(this);
        chip.setTextSize(12);
        chip.getPaint().setFakeBoldText(true);
        if (running) {
            chip.setText("● 运行中");
            chip.setTextColor(LabSkin.GREEN_ON_DARK);
            chip.setBackground(LabSkin.chipRun(this));
        } else {
            chip.setText("已停止");
            chip.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
            chip.setBackground(LabSkin.chipStop(this));
        }
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(15);
        chip.setLayoutParams(clp);
        card.addView(chip);

        // 地址盒：运行中且有地址才露，点复制
        if (running && !TextUtils.isEmpty(addr)) {
            final String theAddr = addr;
            TextView addrBox = new TextView(this);
            addrBox.setText(theAddr);
            addrBox.setTextSize(13);
            addrBox.setTextColor(LabSkin.TEXT_ON_DARK);
            addrBox.setBackground(LabSkin.addrBox(this));
            addrBox.setPadding(dp(12), dp(11), dp(12), dp(11));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.bottomMargin = dp(18);
            addrBox.setLayoutParams(alp);
            addrBox.setClickable(true);
            addrBox.setFocusable(true);
            addrBox.setOnClickListener(v -> copyText(theAddr));
            card.addView(addrBox);
        }

        // 停止/启动大钮（稿 .bigbtn）
        final TextView big = new TextView(this);
        big.setText(running ? "停止" : "启动");
        big.setTextSize(16);
        big.getPaint().setFakeBoldText(true);
        big.setGravity(Gravity.CENTER);
        big.setTextColor(LabSkin.TEXT_ON_DARK);
        big.setBackground(LabSkin.btnBigDark(this));
        big.setPadding(0, dp(11), 0, dp(11));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(11);
        big.setLayoutParams(blp);
        big.setClickable(true);
        big.setFocusable(true);
        big.setOnClickListener(v -> {
            if (LabRunner.isRunning(this, pkgId)) {
                LabRunner.stop(this, pkgId);
                reload();
            } else {
                big.setText("启动中…");
                big.setEnabled(false);
                LabRunner.start(this, pkgId, new LabRunner.Listener() {
                    @Override
                    public void onOutput(String text) {
                    }

                    @Override
                    public void onStarted() {
                        // 起跑成功：原地翻状态（徽→运行中、大钮→停止可点），不整页重绘
                        runOnUiThread(() -> {
                            chip.setText("● 运行中");
                            chip.setTextColor(LabSkin.GREEN_ON_DARK);
                            chip.setBackground(LabSkin.chipRun(LabServiceActivity.this));
                            big.setText("停止");
                            big.setEnabled(true);
                            surfaceStartOutput(s);
                        });
                    }

                    @Override
                    public void onExit(int code) {
                        runOnUiThread(() -> {
                            if (code != 0 && !LabRunner.isRunning(LabServiceActivity.this, pkgId)) {
                                Notify.show("没起来，去运行记录看看");
                            }
                            reload();
                        });
                    }
                });
            }
        });
        card.addView(big);

        // 运行记录行（稿 .logrow）
        LinearLayout logRow = new LinearLayout(this);
        logRow.setOrientation(LinearLayout.HORIZONTAL);
        logRow.setGravity(Gravity.CENTER_VERTICAL);
        logRow.setBackground(LabSkin.logRowDark(this));
        logRow.setPadding(dp(18), dp(13), dp(18), dp(13));
        TextView logT = new TextView(this);
        logT.setText("运行记录");
        logT.setTextSize(15);
        logT.setTextColor(LabSkin.TEXT_ON_DARK);
        logT.getPaint().setFakeBoldText(true);
        logT.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        logRow.addView(logT);
        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextSize(15);
        chev.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        logRow.addView(chev);
        logRow.setClickable(true);
        logRow.setFocusable(true);
        logRow.setOnClickListener(v -> LabLogActivity.start(this, pkgId, s.name));
        card.addView(logRow);
        return card;
    }

    private View warnLine(String needsReview) {
        TextView warn = new TextView(this);
        warn.setText("导入时有没对上的地方，启动前看一眼：\n" + needsReview);
        warn.setTextSize(13);
        warn.setTextColor(LabSkin.COUNT_YELLOW);
        warn.setBackground(LabSkin.glassDark(this));
        warn.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), dp(10), dp(18), 0);
        warn.setLayoutParams(lp);
        return warn;
    }

    // ---------------- 输出动作黄钮 ----------------

    private void addMainAction(LabManifest manifest, int port, LabRegistry.Svc s, boolean running) {
        boolean needChoice = running && (manifest == null || manifest.output == null
                || TextUtils.isEmpty(manifest.output.kind));
        if (running && !needChoice && manifest != null) {
            TextView big = yellowButton("▶ " + manifest.actionLabel());
            final String url = LabOutputBridge.address(manifest, port);
            big.setOnClickListener(v -> LabOutputBridge.apply(this, manifest, port));
            big.setOnLongClickListener(v -> {
                copyText(url);
                return true;
            });
            body.addView(big);
        } else if (needChoice) {
            TextView b1 = yellowButton("当直播源用");
            b1.setOnClickListener(v -> LabOutputBridge.asLive(this, guessUrl(manifest, port), s.name));
            TextView b2 = yellowButton("当点播源用");
            b2.setOnClickListener(v -> LabOutputBridge.asVod(this, guessUrl(manifest, port), s.name));
            body.addView(b1);
            body.addView(b2);
        }
    }

    private TextView yellowButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.getPaint().setFakeBoldText(true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(LabSkin.ON_YELLOW);
        b.setBackground(LabSkin.buttonYellow(this));
        b.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), dp(14), dp(18), 0);
        b.setLayoutParams(lp);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    // ---------------- 重启 / 分享 / 二维码（一行深底卡） ----------------

    private View utilRow(LabManifest manifest, int port, LabRegistry.Svc s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), dp(12), dp(18), 0);
        row.setLayoutParams(lp);

        TextView restart = utilBtn("重启");
        restart.setOnClickListener(v -> {
            LabRunner.stop(this, pkgId);
            restart.setText("启动中…");
            restart.setEnabled(false);
            LabRunner.start(this, pkgId, new LabRunner.Listener() {
                @Override
                public void onOutput(String text) {
                }

                @Override
                public void onStarted() {
                    // 起跑成功：原地恢复「重启」可点，不整页重绘
                    runOnUiThread(() -> {
                        restart.setText("重启");
                        restart.setEnabled(true);
                        surfaceStartOutput(s);
                    });
                }

                @Override
                public void onExit(int code) {
                    runOnUiThread(() -> reload());
                }
            });
        });
        TextView share = utilBtn("分享");
        share.setOnClickListener(v -> {
            String text = shareText(manifest, port, s);
            if (TextUtils.isEmpty(text)) {
                Notify.show("服务没起来，没有可分享的地址");
                return;
            }
            LabOutputBridge.shareText(this, text);
        });
        TextView qr = utilBtn("二维码");
        qr.setOnClickListener(v -> {
            String text = shareText(manifest, port, s);
            if (TextUtils.isEmpty(text)) {
                Notify.show("服务没起来，没有可分享的地址");
                return;
            }
            showQr(text);
        });
        row.addView(restart);
        row.addView(share);
        row.addView(qr);
        return row;
    }

    private TextView utilBtn(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(14);
        b.getPaint().setFakeBoldText(true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(LabSkin.TEXT_ON_DARK);
        b.setBackground(LabSkin.rowDark(this));
        b.setPadding(0, dp(13), 0, dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        int m = dp(5);
        lp.leftMargin = m;
        lp.rightMargin = m;
        b.setLayoutParams(lp);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private String shareText(LabManifest manifest, int port, LabRegistry.Svc s) {
        if (manifest == null) return "";
        String url = LabOutputBridge.address(manifest, port);
        if (TextUtils.isEmpty(url)) url = manifest.humanAddress(port);
        if (TextUtils.isEmpty(url)) return "";
        return s.name + "\n" + url;
    }

    private void showQr(String text) {
        try {
            Bitmap bmp = LabOutputBridge.qrBitmap(text, dp(280));
            if (bmp == null) {
                Notify.show("二维码画不出来");
                return;
            }
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            int pad = dp(24);
            iv.setPadding(pad, pad, pad, pad);
            LabInstallDialog.showView(this, "扫码打开", iv, "知道了");
        } catch (Exception e) {
            Notify.show("二维码画不出来");
        }
    }

    // ---------------- 高级折叠（深底同语言） ----------------

    private View advancedBox(LabManifest manifest, LabRegistry.Svc s) {
        LinearLayout adv = new LinearLayout(this);
        adv.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(dp(18), dp(12), dp(18), 0);
        adv.setLayoutParams(alp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(LabSkin.rowDark(this));
        head.setPadding(dp(16), dp(13), dp(16), dp(13));
        TextView ht = new TextView(this);
        ht.setText("高级");
        ht.setTextSize(14);
        ht.setTextColor(LabSkin.TEXT_ON_DARK);
        ht.getPaint().setFakeBoldText(true);
        ht.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(ht);
        final TextView chev = new TextView(this);
        chev.setText("▸");
        chev.setTextSize(14);
        chev.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        head.addView(chev);
        adv.addView(head);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setVisibility(View.GONE);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(8);
        inner.setLayoutParams(ilp);

        // 命令列表：包命令＋自定义命令
        List<LabManifest.LabCommand> cmds = new ArrayList<>();
        if (manifest != null) cmds.addAll(manifest.commands);
        List<LabCustomCommands.CustomCommand> customs = LabCustomCommands.list(this, pkgId);
        for (LabCustomCommands.CustomCommand cc : customs) cmds.add(LabCustomCommands.toCommand(cc));
        if (!cmds.isEmpty()) {
            inner.addView(sectionLabel("命令"));
            for (LabManifest.LabCommand c : cmds) {
                inner.addView(commandRow(c, isCustom(c, customs)));
            }
        }

        // 后续操作（上游 clicks）：每条渲染成可点行，按 action 分发处置
        if (manifest != null) {
            boolean clickHead = false;
            for (LabManifest.LabCommand c : manifest.commands) {
                if (c.clicks == null || c.clicks.isEmpty()) continue;
                if (!clickHead) {
                    inner.addView(sectionLabel("后续操作"));
                    clickHead = true;
                }
                for (LabManifest.LabClick cl : c.clicks) inner.addView(clickRow(c, cl));
            }
        }

        inner.addView(advRow("添加命令", () -> showCustomCommandDialog(null)));
        if (manifest != null && !manifest.settings.isEmpty()) {
            inner.addView(advRow("包设置", () -> {
                Map<String, String> cached = LabCustomCommands.settingsOf(this, pkgId);
                LabVarDialog.show(this, "包设置", manifest.settings, cached, "保存",
                        values -> {
                            LabCustomCommands.saveSettings(this, pkgId, values);
                            Notify.show("设置存好了");
                        });
            }));
        }
        inner.addView(advRow("终端", () -> LabTerminalActivity.start(this, pkgId, s.name)));
        inner.addView(advRow("实时输出", () -> LabLiveOutputActivity.start(this, pkgId, s.name)));
        inner.addView(advRow("导出包", this::exportPackage));

        head.setOnClickListener(v -> {
            boolean show = inner.getVisibility() != View.VISIBLE;
            inner.setVisibility(show ? View.VISIBLE : View.GONE);
            chev.setText(show ? "▾" : "▸");
        });
        adv.addView(inner);
        return adv;
    }

    private boolean isCustom(LabManifest.LabCommand c, List<LabCustomCommands.CustomCommand> customs) {
        for (LabCustomCommands.CustomCommand cc : customs) {
            if (cc.id != null && cc.id.equals(c.id)) return true;
        }
        return false;
    }

    /** 命令行：深底卡；点行弹变量表运行；快捷钮 运行/停止；长按改/删（仅自定义命令） */
    private View commandRow(LabManifest.LabCommand c, boolean custom) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(LabSkin.rowDark(this));
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setClickable(true);
        row.setFocusable(true);

        TextView name = new TextView(this);
        name.setText(c.name == null ? c.id : c.name);
        name.setTextSize(14);
        name.setTextColor(LabSkin.TEXT_ON_DARK);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(name);

        boolean running = LabRunner.isCommandRunning(this, pkgId, c.id);
        TextView go = new TextView(this);
        go.setText(running ? "停止" : "运行");
        go.setTextSize(13);
        go.getPaint().setFakeBoldText(true);
        if (running) {
            go.setTextColor(LabSkin.TEXT_ON_DARK);
            go.setBackground(LabSkin.dialogNeutral(this));
        } else {
            go.setTextColor(LabSkin.ON_YELLOW);
            go.setBackground(LabSkin.capsuleYellow(this));
        }
        go.setPadding(dp(16), dp(6), dp(16), dp(6));
        go.setClickable(true);
        go.setFocusable(true);
        go.setOnClickListener(v -> {
            if (LabRunner.isCommandRunning(this, pkgId, c.id)) {
                LabRunner.stopCommand(this, pkgId, c.id);
                reload();
            } else {
                runCommandWithVars(c);
            }
        });
        row.addView(go);

        row.setOnClickListener(v -> runCommandWithVars(c));
        if (custom) {
            row.setOnLongClickListener(v -> {
                showCustomCommandDialog(findCustom(c.id));
                return true;
            });
        }
        return row;
    }

    private View clickRow(LabManifest.LabCommand owner, LabManifest.LabClick cl) {
        TextView row = new TextView(this);
        row.setText((cl.title == null || cl.title.isEmpty()) ? "操作" : cl.title);
        row.setTextSize(14);
        row.setTextColor(LabSkin.TEXT_ON_DARK);
        row.setBackground(LabSkin.rowDark(this));
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> dispatchClick(owner, cl));
        return row;
    }

    /** click 的 value/content 里 {var} 占位：用变量缓存＋包设置＋当前端口展开 */
    private String expandClickText(String text) {
        if (text == null) return "";
        String out = text;
        Map<String, String> vals = new HashMap<>(LabCustomCommands.settingsOf(this, pkgId));
        vals.putAll(LabCustomCommands.varCache(this, pkgId));
        for (Map.Entry<String, String> e : vals.entrySet()) {
            if (e.getValue() != null) out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        LabRegistry.Svc s = LabRegistry.get(this).svc(pkgId);
        if (s != null && s.port > 0) out = out.replace("{port}", String.valueOf(s.port));
        // {dataPath}/{serverPort} 与命令展开同口径补齐（原件 LabActions 有换）：
        // 此前不换，click 值里带这两占位的是原样字面量，open/qrcode/copy 全拿错值。
        // serverPort 走 LabRunner.hostPort 安全取值（没起来先拉起、仍无兜底 9978），不许是 -1
        out = out.replace("{dataPath}",
                new java.io.File(LabPaths.packageDir(this, pkgId), "data").getAbsolutePath());
        out = out.replace("{serverPort}", String.valueOf(LabRunner.hostPort()));
        return out;
    }

    /** clicks 七种样本 action 的处置（＋log/run/stop/restart）；没有对应处置的明说 */
    private void dispatchClick(LabManifest.LabCommand owner, LabManifest.LabClick cl) {
        String action = cl.action == null ? "" : cl.action.trim().toLowerCase();
        String value = expandClickText(cl.value);
        switch (action) {
            case "open": {
                if (TextUtils.isEmpty(value)) { Notify.show("这个按钮没给地址"); return; }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(value)));
                } catch (Exception e) {
                    Notify.show("打不开这个地址");
                }
                return;
            }
            case "webview": {
                // 原件口径是应用内 WebView 弹窗，此前被降级成与 open 一样的外部打开
                if (TextUtils.isEmpty(value) && TextUtils.isEmpty(cl.content)) {
                    Notify.show("这个按钮没给地址");
                    return;
                }
                showWebviewClick(cl, value);
                return;
            }
            case "copy": {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("lab", value));
                Notify.show("复制好了");
                return;
            }
            case "qrcode": {
                if (TextUtils.isEmpty(value)) { Notify.show("这个按钮没给内容"); return; }
                showQr(value);
                return;
            }
            case "share":
                LabOutputBridge.shareText(this, value);
                return;
            case "alert": {
                String msg = value;
                if (cl.content != null && !cl.content.isEmpty()) {
                    msg = msg + (msg.isEmpty() ? "" : "\n") + expandClickText(cl.content);
                }
                LabInstallDialog.showConfirm(this,
                        (cl.title == null || cl.title.isEmpty()) ? "说明" : cl.title, msg, "知道了", () -> { });
                return;
            }
            case "toast":
                Notify.show(value);
                return;
            case "log":
                LabLogActivity.start(this, pkgId, owner.name == null ? "" : owner.name);
                return;
            case "run":
                runCommandWithVars(owner);
                return;
            case "stop":
                LabRunner.stopCommand(this, pkgId, owner.id);
                reload();
                return;
            case "restart":
                LabRunner.stopCommand(this, pkgId, owner.id);
                runCommandWithVars(owner);
                return;
            default:
                Notify.show("这个按钮的动作（" + action + "）星辰还没有对应处置");
        }
    }

    /**
     * webview 动作（原件口径）：应用内 WebView 弹窗，不跳外部浏览器。
     * LabClick 收进来的 style/width/height/content 在这里全消费：content 非空
     * 按 HTML 载入（value 作基址让相对链接能解析）、空则直接载 value 的地址；
     * width/height 见 clickDim；style 含 full 时按近全屏走。
     * 弹窗壳复用 LabInstallDialog.showView 的深底壳（实验室弹窗定案 UI），
     * 不另起一套；它会把直传 View 的布局改成 MATCH_PARENT/WRAP_CONTENT，
     * 故 WebView 先套一层 FrameLayout 保住自己的宽高。
     */
    private void showWebviewClick(LabManifest.LabClick cl, String value) {
        try {
            android.webkit.WebView wv = new android.webkit.WebView(this);
            android.webkit.WebSettings ws = wv.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            wv.setWebViewClient(new android.webkit.WebViewClient());
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            boolean full = cl.style != null && cl.style.toLowerCase().contains("full");
            int w = full ? (int) (screenW * 0.96f) : clickDim(cl.width, screenW, (int) (screenW * 0.92f));
            int h = full ? (int) (screenH * 0.78f) : clickDim(cl.height, screenH, dp(360));
            wv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(w, h));
            android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
            holder.addView(wv);
            String content = cl.content == null ? "" : expandClickText(cl.content);
            if (!content.isEmpty()) {
                wv.loadDataWithBaseURL(TextUtils.isEmpty(value) ? null : value,
                        content, "text/html", "UTF-8", null);
            } else {
                wv.loadUrl(value);
            }
            LabInstallDialog.showView(this,
                    (cl.title == null || cl.title.isEmpty()) ? "网页" : cl.title, holder, "关闭");
        } catch (Exception e) {
            Notify.show("这个网页打不开");
        }
    }

    /**
     * click 的 width/height 取值（纯逻辑）：带 % 按屏幕占比；纯数字 ≤1 按占比、
     * >1 按 dp；带 dp/px 后缀照字面；空/解析不了/≤0 用缺省。结果夹在
     * [48dp, 屏幕尺寸] 内，不让包声明把弹窗撑出屏。
     */
    int clickDim(String raw, int screenPx, int fallbackPx) {
        int px = clickDimPx(raw, screenPx, fallbackPx,
                getResources().getDisplayMetrics().density);
        int min = (int) (48 * getResources().getDisplayMetrics().density);
        return Math.max(min, Math.min(px, screenPx));
    }

    static int clickDimPx(String raw, int screenPx, int fallbackPx, float density) {
        if (raw == null) return fallbackPx;
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) return fallbackPx;
        try {
            if (s.endsWith("%")) {
                double pct = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return pct > 0 ? (int) (screenPx * pct / 100.0) : fallbackPx;
            }
            boolean pxUnit = s.endsWith("px");
            if (s.endsWith("dp") || pxUnit) s = s.substring(0, s.length() - 2).trim();
            double v = Double.parseDouble(s);
            if (v <= 0) return fallbackPx;
            if (pxUnit) return (int) v;
            if (v <= 1) return (int) (screenPx * v);
            return (int) (v * density);
        } catch (Exception e) {
            return fallbackPx;
        }
    }

    private LabCustomCommands.CustomCommand findCustom(String id) {
        for (LabCustomCommands.CustomCommand cc : LabCustomCommands.list(this, pkgId)) {
            if (cc.id != null && cc.id.equals(id)) return cc;
        }
        return null;
    }

    /** 点行运行：有变量先弹表，没变量直接跑（变量值进缓存，下次默认带上） */
    private void runCommandWithVars(LabManifest.LabCommand c) {
        List<LabManifest.LabVar> vars = c.variables == null
                ? new ArrayList<>() : c.variables;
        if (vars.isEmpty()) {
            surfaceCommandOutput(c);
            LabRunner.startCommand(this, pkgId, c, null, cmdListener());
            return;
        }
        Map<String, String> cached = new HashMap<>(LabCustomCommands.varCache(this, pkgId));
        LabVarDialog.show(this, c.name == null ? "填参数" : c.name, vars, cached, "运行",
                values -> {
                    LabCustomCommands.saveVarCache(this, pkgId, values);
                    surfaceCommandOutput(c);
                    LabRunner.startCommand(this, pkgId, c, values, cmdListener());
                });
    }

    /**
     * 命令运行后的输出口径（对齐上游 show_output）：
     * 后台型命令（background=true）维持现行——状态在服务页和运行记录里看；
     * 前台型命令 show_output=true：先开这条命令的实时输出页再起进程，输出一行不漏；
     * show_output=false：只提示「已在后台运行」，不弹输出（上游原话口径）。
     */
    private void surfaceCommandOutput(LabManifest.LabCommand c) {
        if (c.background) {
            Notify.show("跑起来了，去实时输出看");
            return;
        }
        if (c.showOutput) {
            LabLiveOutputActivity.start(this, pkgId, c.id, c.name == null ? "实时输出" : c.name);
        } else {
            Notify.show("已在后台运行：" + (c.name == null ? c.id : c.name));
        }
    }

    /** 大钮/重启起的是默认命令：输出口径与命令运行一致，页按服务级打开 */
    private void surfaceStartOutput(LabRegistry.Svc s) {
        LabManifest m = peekManifest();
        LabManifest.LabCommand def = m == null ? null : m.defaultCommand();
        if (def == null || def.background) return;
        if (def.showOutput) {
            LabLiveOutputActivity.start(this, pkgId, s.name);
        } else {
            Notify.show("已在后台运行：" + s.name);
        }
    }

    private LabRunner.Listener cmdListener() {
        return new LabRunner.Listener() {
            @Override
            public void onOutput(String text) {
            }

            @Override
            public void onExit(int code) {
                runOnUiThread(() -> reload());
            }
        };
    }

    /** 添加/编辑自定义命令（深底表单弹窗） */
    private void showCustomCommandDialog(LabCustomCommands.CustomCommand old) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText nameEt = dlgEdit(box, "名字", old == null ? "" : old.name);
        EditText cmdEt = dlgEdit(box, "命令", old == null ? "" : old.command);
        cmdEt.setMinLines(3);

        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(8), 0, dp(8));
        TextView swLab = new TextView(this);
        swLab.setText("开机自动运行");
        swLab.setTextSize(15);
        swLab.setTextColor(LabSkin.TEXT_ON_DARK);
        swLab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(old != null && old.autoExecute);
        swRow.addView(swLab);
        swRow.addView(sw);
        box.addView(swRow);
        // 自启门槛明示（2026-10-29 口径）：没有总开关，只看这条命令自己的
        // 开关——开着，星辰一启动就会把它拉起来
        TextView gateHint = new TextView(this);
        gateHint.setText("这个开关开着时，星辰一启动就会把这条命令拉起来");
        gateHint.setTextSize(12);
        gateHint.setTextColor(LabSkin.HINT_ON_DARK);
        gateHint.setPadding(0, 0, 0, dp(8));
        box.addView(gateHint);

        LabInstallDialog.showForm(this, old == null ? "添加命令" : "改命令", box,
                "保存", () -> {
                    String name = nameEt.getText().toString().trim();
                    String command = cmdEt.getText().toString().trim();
                    if (TextUtils.isEmpty(command)) {
                        Notify.show("命令不能为空");
                        return false;
                    }
                    LabCustomCommands.CustomCommand cc = old == null
                            ? new LabCustomCommands.CustomCommand() : old;
                    if (cc.id == null) cc.id = "custom-" + System.currentTimeMillis();
                    if (TextUtils.isEmpty(name)) name = "自定义命令";
                    cc.name = name;
                    cc.command = command;
                    cc.autoExecute = sw.isChecked();
                    if (old == null) LabCustomCommands.add(this, pkgId, cc);
                    else LabCustomCommands.update(this, pkgId, cc);
                    Notify.show("存好了");
                    reload();
                    return true;
                },
                old == null ? null : "删除", () -> {
                    LabRunner.stopCommand(this, pkgId, old.id);
                    LabCustomCommands.remove(this, pkgId, old.id);
                    Notify.show("删掉了");
                    reload();
                });
    }

    private EditText dlgEdit(LinearLayout box, String hint, String value) {
        EditText et = LabInstallDialog.darkEdit(this, hint, value);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        box.addView(et, lp);
        return et;
    }

    private interface RowAction {
        void run();
    }

    private View advRow(String label, RowAction action) {
        TextView t = new TextView(this);
        t.setText(label + " ›");
        t.setTextSize(14);
        t.setTextColor(LabSkin.TEXT_ON_DARK);
        t.setBackground(LabSkin.rowDark(this));
        t.setPadding(dp(16), dp(13), dp(16), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        lp.topMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private void exportPackage() {
        Notify.show("正在打包…");
        LabPackageInstaller.export(this, pkgId, new LabPackageInstaller.ExportListener() {
            @Override
            public void onOk(String fileName) {
                runOnUiThread(() -> Notify.show("包已存到下载目录：" + fileName));
            }

            @Override
            public void onFail(String reason) {
                runOnUiThread(() -> Notify.show(reason));
            }
        });
    }

    // ---------------- 小件 ----------------

    private LabManifest peekManifest() {
        try {
            java.io.File mf = LabPaths.manifestOf(this, pkgId);
            if (!mf.exists()) return null;
            java.io.FileInputStream in = new java.io.FileInputStream(mf);
            byte[] buf = new byte[(int) mf.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            in.close();
            // 与 LabRunner.readManifest 同口径：缺 id 时按包目录名派生，不整份读死
            return LabManifest.parse(new String(buf, 0, n, "UTF-8"), pkgId);
        } catch (Exception e) {
            // 文件不存在已在上面静默返回；走到这里是文件在但读坏/解坏，留痕 + 报因一次，
            // 别再静默回 null 让页面装作没这个包
            android.util.Log.e("LabService", "包说明读不出来 pkg=" + pkgId, e);
            if (!manifestBadNotified) {
                manifestBadNotified = true;
                String m = e.getMessage();
                Notify.show("包说明读不出来：" + (m == null ? "内容损坏" : m));
            }
            return null;
        }
    }

    private String guessUrl(LabManifest m, int port) {
        if (m != null && m.output != null && !TextUtils.isEmpty(m.output.target)) {
            return m.output.target.replace("{port}", String.valueOf(port));
        }
        return "http://127.0.0.1:" + port + "/";
    }

    private void copyText(String text) {
        if (TextUtils.isEmpty(text)) return;
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("lab", text));
        Notify.show("地址已复制");
    }
}

package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.utils.Notify;

import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 环境页（定稿 lab-design-sets2/a2/env-uninstalled + env-installed）：
 * 标题行＝环境名＋安装/卸载小胶囊紧跟名字＋行尾「＋」（加服务，二选一）；
 * 版本行＝已装版本号小字，没装不显示；
 * 副行只写「X 个服务」（已装：「已安装 · X 个服务」）；
 * 服务＝两列深玻璃格（名＋状态徽＋启/停钮＋自启开关），点格进服务详情，长按卸载服务。
 * 没介绍文字。顶栏只留标题，走系统返回。
 */
public class LabEnvActivity extends AppCompatActivity {

    private static final String EXTRA_ENV = "env";
    private static final int REQ_RT = 0x2101;

    private String env;
    private LinearLayout listBox;
    private TextView headAction;
    private TextView subLine;
    private TextView addBtn;
    private TextView versionLine;

    public static void start(Activity activity, String env) {
        Intent i = new Intent(activity, LabEnvActivity.class);
        i.putExtra(EXTRA_ENV, env);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        env = getIntent().getStringExtra(EXTRA_ENV);
        if (env == null) env = "tool";
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

    private int dp(int v) {
        return LabSkin.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 标题行：环境名＋安装/卸载胶囊紧跟＋行尾「＋」
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(18), dp(9) + LabSkin.statusBarHeight(this), dp(18), dp(5));
        TextView title = new TextView(this);
        title.setText(LabRegistry.envLabel(env));
        title.setTextSize(24);
        title.setTextColor(LabSkin.WALL_TITLE);
        title.getPaint().setFakeBoldText(true);
        LabSkin.halo(title);
        nav.addView(title);
        headAction = new TextView(this);
        headAction.setTextSize(12);
        headAction.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = dp(11);
        headAction.setLayoutParams(hlp);
        headAction.setClickable(true);
        headAction.setFocusable(true);
        nav.addView(headAction);
        LinearLayout spacer = new LinearLayout(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nav.addView(spacer);
        addBtn = new TextView(this);
        addBtn.setText("＋");
        addBtn.setTextSize(23);
        addBtn.setTextColor(LabSkin.WALL_DIM);
        LabSkin.halo(addBtn);
        addBtn.setPadding(dp(12), dp(4), dp(4), dp(4));
        addBtn.setClickable(true);
        addBtn.setFocusable(true);
        addBtn.setOnClickListener(v -> LabAddMenu.show(this, env));
        nav.addView(addBtn);
        root.addView(nav);

        // 版本行：已装才显示（有新版时「旧 → 新」），没装隐藏；小字次要色，不抢名字
        versionLine = new TextView(this);
        versionLine.setTextSize(12);
        versionLine.setTextColor(LabSkin.WALL_DIM);
        LabSkin.halo(versionLine);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(18);
        vlp.bottomMargin = dp(6);
        versionLine.setLayoutParams(vlp);
        versionLine.setVisibility(View.GONE);
        root.addView(versionLine);

        // 副行：只写「X 个服务」
        subLine = new TextView(this);
        subLine.setTextSize(13);
        subLine.setTextColor(LabSkin.WALL_SUB);
        LabSkin.halo(subLine);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(18);
        slp.bottomMargin = dp(20);
        subLine.setLayoutParams(slp);
        root.addView(subLine);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, 0, 0, dp(46) + LabSkin.navBarHeight(this));
        scroll.addView(listBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void reload() {
        listBox.removeAllViews();
        boolean installed = !"tool".equals(env) && LabRuntimeManager.isInstalled(this, env);
        List<LabRegistry.Svc> svcs = LabRegistry.get(this).services(env);
        // 头部动作：没装→黄色「安装」；已装→中性「卸载」（卸载确认照稿弹窗）。
        // 更新检测已整套摘除（他亲令：版本比对永远不收敛、装到最新仍报可更新），
        // 已装环境不再算「可更新」、不摆「更新」钮；要换版本先卸载再装。
        if (installed) {
            headAction.setText("卸载");
            headAction.setTextColor(LabSkin.WALL_DIM);
            headAction.setBackground(LabSkin.capsuleNeutral(this));
            headAction.setPadding(dp(18), dp(6), dp(18), dp(6));
            headAction.setOnClickListener(v -> LabInstallDialog.showConfirm(this,
                    "卸载「" + LabRegistry.envLabel(env) + "」？",
                    "依赖它的服务会停掉，环境文件删干净。", "卸载", () -> {
                        LabRuntimeManager.uninstall(this, env);
                        Notify.show("卸掉了");
                        reload();
                    }));
            subLine.setText("已安装 · " + svcs.size() + " 个服务");
            // 版本行：只显示已装版本
            String iv = LabRuntimeManager.installedVersion(this, env);
            if (!iv.isEmpty()) {
                versionLine.setText(iv);
                versionLine.setVisibility(View.VISIBLE);
            } else {
                versionLine.setVisibility(View.GONE);
            }
        } else {
            headAction.setText("安装");
            headAction.setTextColor(LabSkin.ON_YELLOW);
            headAction.setBackground(LabSkin.capsuleYellow(this));
            headAction.setPadding(dp(18), dp(6), dp(18), dp(6));
            headAction.setOnClickListener(v -> LabInstallDialog.showChoice(this,
                    "安装「" + LabRegistry.envLabel(env) + "」",
                    new String[]{"在线安装", "本地安装"}, which -> {
                        if (which == 0) installOnline();
                        else pickLocalZip();
                    }));
            subLine.setText(svcs.size() + " 个服务");
            versionLine.setVisibility(View.GONE);
        }
        // 服务两列格
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setPadding(dp(18), 0, dp(18), 0);
        for (LabRegistry.Svc s : svcs) {
            grid.addView(serviceTile(s));
        }
        listBox.addView(grid);
    }

    /** 两列格定宽：屏宽减网格左右边距（18dp×2）后对半成格，再减格子左右 margin（5dp×2）即卡宽；格位与卡宽不随服务数量变，单个服务只占第一格 */
    private int tileWidth() {
        int content = getResources().getDisplayMetrics().widthPixels - dp(36);
        return content / 2 - dp(10);
    }

    /** 服务格：深玻璃＋名＋状态徽＋启/停钮＋自启开关＋行尾节点开关（稿 env-installed .tile） */
    private View serviceTile(LabRegistry.Svc s) {
        boolean running = LabRunner.isRunning(this, s.id);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setBackground(LabSkin.glassDark(this));
        tile.setPadding(dp(13), dp(14), dp(13), dp(14));
        // 定宽定高（用户点名「卡片固定」）：宽按屏宽算死（tileWidth），高定 158dp
        // （＝卡内定高件之和、上一件的自然内容高）；不许再用权重列＋WRAP_CONTENT——
        // 只剩一个服务时权重列会把整行宽都给它，卡被横向拉满整行（截图 113147 实证）
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = tileWidth();
        lp.height = dp(158);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
        int gap = dp(5);
        lp.setMargins(gap, 0, gap, dp(9));
        tile.setLayoutParams(lp);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(v -> LabServiceActivity.start(this, s.id));
        tile.setOnLongClickListener(v -> {
            confirmUninstall(s);
            return true;
        });

        TextView name = new TextView(this);
        name.setText(s.name);
        name.setTextSize(15);
        name.setTextColor(LabSkin.TEXT_ON_DARK);
        name.getPaint().setFakeBoldText(true);
        // 标题单行：长了跑马灯滚动（与详情页长标题同口径），短标题不动；
        // 不许再换行把卡片撑高
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        name.setMarqueeRepeatLimit(-1);
        name.setHorizontallyScrolling(true);
        name.setSelected(true);
        name.setPadding(0, 0, 0, dp(10));
        tile.addView(name);

        TextView chip = new TextView(this);
        chip.setTextSize(11);
        chip.getPaint().setFakeBoldText(true);
        if (running) {
            chip.setText("运行中");
            chip.setTextColor(LabSkin.GREEN_ON_DARK);
            chip.setBackground(LabSkin.chipRun(this));
        } else {
            chip.setText("已停止");
            chip.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
            chip.setBackground(LabSkin.chipStop(this));
        }
        chip.setPadding(dp(11), dp(4), dp(11), dp(4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(11);
        chip.setLayoutParams(clp);
        tile.addView(chip);

        // 启动钮独占一行（整宽居中）：原先与自启/节点开关挤同一行，卡宽放不下
        // 三样，节点开关被挤出卡外。新排布：钮一行、开关一行（见下），三样全露。
        TextView go = new TextView(this);
        go.setText(running ? "停止" : "启动");
        go.setTextSize(12);
        go.getPaint().setFakeBoldText(true);
        go.setGravity(Gravity.CENTER);
        if (running) {
            go.setTextColor(LabSkin.TEXT_ON_DARK);
            go.setBackground(LabSkin.rowDark(this));
        } else {
            go.setTextColor(LabSkin.ON_YELLOW);
            go.setBackground(LabSkin.capsuleYellow(this));
        }
        go.setPadding(0, dp(8), 0, dp(8));
        go.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        go.setClickable(true);
        go.setFocusable(true);
        go.setOnClickListener(v -> {
            if (LabRunner.isRunning(this, s.id)) {
                LabRunner.stop(this, s.id);
                reload();
            } else {
                go.setText("启动中…");
                go.setEnabled(false);
                LabRunner.start(this, s.id, new LabRunner.Listener() {
                    @Override
                    public void onOutput(String text) {
                    }

                    @Override
                    public void onStarted() {
                        // 起跑成功：原地翻卡（徽→运行中、钮→停止可点），不整页重绘
                        runOnUiThread(() -> markTileRunning(chip, go));
                    }

                    @Override
                    public void onExit(int code) {
                        runOnUiThread(() -> {
                            if (code != 0 && !LabRunner.isRunning(LabEnvActivity.this, s.id)) {
                                Notify.show("没起来，去运行记录看看");
                            }
                            reload();
                        });
                    }
                });
            }
        });
        tile.addView(go);

        // 开关行：自启、节点两组各占半行（标签＋开关），组内贴紧、两组分列左右，
        // 不再横排硬挤。语义与存法：自启拨动即存为用户覆盖（Svc.autoStart），
        // 没拨过时开关初始态与 LabBoot 拉起判定同源 autoStartOf——默认跟包内
        // auto_execute 标记（落 Svc.autoStartDefault）走；节点拨动即存，
        // 对正在跑的服务下次启动生效（不强制重启）。
        // 注：自启组曾于 2026-10-25 被助手误判摘除（26e752726），2026-10-29
        // 按他的原意装回；同行节点组是另一功能（启动注入本机节点代理），勿混。
        LinearLayout swRow = new LinearLayout(this);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams swRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swRowLp.topMargin = dp(11);
        swRow.setLayoutParams(swRowLp);

        LinearLayout autoGroup = new LinearLayout(this);
        autoGroup.setOrientation(LinearLayout.HORIZONTAL);
        autoGroup.setGravity(Gravity.CENTER_VERTICAL);
        autoGroup.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView swLab = new TextView(this);
        swLab.setText("自启");
        swLab.setTextSize(11);
        swLab.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        autoGroup.addView(swLab);
        View sw = LabSkin.miniSwitch(this, LabRegistry.autoStartOf(s), on -> {
            LabRegistry.Svc cur = LabRegistry.get(this).svc(s.id);
            if (cur != null) {
                cur.autoStart = on;
                LabRegistry.get(this).upsertSvc(this, cur);
            }
        });
        // 开关自带定尺寸 LayoutParams，不许覆盖；标签与开关之间用 5dp 间隔件
        View swGap = new View(this);
        swGap.setLayoutParams(new LinearLayout.LayoutParams(dp(5), 1));
        autoGroup.addView(swGap);
        autoGroup.addView(sw);
        swRow.addView(autoGroup);

        LinearLayout nodeGroup = new LinearLayout(this);
        nodeGroup.setOrientation(LinearLayout.HORIZONTAL);
        nodeGroup.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        nodeGroup.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView nodeLab = new TextView(this);
        nodeLab.setText("节点");
        nodeLab.setTextSize(11);
        nodeLab.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        nodeGroup.addView(nodeLab);
        View nodeSw = LabSkin.miniSwitch(this, s.nodeProxy, on -> {
            LabRegistry.Svc cur = LabRegistry.get(this).svc(s.id);
            if (cur != null) {
                cur.nodeProxy = on;
                LabRegistry.get(this).upsertSvc(this, cur);
            }
        });
        View nodeGap = new View(this);
        nodeGap.setLayoutParams(new LinearLayout.LayoutParams(dp(5), 1));
        nodeGroup.addView(nodeGap);
        nodeGroup.addView(nodeSw);
        swRow.addView(nodeGroup);
        tile.addView(swRow);
        return tile;
    }

    /** 启动成功后原地翻卡：徽→运行中、钮→停止可点，不整页重绘 */
    private void markTileRunning(TextView chip, TextView go) {
        chip.setText("运行中");
        chip.setTextColor(LabSkin.GREEN_ON_DARK);
        chip.setBackground(LabSkin.chipRun(this));
        go.setText("停止");
        go.setTextColor(LabSkin.TEXT_ON_DARK);
        go.setBackground(LabSkin.rowDark(this));
        go.setEnabled(true);
    }

    private void confirmUninstall(LabRegistry.Svc s) {
        LabInstallDialog.showConfirm(this,
                "卸载「" + s.name + "」？", "服务会停掉，包文件删干净。", "卸载", () -> {
                    LabPackageInstaller.uninstall(this, s.id);
                    reload();
                });
    }

    // ---------------- 安装管线（上一批已对，不动逻辑） ----------------

    /** 本地安装：选本地 zip 文件，走环境安装管线 */
    private void pickLocalZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_RT);
    }

    /** 在线安装：统一入口（目录制/回填表/分片/镜像/跨源重试都在 LabRuntimeManager 里），照稿弹安装中弹窗 */
    private void installOnline() {
        LabInstallDialog.Installing dlg = LabInstallDialog.showInstalling(
                this, "正在安装「" + LabRegistry.envLabel(env) + "」");
        dlg.setOnCancel(() -> LabRuntimeManager.cancelInstall(env));
        Runnable[] retry = new Runnable[1];
        retry[0] = () -> LabRuntimeManager.installOnline(this, env, installListener(dlg, retry[0]));
        retry[0].run();
    }

    /** 本地 zip 安装：同样走安装中弹窗，失败可再试一次 */
    private void importLocalZip(Uri uri) {
        LabInstallDialog.Installing dlg = LabInstallDialog.showInstalling(
                this, "正在安装「" + LabRegistry.envLabel(env) + "」");
        dlg.setOnCancel(() -> LabRuntimeManager.cancelInstall(env));
        Runnable[] retry = new Runnable[1];
        retry[0] = () -> LabRuntimeManager.importFromUri(this, env, uri, installListener(dlg, retry[0]));
        retry[0].run();
    }

    private LabRuntimeManager.Listener installListener(LabInstallDialog.Installing dlg, Runnable retry) {
        return new LabRuntimeManager.Listener() {
            @Override
            public void onProgress(String text) {
                String pct = LabInstallDialog.shardLine(text);
                if (pct != null) dlg.progress(pct, text);
                else dlg.busy(text);
            }

            @Override
            public void onShard(int done, int total) {
                int pct = total <= 0 ? 0 : Math.min(99, done * 100 / total);
                dlg.progress("第 " + done + " 片 / 共 " + total + " 片 · " + pct + "%",
                        "正在下载第 " + done + " 片…");
            }

            @Override
            public void onOk() {
                runOnUiThread(() -> {
                    dlg.ok();
                    Notify.show("环境装好了");
                    reload();
                });
            }

            @Override
            public void onFail(String reason) {
                runOnUiThread(() -> dlg.error(reason, retry));
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    dlg.cancelled();
                    Notify.show("已取消安装");
                    reload();
                });
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_RT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importLocalZip(data.getData());
        } else if (requestCode == LabInstallActivity.REQ_PICK && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            // 环境页「＋」→选文件：默认落当前环境
            LabInstallActivity.startWithUri(this, data.getData(), env);
        }
    }
}

package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.utils.Notify;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.fongmi.android.tv.R;

/**
 * .lab 安装确认页：文件打开方式（点 .lab 文件拉起）＋首页「＋」选文件的落点。
 * 先展示包说明（名字/版本/简介/要什么环境），点「安装」才装。
 * 不设叉，走系统返回。
 */
public class LabInstallActivity extends AppCompatActivity {

    public static final int REQ_PICK = 0x2102;
    private static final String EXTRA_URI = "uri";
    private static final String EXTRA_DEFAULT_ENV = "default_env";

    private Uri uri;
    private LinearLayout body;
    private TextView progressText;

    /** 首页「＋」：选 .lab 文件 */
    public static void pickFile(Activity activity) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        activity.startActivityForResult(i, REQ_PICK);
    }

    public static void startWithUri(Activity activity, Uri uri) {
        startWithUri(activity, uri, null);
    }

    /** 环境页「＋」→选文件：默认落当前环境（包没声明运行时才用） */
    public static void startWithUri(Activity activity, Uri uri, String defaultEnv) {
        Intent i = new Intent(activity, LabInstallActivity.class);
        i.setData(uri);
        if (defaultEnv != null) i.putExtra(EXTRA_DEFAULT_ENV, defaultEnv);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uri = getIntent().getData();
        if (uri == null && getIntent().hasExtra(EXTRA_URI)) {
            uri = Uri.parse(getIntent().getStringExtra(EXTRA_URI));
        }
        LabSkin.edgeToEdge(this);
        buildUi();
        if (uri != null) peek();
        else {
            progressText.setText("没拿到文件，重选一次");
            progressText.setVisibility(View.VISIBLE);
        }

        // 页面进出统一渐入渐出（与 BaseActivity 一致）
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
}

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.xc_fade_in, R.anim.xc_fade_out);
    }


    private int dp(int v) {
        return LabSkin.dp(this, v);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        
        nav.setPadding(dp(18), dp(14) + LabSkin.statusBarHeight(this), dp(18), dp(6));
        TextView title = new TextView(this);
        title.setText("安装服务包");
        title.setTextSize(20);
        title.setTextColor(LabSkin.TEXT_MAIN);
        title.getPaint().setFakeBoldText(true);
        nav.addView(title);
        root.addView(nav);

        progressText = new TextView(this);
        progressText.setTextSize(14);
        progressText.setTextColor(LabSkin.AMBER);
        
        progressText.setPadding(dp(18), dp(8), dp(18), dp(8));
        progressText.setVisibility(View.GONE);
        root.addView(progressText);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(8), 0, dp(24) + LabSkin.navBarHeight(this));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void peek() {
        new Thread(() -> {
            try {
                File tmp = copyToTemp(uri);
                if (!isZip(tmp)) {
                    // 文本类（服务清单/命令条目/脚本）：转粘贴导入页按内容识别
                    tmp.delete();
                    runOnUiThread(() -> {
                        LabPasteImportActivity.startWithUri(this, uri);
                        finish();
                    });
                    return;
                }
                final LabManifest m = LabPackageInstaller.peekManifest(tmp);
                tmp.delete();
                runOnUiThread(() -> showConfirm(m));
            } catch (Exception e) {
                String msg = e.getMessage();
                runOnUiThread(() -> {
                    progressText.setVisibility(View.VISIBLE);
                    progressText.setText(msg == null ? "这个包打不开" : msg);
                });
            }
        }).start();
    }

    /** zip 魔数 PK\x03\x04 */
    private boolean isZip(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] head = new byte[4];
            if (in.read(head) < 4) return false;
            return head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    private void showConfirm(LabManifest m) {
        body.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LabSkin.cardBackground(this, false));
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(18), dp(8), dp(18), 0);
        card.setLayoutParams(clp);

        TextView name = new TextView(this);
        name.setText(m.name);
        name.setTextSize(20);
        name.setTextColor(LabSkin.TEXT_MAIN);
        name.getPaint().setFakeBoldText(true);
        card.addView(name);

        TextView ver = new TextView(this);
        ver.setText("版本 " + m.version);
        ver.setTextSize(13);
        ver.setTextColor(LabSkin.TEXT_DIM);
        ver.setPadding(0, dp(6), 0, 0);
        card.addView(ver);

        TextView desc = new TextView(this);
        desc.setText(m.desc == null ? "" : m.desc);
        desc.setTextSize(15);
        desc.setTextColor(LabSkin.TEXT_SUB);
        desc.setPadding(0, dp(10), 0, 0);
        card.addView(desc);

        TextView rt = new TextView(this);
        String need = "none".equals(m.runtime) ? "不用装环境，装上就能跑"
                : "需要" + LabRuntimeManager.label(m.runtime) + "（没装的话先进环境页装上）";
        rt.setText("环境要求：" + need);
        rt.setTextSize(14);
        rt.setTextColor(LabSkin.TEXT_SUB);
        rt.setPadding(0, dp(12), 0, 0);
        card.addView(rt);
        body.addView(card);

        TextView install = new TextView(this);
        install.setText("安装");
        install.setTextSize(18);
        install.getPaint().setFakeBoldText(true);
        install.setGravity(Gravity.CENTER);
        install.setTextColor(0xFF171310);
        install.setBackground(LabSkin.buttonYellow(this));
        install.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(18), dp(16), dp(18), 0);
        install.setLayoutParams(blp);
        install.setOnClickListener(v -> startInstall(uri, install));
        body.addView(install);
    }

    /** 走安装中弹窗：包声明的环境没装会自动先装环境（进度同样进这个弹窗），失败切报错态 */
    private void startInstall(Uri uri, TextView installBtn) {
        installBtn.setEnabled(false);
        installBtn.setText("安装中…");
        String defaultEnv = getIntent().getStringExtra(EXTRA_DEFAULT_ENV);
        LabInstallDialog.Installing dlg = LabInstallDialog.showInstalling(this, "正在安装服务");
        final LabPackageInstaller.InstallHandle[] handle = new LabPackageInstaller.InstallHandle[1];
        dlg.setOnCancel(() -> {
            if (handle[0] != null) handle[0].cancel();
        });
        Runnable[] retry = new Runnable[1];
        retry[0] = () -> handle[0] = LabPackageInstaller.installFromUri(this, uri, defaultEnv,
                new LabPackageInstaller.Listener() {
                    @Override
                    public void onProgress(String text) {
                        String pct = LabInstallDialog.shardLine(text);
                        if (pct != null) dlg.progress(pct, text);
                        else dlg.busy(text);
                    }

                    @Override
                    public void onOk(LabRegistry.Svc pkg) {
                        runOnUiThread(() -> {
                            dlg.ok();
                            Notify.show("装好了");
                            finish();
                            LabEnvActivity.start(LabInstallActivity.this, LabRegistry.envOf(pkg));
                        });
                    }

                    @Override
                    public void onFail(String reason) {
                        runOnUiThread(() -> {
                            installBtn.setEnabled(true);
                            installBtn.setText("安装");
                            dlg.error(reason, retry[0]);
                        });
                    }

                    @Override
                    public void onCancelled() {
                        runOnUiThread(() -> {
                            dlg.cancelled();
                            installBtn.setEnabled(true);
                            installBtn.setText("安装");
                            Notify.show("已取消安装");
                        });
                    }
                });
        retry[0].run();
    }

    private File copyToTemp(Uri u) throws Exception {
        File tmp = new File(getCacheDir(), "lab-peek-" + System.currentTimeMillis() + ".lab");
        try (InputStream in = getContentResolver().openInputStream(u);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new Exception("打不开这个文件");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return tmp;
    }
}

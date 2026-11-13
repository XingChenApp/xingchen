package com.fongmi.android.tv.ui.lab;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.utils.Notify;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.fongmi.android.tv.R;

/**
 * 粘贴导入（头等路）：用户把服务清单 / 命令条目 / 脚本源码粘进框，
 * 壳子立刻识别格式、把服务全列出来，点一下就装。
 * 单行 http(s) 链接也会先拉下来再识别（链接路走同一框）。
 * 选文件时拿到文本类文件（.json/.php/.js/.py/.txt）也转到这里按内容识别。
 * 不设叉，走系统返回。
 */
public class LabPasteImportActivity extends AppCompatActivity {

    private static final String EXTRA_URI = "uri";
    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_DEFAULT_ENV = "default_env";
    private static final String EXTRA_URL = "url";
    /** 粘贴模式：多行文本框 */
    private static final String MODE_PASTE = "paste";
    /** 链接模式：单行 URL 输入框 */
    private static final String MODE_LINK = "link";

    private EditText input;
    private TextView status;
    private LinearLayout resultBox;
    private TextView importBtn;
    private TextView goBtn;
    private List<LabImportCompat.ImportItem> items;
    private String mode = MODE_PASTE;

    public static void start(Activity activity) {
        start(activity, null);
    }

    /** 环境页「＋」→粘贴：默认落当前环境（条目没声明运行时才用） */
    public static void start(Activity activity, String defaultEnv) {
        Intent i = new Intent(activity, LabPasteImportActivity.class);
        i.putExtra(EXTRA_MODE, MODE_PASTE);
        if (defaultEnv != null) i.putExtra(EXTRA_DEFAULT_ENV, defaultEnv);
        activity.startActivity(i);
    }

    /** 链接入口：单行输入框，拉下来再识别 */
    public static void startLink(Activity activity) {
        startLink(activity, null);
    }

    /** 环境页「＋」→链接：默认落当前环境（条目没声明运行时才用） */
    public static void startLink(Activity activity, String defaultEnv) {
        Intent i = new Intent(activity, LabPasteImportActivity.class);
        i.putExtra(EXTRA_MODE, MODE_LINK);
        if (defaultEnv != null) i.putExtra(EXTRA_DEFAULT_ENV, defaultEnv);
        activity.startActivity(i);
    }

    /** 添加服务页的链接卡跳过来：链接模式＋输入框预填 */
    public static void startWithUrl(Activity activity, String url) {
        Intent i = new Intent(activity, LabPasteImportActivity.class);
        i.putExtra(EXTRA_MODE, MODE_LINK);
        i.putExtra(EXTRA_URL, url);
        activity.startActivity(i);
    }

    private static void startMode(Activity activity, String mode) {
        Intent i = new Intent(activity, LabPasteImportActivity.class);
        i.putExtra(EXTRA_MODE, mode);
        activity.startActivity(i);
    }

    public static void startWithUri(Activity activity, Uri uri) {
        Intent i = new Intent(activity, LabPasteImportActivity.class);
        i.putExtra(EXTRA_URI, uri.toString());
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String m = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_LINK.equals(m)) mode = MODE_LINK;
        LabSkin.edgeToEdge(this);
        buildUi();
        String prefill = getIntent().getStringExtra(EXTRA_URL);
        if (!TextUtils.isEmpty(prefill) && input != null) input.setText(prefill);
        String uriStr = getIntent().getStringExtra(EXTRA_URI);
        if (!TextUtils.isEmpty(uriStr)) {
            loadFromUri(Uri.parse(uriStr));
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
        boolean linkMode = MODE_LINK.equals(mode);
        title.setText(linkMode ? "链接导入" : "粘贴导入");
        title.setTextSize(20);
        title.setTextColor(LabSkin.TEXT_MAIN);
        title.getPaint().setFakeBoldText(true);
        nav.addView(title);
        root.addView(nav);

        TextView hint = new TextView(this);
        hint.setText(linkMode ? "粘贴服务清单的链接，拉下来自动识别"
                : "把服务清单、命令条目或脚本粘进来，自动识别");
        hint.setTextSize(14);
        hint.setTextColor(LabSkin.TEXT_ON_DARK_DIM);
        hint.setBackground(LabSkin.glassDark(this));
        hint.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(dp(18), dp(2), dp(18), 0);
        hint.setLayoutParams(hlp);
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(30) + LabSkin.navBarHeight(this));

        input = new EditText(this);
        if (MODE_LINK.equals(mode)) {
            input.setSingleLine(true);
            input.setHint("https://…");
        } else {
            input.setMinLines(8);
            input.setHint("粘在这里…");
        }
        input.setTextSize(13);
        input.setTextColor(LabSkin.TEXT_ON_DARK);
        input.setHintTextColor(LabSkin.TEXT_ON_DARK_DIM);
        input.setBackground(LabSkin.glassDark(this));
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.setMargins(dp(18), dp(4), dp(18), 0);
        body.addView(input, ilp);

        goBtn = bigButton(MODE_LINK.equals(mode) ? "拉取并识别" : "识别", true);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.setMargins(dp(18), dp(12), dp(18), 0);
        goBtn.setLayoutParams(glp);
        goBtn.setOnClickListener(v -> recognize(input.getText().toString(), null));
        body.addView(goBtn);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(LabSkin.COUNT_YELLOW);
        status.setBackground(LabSkin.glassDark(this));
        status.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.setMargins(dp(18), dp(10), dp(18), 0);
        status.setLayoutParams(stlp);
        status.setVisibility(View.GONE);
        body.addView(status);

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(resultBox);

        importBtn = bigButton("导入", true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(18), dp(12), dp(18), 0);
        importBtn.setLayoutParams(blp);
        importBtn.setVisibility(View.GONE);
        importBtn.setOnClickListener(v -> doImport());
        body.addView(importBtn);

        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void loadFromUri(Uri uri) {
        status.setVisibility(View.VISIBLE);
        status.setText("正在读文件…");
        new Thread(() -> {
            try {
                String name = displayName(uri);
                // 先落盘再识别：7z 离线包（整目录 Node 项目也走这条）整包不进内存，
                // 边读边数，上限仍是 30MB；文本内容仍在下面按 2MB 拦，语义不变
                java.io.File spool = spoolToFile(getContentResolver().openInputStream(uri),
                        30 * 1024 * 1024, "文件太大");
                try {
                    if (!dispatchSevenZ(spool, name) && !dispatchZip(spool, name)) {
                        if (spool.length() > 2 * 1024 * 1024) throw new Exception("文件太大");
                        String text = new String(readAll(new java.io.FileInputStream(spool),
                                2 * 1024 * 1024), StandardCharsets.UTF_8);
                        final String fn = name;
                        runOnUiThread(() -> recognize(text, fn));
                    }
                } catch (Exception pe) {
                    final String msg = plainError(pe);
                    runOnUiThread(() -> {
                        status.setVisibility(View.VISIBLE);
                        status.setText(msg);
                    });
                } finally {
                    spool.delete();
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setVisibility(View.VISIBLE);
                    status.setText("读不到这个文件");
                });
            }
        }).start();
    }

    private String displayName(Uri uri) {
        try {
            ContentResolver cr = getContentResolver();
            try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 识别：单行链接先下载，否则直接按文本识别 */
    private void recognize(String text, String fileName) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            showStatus(MODE_LINK.equals(mode) ? "先粘个链接进来" : "先粘点东西进来");
            return;
        }
        showStatus("正在识别…");
        resultBox.removeAllViews();
        importBtn.setVisibility(View.GONE);
        items = null;
        new Thread(() -> {
            try {
                // 本地路径一行（目录 / index.js / index.js.md5，含 file:// 与
                // clan:// 写法）：走本地 Node bundle 目录路（G-7 余路＋G-8）；
                // 不是路径或路径不存在时它返回 false，继续原有各路
                if (fileName == null && dispatchLocalBundlePath(t)) return;
                String src = t;
                String fn = fileName;
                if (isSingleUrl(t)) {
                    showStatusOnUi("正在拉链接…");
                    // 链接内容先落盘：.lab.7z 离线包整包不进内存（上限仍 30MB）
                    java.io.File spool = downloadFile(t, 30 * 1024 * 1024);
                    try {
                        // 链接也可能是 .lab.7z 离线包：先看魔数/后缀
                        if (dispatchSevenZ(spool, t)) return;
                        // Node bundle 指针（.js.md5）：本体只是 MD5，跟随指针拉整包
                        if (dispatchNodeBundle(spool, t)) return;
                        // .lab/npm 的 zip 包（G-6）：此前不分发，二进制当文本读、
                        // 过 2MB 上限直接报废；转安装确认页，与文件路对齐
                        if (dispatchZip(spool, t)) return;
                        if (spool.length() > 2 * 1024 * 1024) throw new Exception("这个文件太大了，下不动");
                        src = new String(readAll(new java.io.FileInputStream(spool),
                                2 * 1024 * 1024), StandardCharsets.UTF_8);
                        fn = null; // 链接内容按内容识别
                    } finally {
                        spool.delete();
                    }
                }
                final LabImportCompat.ParseResult r = LabImportCompat.parseText(src, fn);
                runOnUiThread(() -> showResult(r));
            } catch (Exception e) {
                final String msg = plainError(e);
                runOnUiThread(() -> showStatus(msg));
            }
        }).start();
    }

    /**
     * 报错给人话但不吞真因：自家异常本来就是人话，原样给；解析库的原始报错
     * 只折成一行、截到 140 字，不再整段换成一句「认不出来」——出错位置、
     * parseScript 的后缀提示这类真原因必须留得住（此前一律换短句，真因全吞）。
     */
    private static String plainError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) return "认不出来，换一种试试";
        String one = msg.replaceAll("\\s+", " ").trim();
        if (one.length() > 140) one = one.substring(0, 140) + "…";
        return one;
    }

    private boolean isSingleUrl(String t) {
        return t.matches("(?i)https?://\\S+") && !t.contains("\n");
    }

    /** 下载落盘：文本路 2MB 上限在读文本时拦（语义不变），7z 包路放宽到 30MB、整包不进内存 */
    private java.io.File downloadFile(String urlStr, int maxBytes) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "XingChenLab/1.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("这个链接拉不下来（" + code + "）");
            return spoolToFile(conn.getInputStream(), maxBytes, "这个文件太大了，下不动");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 流边读边落缓存临时文件（边读边数上限），调用方负责删 */
    private java.io.File spoolToFile(InputStream in, int maxBytes, String tooBigMsg) throws Exception {
        if (in == null) throw new Exception("打不开");
        java.io.File f = java.io.File.createTempFile("lab-import-", ".bin", getCacheDir());
        boolean ok = false;
        try (InputStream i = in;
             java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = i.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new Exception(tooBigMsg);
                out.write(buf, 0, n);
            }
            ok = true;
            return f;
        } finally {
            if (!ok) f.delete();
        }
    }

    /** 落盘文件先嗅 7z（魔数/后缀）：是就走离线包解析并展示，返回 true；不是返回 false 交回文本路 */
    private boolean dispatchSevenZ(java.io.File spool, String sniffName) throws Exception {
        byte[] head = new byte[6];
        int hn = 0;
        try (InputStream in = new java.io.FileInputStream(spool)) {
            int r;
            while (hn < head.length && (r = in.read(head, hn, head.length - hn)) > 0) hn += r;
        }
        byte[] magic = hn == head.length ? head : java.util.Arrays.copyOf(head, Math.max(hn, 0));
        if (!LabImportCompat.isSevenZ(magic, sniffName)) return false;
        final LabImportCompat.ParseResult r;
        try (InputStream in = new java.io.FileInputStream(spool)) {
            r = LabImportCompat.parseSevenZ(in);
        }
        runOnUiThread(() -> showResult(r));
        return true;
    }

    /**
     * 本地路径分发（G-7 余路）：粘进来的是一行本地路径时命中——目录、
     * 目录里的 index.js、或 index.js.md5 指针（本地形态：读指针、验同目录
     * index.js 的 MD5）。整目录文件收进内存（总量上限 30MB，与归档路同口径）
     * 交 LabImportCompat 造 Node bundle 条目。路径形状对但不存在/不是 bundle
     * 时在本方法里把话说清并返回 true 收口；压根不是路径形状返回 false。
     */
    private boolean dispatchLocalBundlePath(String t) throws Exception {
        if (t.contains("\n")) return false;
        String p = LabImportCompat.clanToLocalPath(t);
        if (p == null && t.toLowerCase().startsWith("file://")) p = t.substring(7);
        if (p == null && t.startsWith("/")) p = t;
        if (p == null) return false;
        java.io.File f = new java.io.File(p);
        if (!f.exists()) {
            showResultError("这个本地路径不存在：" + p);
            return true;
        }
        java.io.File dir = f;
        if (f.isFile()) {
            String fn = f.getName().toLowerCase();
            if (fn.endsWith(".js.md5")) {
                // 本地指针：本体是 MD5，真身是同目录去掉 .md5 的文件，验过才收
                String pointerText = readTextFile(f);
                String pointer = LabImportCompat.isMd5Pointer(pointerText)
                        ? pointerText.trim().split("\\s+")[0] : "";
                java.io.File main = new java.io.File(f.getParentFile(),
                        f.getName().substring(0, f.getName().length() - 4));
                if (pointer.isEmpty() || !main.isFile()) {
                    showResultError("这个 .md5 指针指不到同目录的真身文件");
                    return true;
                }
                byte[] mainBytes = readFileBytes(main, 30 * 1024 * 1024);
                if (!LabImportCompat.md5Hex(mainBytes).equalsIgnoreCase(pointer)) {
                    showResultError("同目录真身文件的 MD5 与指针里写的对不上，不导了");
                    return true;
                }
                dir = f.getParentFile();
            } else if (fn.equals("index.js")) {
                dir = f.getParentFile();
            } else {
                showResultError("这个文件不是 Node 服务包入口（要目录、index.js 或 index.js.md5）");
                return true;
            }
        }
        if (dir == null || !new java.io.File(dir, "index.js").isFile()) {
            showResultError("这个目录里没有 index.js，不是 Node 服务包目录");
            return true;
        }
        java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        long[] total = {0};
        collectBundleDir(dir, "", files, total);
        if (files.isEmpty()) {
            showResultError("这个目录里没收到文件");
            return true;
        }
        LabImportCompat.ParseResult r = LabImportCompat.nodeBundleLocalResult(
                dir.getName(), files, "");
        runOnUiThread(() -> showResult(r));
        return true;
    }

    /** 递归收目录文件（相对名用 / 分隔；总量 30MB 上限；跳过隐藏目录） */
    private void collectBundleDir(java.io.File dir, String prefix,
                                  java.util.Map<String, byte[]> out, long[] total) throws Exception {
        java.io.File[] kids = dir.listFiles();
        if (kids == null) return;
        java.util.Arrays.sort(kids, (a, b) -> a.getName().compareTo(b.getName()));
        for (java.io.File k : kids) {
            if (k.isDirectory()) {
                if (k.getName().startsWith(".")) continue;
                collectBundleDir(k, prefix + k.getName() + "/", out, total);
            } else if (k.isFile()) {
                byte[] b = readFileBytes(k, 30 * 1024 * 1024);
                total[0] += b.length;
                if (total[0] > 30 * 1024 * 1024) throw new Exception("整包超过 30MB 了");
                out.put(prefix + k.getName(), b);
            }
        }
    }

    private static String readTextFile(java.io.File f) throws Exception {
        return new String(readFileBytes(f, 4096), "UTF-8");
    }

    private static byte[] readFileBytes(java.io.File f, int cap) throws Exception {
        try (InputStream in = new java.io.FileInputStream(f);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > cap) throw new Exception("文件太大");
            }
            return out.toByteArray();
        }
    }

    /** 错误收口（后台线程调用安全）：状态行显错误、不给导入钮 */
    private void showResultError(String msg) {
        runOnUiThread(() -> {
            showStatus(msg);
            resultBox.removeAllViews();
            importBtn.setVisibility(View.GONE);
        });
    }

    /**
     * zip 包分发（G-6）：嗅到 zip 魔数/后缀就把落盘文件转给安装确认页
     * （peekManifest 展示包说明、点安装才装，与选文件路同一条链）。
     * spool 是调用方的临时文件、回去就删，这里先拷一份到缓存再交出去；
     * 确认页自己会再拷临时副本，这份留缓存里等系统清。
     * 是 zip 返回 true（本页收口），不是返回 false 交回文本路。
     */
    private boolean dispatchZip(java.io.File spool, String sniffName) throws Exception {
        byte[] head = new byte[4];
        int hn = 0;
        try (InputStream in = new java.io.FileInputStream(spool)) {
            int r;
            while (hn < head.length && (r = in.read(head, hn, head.length - hn)) > 0) hn += r;
        }
        byte[] magic = hn == head.length ? head : java.util.Arrays.copyOf(head, Math.max(hn, 0));
        if (!LabImportCompat.isZip(magic, sniffName)) return false;
        java.io.File keep = new java.io.File(getCacheDir(), "lab-link-import.lab");
        try (InputStream in = new java.io.FileInputStream(spool);
             java.io.FileOutputStream out = new java.io.FileOutputStream(keep)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        final String env = getIntent().getStringExtra(EXTRA_DEFAULT_ENV);
        runOnUiThread(() -> {
            LabInstallActivity.startWithUri(this, Uri.fromFile(keep), env);
            finish();
        });
        return true;
    }

    /**
     * Node bundle 分发：链接是 .js.md5 指针时命中——指针本体只是 32 位 MD5，
     * 按同目录拉真身 .js（走 30MB 归档口径，不走 2MB 文本路）、验 MD5，
     * 再捎上同目录配置文件，造一条服务交回现有安装链，返回 true。
     * 不是指针形态返回 false 交回文本路；是指针但拉不下/验不过则抛人话错，不装哑巴。
     * 配置文件裸跑不读它：拉到就一并落盘备用，拉不到不拦整包（记一条提示）。
     */
    private boolean dispatchNodeBundle(java.io.File md5Spool, String url) throws Exception {
        if (!LabImportCompat.isNodeBundleUrl(url)) return false;
        String pointer = new String(readAll(new java.io.FileInputStream(md5Spool),
                1024), StandardCharsets.UTF_8);
        if (!LabImportCompat.isMd5Pointer(pointer)) return false;
        String expect = pointer.trim().split("\\s+")[0].toLowerCase();
        String base = LabImportCompat.nodeBundleBaseUrl(url);
        String mainName = LabImportCompat.nodeBundleMainName(url);
        showStatusOnUi("认出来了：Node 服务包，正在拉整包…");
        java.io.File jsSpool = downloadFile(base + mainName, 30 * 1024 * 1024);
        final byte[] mainBytes;
        try {
            mainBytes = readAll(new java.io.FileInputStream(jsSpool), 30 * 1024 * 1024);
        } finally {
            jsSpool.delete();
        }
        String actual = LabImportCompat.md5Hex(mainBytes);
        if (!expect.equals(actual)) {
            throw new Exception("这个服务包校验对不上：指针里写的 MD5 是 " + expect
                    + "，拉下来的文件算出来是 " + actual + "，先别装——可能是文件没传完，或者那边的包被换过了");
        }
        byte[] configBytes = null;
        boolean configMissed = false;
        try {
            java.io.File cfgSpool = downloadFile(base + LabImportCompat.nodeBundleConfigName(mainName),
                    2 * 1024 * 1024);
            try {
                configBytes = readAll(new java.io.FileInputStream(cfgSpool), 2 * 1024 * 1024);
            } finally {
                cfgSpool.delete();
            }
        } catch (Exception ignored) {
            configMissed = true;
        }
        final LabImportCompat.ParseResult r =
                LabImportCompat.nodeBundleResult(url, mainBytes, configBytes);
        if (configMissed) {
            LabImportCompat.ImportItem it = r.items.get(0);
            it.needsReview = LabImportCompat.addLine(it.needsReview,
                    "同目录的配置文件这次没拉到（包裸跑不读它，不影响先用；以后要用配置再补）");
        }
        runOnUiThread(() -> showResult(r));
        return true;
    }

    private void showResult(LabImportCompat.ParseResult r) {
        items = r.items;
        showStatus(r.note);
        for (LabImportCompat.ImportItem it : r.items) {
            resultBox.addView(itemCard(it));
        }
        importBtn.setText("导入这 " + r.items.size() + " 个服务");
        importBtn.setVisibility(View.VISIBLE);
    }

    private View itemCard(LabImportCompat.ImportItem it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(LabSkin.cardBackground(this, false));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(18), dp(10), dp(18), 0);
        card.setLayoutParams(lp);

        TextView name = new TextView(this);
        name.setText(it.name);
        name.setTextSize(17);
        name.setTextColor(LabSkin.TEXT_MAIN);
        name.getPaint().setFakeBoldText(true);
        card.addView(name);

        TextView meta = new TextView(this);
        String port = "auto".equals(it.portPolicy) ? "端口自动分" : "端口 " + it.portPolicy;
        meta.setText(LabRuntimeManager.label(it.runtime) + " · " + port);
        meta.setTextSize(13);
        meta.setTextColor(LabSkin.TEXT_SUB);
        meta.setPadding(0, dp(6), 0, 0);
        card.addView(meta);

        if (!TextUtils.isEmpty(it.needsReview)) {
            TextView warn = new TextView(this);
            warn.setText("要确认：\n" + it.needsReview);
            warn.setTextSize(13);
            warn.setTextColor(LabSkin.AMBER);
            warn.setPadding(0, dp(8), 0, 0);
            card.addView(warn);
        }
        return card;
    }

    private void doImport() {
        if (items == null || items.isEmpty()) return;
        importBtn.setEnabled(false);
        importBtn.setText("导入中…");
        String defaultEnv = getIntent().getStringExtra(EXTRA_DEFAULT_ENV);
        // 照稿弹安装中弹窗：条目声明的环境没装会自动先装（进度同样进这个弹窗）
        LabInstallDialog.Installing dlg = LabInstallDialog.showInstalling(this, "正在导入服务");
        final LabPackageInstaller.InstallHandle[] handle = new LabPackageInstaller.InstallHandle[1];
        dlg.setOnCancel(() -> {
            if (handle[0] != null) handle[0].cancel();
        });
        Runnable[] retry = new Runnable[1];
        retry[0] = () -> handle[0] = LabPackageInstaller.installCompat(this, items, defaultEnv,
                new LabPackageInstaller.CompatListener() {
                    @Override
                    public void onProgress(String text) {
                        String pct = LabInstallDialog.shardLine(text);
                        if (pct != null) dlg.progress(pct, text);
                        else dlg.busy(text);
                    }

                    @Override
                    public void onDone(int count) {
                        runOnUiThread(() -> {
                            dlg.ok();
                            Notify.show("装好了 " + count + " 个");
                            finish();
                        });
                    }

                    @Override
                    public void onFail(String reason) {
                        runOnUiThread(() -> {
                            importBtn.setEnabled(true);
                            importBtn.setText("导入这 " + items.size() + " 个服务");
                            dlg.error(reason, retry[0]);
                        });
                    }

                    @Override
                    public void onCancelled() {
                        runOnUiThread(() -> {
                            dlg.cancelled();
                            importBtn.setEnabled(true);
                            importBtn.setText("导入这 " + items.size() + " 个服务");
                            Notify.show("已取消安装");
                        });
                    }
                });
        retry[0].run();
    }

    private void showStatus(String s) {
        status.setVisibility(View.VISIBLE);
        status.setText(s);
    }

    private void showStatusOnUi(String s) {
        runOnUiThread(() -> showStatus(s));
    }

    private TextView bigButton(String text, boolean yellow) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(18);
        b.getPaint().setFakeBoldText(true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(yellow ? 0xFF171310 : LabSkin.TEXT_MAIN);
        b.setBackground(yellow ? LabSkin.buttonYellow(this) : LabSkin.buttonGray(this));
        b.setPadding(0, dp(16), 0, dp(16));
        return b;
    }

    private byte[] readAll(InputStream in, int maxBytes) throws Exception {
        if (in == null) throw new Exception("打不开");
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = i.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new Exception("文件太大");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}

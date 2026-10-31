package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.music.MusicSourceManager;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.legado.LegadoStore;
import com.fongmi.android.tv.localbook.LocalBook;
import com.fongmi.android.tv.localbook.LocalBookImporter;
import com.fongmi.android.tv.localbook.LocalBookSession;
import com.fongmi.android.tv.read.ReadingRouter;
import com.fongmi.android.tv.read.ReadingSession;
import com.fongmi.android.tv.utils.PyExtConfig;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.ToastUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * .py/.js「打开方式」总门（清单三份申报的落点）：
 * 外部点开脚本文件 → 静默登记进插件管理 → 直接打开这个脚本的内容。
 * 成功全程零打扰（不弹页、不出提示），用户感知就是「点开文件→脚本开了」；
 * 只有真失败（读不到／定不了型／没存进去）才 Toast 一句原因。
 *
 * 登记口径与插件管理手工导入完全一致（同一套存储，不另起炉灶）：
 * 文件落 files/plugins/py|js，目录即注册表（PluginActivity 扫目录成列，
 * PyLoader/JsLoader 监听同目录），失效照 PluginActivity.invalidateFiles
 * （py 按文件单清、js 全清）。
 * 打开走现行选源路，不另写跳转：照 VodFragment.onScriptSelected 建动态源
 * （key py_/js_+基名、api 文件绝对路径、type 3、ext 走 PyExtConfig），
 * registerDynamicSite（CollectFragment 先例）＋ setHome ＋ RefreshEvent.home，
 * 再把 HomeActivity 拉到前台，分类/列表随首页自家逻辑出来。
 * 音乐插件形态的 .js（MusicSourceManager.detectFormat 认得出）没有点播
 * 「源首页」概念：文件照样登记进插件管理，打开落音乐插件管理页——但它
 * 不会出现在音乐插件列表里（音乐侧是另一套登记：MusicSourceManager 按
 * srcUrl/本地副本入库，本门不往那套里塞，冲突与理由记在导入账里）。
 * <p>
 * txt 一类（扩展名 .txt，或无/杂扩展名且脚本嗅探不中）走阅读分发
 * （TxtFileJudge 三分）：书源 JSON → LegadoStore.importText 现行链登记
 * （整包多源、同 bookSourceUrl 覆盖），导完打开书源列表（LegadoActivity）；
 * 小说正文 → LocalBookImporter.importUri 现行链落本地书存储（流式拷贝、
 * 上限与索引口径全随现行导入），直接打开阅读器（LocalBookSession＋
 * ReadingRouter，与 LocalBookOpenActivity 同路）。取舍钉死：扩展名是 .txt
 * 就只走阅读分发，不许 py/js 内容嗅探翻案抢走（py 被误命名 .txt 极少，
 * 真发生了它会按正文落成本地书，详见 txt 导入账）。
 */
public class ImportActivity extends AppCompatActivity {

    /** 导入体积上限：脚本源常见几十~几百 KB，10MB 足够兜住胖源、又挡住误点的大文件 */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /** txt 分发探头样本上限：判定只看头部，512KB 足够书源特征与章节行露头 */
    private static final int HEAD_BYTES = 512 * 1024;

    /** 书源文件上限：与书源现行文件导入同口径（LegadoActivity 选择器、LegadoStore.importFile 均 2MB） */
    private static final long SOURCE_MAX_BYTES = 2L * 1024 * 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = resolveUri(getIntent());
        if (uri == null) {
            finish();
            return;
        }
        Task.execute(() -> doImport(uri));
    }

    /** VIEW 取 data；SEND 取 EXTRA_STREAM；其余一律不接 */
    private Uri resolveUri(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) return intent.getData();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Parcelable p = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (p instanceof Uri) return (Uri) p;
        }
        return null;
    }

    private void doImport(Uri uri) {
        String displayName = queryDisplayName(uri);
        String ext = PluginFileJudge.extensionOf(PluginFileJudge.sanitize(displayName));
        if ("txt".equals(ext)) {
            // .txt 钉死走阅读分发，不许脚本嗅探翻案（取舍见类头注）
            doImportTxt(uri, displayName, null);
            return;
        }
        if (ext == null || (!"py".equals(ext) && !"js".equals(ext) && !"wv".equals(ext))) {
            // 无/杂扩展名：先探头嗅脚本（标志专一、优先），嗅不出再按 txt 三分
            HeadSample head = readHead(uri);
            if (head == null) {
                fail("读不到这个文件");
                return;
            }
            if (head.bytes.length == 0) {
                fail("文件是空的");
                return;
            }
            if (PluginFileJudge.sniff(head.bytes) != PluginFileJudge.Kind.UNKNOWN) {
                doImportScript(uri, displayName);
                return;
            }
            doImportTxt(uri, displayName, head);
            return;
        }
        doImportScript(uri, displayName);
    }

    private void doImportScript(Uri uri, String displayName) {
        byte[] bytes;
        try {
            bytes = readAll(uri, MAX_BYTES);
        } catch (TooLargeException e) {
            fail("文件太大了，超过 10MB 没法导入");
            return;
        } catch (Exception e) {
            fail("读不到这个文件");
            return;
        }
        if (bytes == null || bytes.length == 0) {
            fail("文件是空的");
            return;
        }
        PluginFileJudge.Verdict verdict = PluginFileJudge.judge(displayName, bytes);
        if (verdict.kind == PluginFileJudge.Kind.UNKNOWN) {
            fail("认不出这是哪种脚本");
            return;
        }
        boolean py = verdict.kind == PluginFileJudge.Kind.PY;
        String targetName = verdict.targetName != null ? verdict.targetName
                : fallbackScriptName(bytes, py);
        File dir = new File(getFilesDir(), py ? "plugins/py" : "plugins/js");
        File dest = new File(dir, targetName);
        try {
            landFile(dir, dest, bytes);
        } catch (Exception e) {
            fail("没存进去，导入失败");
            return;
        }
        // 登记即落盘（目录即注册表）；同名覆盖后照插件管理口径失效旧蜘蛛
        try {
            if (py) BaseLoader.get().invalidatePyScript(dest.getAbsolutePath());
            else BaseLoader.get().clearJs();
        } catch (Throwable ignored) {
        }
        // 同一脚本的缀号变体清掉（此前按「名(1)」落过盘的存量双份）：只留正名
        // 这一份，列表/条目不许再双份（fix168-pyimport2）；py 变体一并失效
        try {
            for (String variant : PluginFileJudge.purgeAvoidVariants(dir, dest.getName())) {
                if (py) BaseLoader.get().invalidatePyScript(variant);
            }
        } catch (Throwable ignored) {
        }
        RefreshEvent.home();
        runOnUiThread(() -> openImported(dest, py, bytes));
    }

    /**
     * 无名导入的落盘名：按内容定名（import_＋SHA-256 前 10 位 hex）——同一脚本
     * 经无名通道反复导入必落同一文件、走 landFile 覆盖原地生效，列表仍是一条；
     * 不再按时间戳每次生新文件堆重复条目（他 2026-10-31 亲令「同名直接覆盖」）。
     * 定名失败兜底退回时间戳命名，保导入不死。
     */
    private static String fallbackScriptName(byte[] bytes, boolean py) {
        String suffix = py ? ".py" : ".js";
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder("import_");
            for (int i = 0; i < 5; i++) sb.append(String.format("%02x", digest[i] & 0xff));
            return sb.append(suffix).toString();
        } catch (Exception e) {
            return "import_" + System.currentTimeMillis() + suffix;
        }
    }

    /** 先写临时文件再改名（同名覆盖原子换，不留半截文件）；改名不成退回直写 */
    private void landFile(File dir, File dest, byte[] bytes) throws Exception {
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdirs");
        File tmp = new File(dir, dest.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        }
        if (!tmp.renameTo(dest)) {
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(bytes);
            }
            tmp.delete();
        }
    }

    // ---------- txt 分发（书源／正文／拒收） ----------

    /** 头样本：bytes 为文件头部（≤HEAD_BYTES）；wholeFile 表示样本即全文（流已到尾） */
    private static class HeadSample {
        final byte[] bytes;
        final boolean wholeFile;

        HeadSample(byte[] bytes, boolean wholeFile) {
            this.bytes = bytes;
            this.wholeFile = wholeFile;
        }
    }

    private void doImportTxt(Uri uri, String displayName, HeadSample preRead) {
        HeadSample head = preRead != null ? preRead : readHead(uri);
        if (head == null) {
            fail("读不到这个文件");
            return;
        }
        if (head.bytes.length == 0) {
            fail("文件是空的");
            return;
        }
        TxtFileJudge.Kind kind = TxtFileJudge.judge(head.bytes, head.wholeFile, querySize(uri));
        switch (kind) {
            case BOOK_SOURCE:
                importBookSource(uri);
                break;
            case NOVEL:
                importNovel(uri, displayName);
                break;
            default:
                fail("认不出这个文件是书源还是小说");
                break;
        }
    }

    /** 书源：现行导入链 LegadoStore.importText 登记（整包多源），导完打开书源列表；成功静默 */
    private void importBookSource(Uri uri) {
        byte[] bytes;
        try {
            bytes = readAll(uri, SOURCE_MAX_BYTES);
        } catch (TooLargeException e) {
            fail("书源文件太大了，超过 2MB 没法导入");
            return;
        } catch (Exception e) {
            fail("读不到这个文件");
            return;
        }
        try {
            // 最终裁定在导入器：逐项验 bookSourceUrl、坏项跳过、同 url 覆盖
            LegadoStore.importText(new String(bytes, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            fail("书源没导进来，格式可能不对");
            return;
        }
        runOnUiThread(() -> {
            LegadoActivity.start(this);
            finish();
        });
    }

    /**
     * 正文：现行本地书链 LocalBookImporter.importUri 落盘＋建索引，直接开阅读器。
     * 文件名不以 .txt 结尾（探头嗅出的无扩展名正文）时先流式暂存成同名 .txt 再走同一条链，
     * 因为现行导入按文件名认格式；暂存文件在导入拷完后即删（导入是拷入私有目录，非引用原文件）。
     */
    private void importNovel(Uri uri, String displayName) {
        String name = PluginFileJudge.sanitize(displayName);
        boolean txtNamed = name != null && name.toLowerCase(Locale.ROOT).endsWith(".txt");
        File tmp = null;
        try {
            Uri target = uri;
            if (!txtNamed) {
                String base = "导入的小说";
                if (name != null) {
                    int dot = name.lastIndexOf('.');
                    base = dot > 0 ? name.substring(0, dot) : name;
                    if (base.isEmpty()) base = "导入的小说";
                }
                tmp = new File(getCacheDir(), base + ".txt");
                copyToFile(uri, tmp);
                target = Uri.fromFile(tmp);
            }
            LocalBookImporter.Result r = LocalBookImporter.importUri(getApplicationContext(), target);
            LocalBook book = r.book;
            String warn = r.warn;
            if (tmp != null) tmp.delete();
            runOnUiThread(() -> openNovel(book, warn));
        } catch (LocalBookImporter.ImportException e) {
            if (tmp != null) tmp.delete();
            fail(e.getMessage());
        } catch (Exception e) {
            if (tmp != null) tmp.delete();
            fail("这本书没导进来");
        }
    }

    /** 打开正文：与 LocalBookOpenActivity 同路（建会话→阅读器），导入侧的截断提示照现行口径出 */
    private void openNovel(LocalBook book, String warn) {
        if (book == null) {
            ToastUtil.show(this, "打不开这本书", Toast.LENGTH_LONG);
            finish();
            return;
        }
        if (warn != null && !warn.isEmpty()) {
            ToastUtil.show(this, warn, Toast.LENGTH_LONG);
        }
        ReadingSession s = LocalBookSession.build(book, 0);
        if (s != null) {
            if (s.kind == ReadingSession.Kind.COMIC) {
                ReadingRouter.openReadingWithStartPage(this, s, 0);
            } else {
                ReadingRouter.openReading(this, s);
            }
        } else {
            ToastUtil.show(this, "打不开这本书", Toast.LENGTH_LONG);
        }
        finish();
    }

    /** 只读头部样本（≤HEAD_BYTES），不整读大文件；打开流失败返回 null */
    private HeadSample readHead(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int len;
            while (buf.size() < HEAD_BYTES && (len = in.read(chunk, 0,
                    Math.min(chunk.length, HEAD_BYTES - buf.size()))) > 0) {
                buf.write(chunk, 0, len);
            }
            // 没填满样本上限就是流已到尾（样本即全文）；填满则不下整本结论
            return new HeadSample(buf.toByteArray(), buf.size() < HEAD_BYTES);
        } catch (Exception e) {
            return null;
        }
    }

    /** 流式暂存到文件（正文暂存用）；上限随本地书现行导入（2GB），超限抛 TooLargeException */
    private void copyToFile(Uri uri, File dest) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IllegalStateException("openInputStream null");
            byte[] chunk = new byte[64 * 1024];
            int len;
            long total = 0;
            while ((len = in.read(chunk)) > 0) {
                total += len;
                if (total > 2L * 1024 * 1024 * 1024) throw new TooLargeException();
                out.write(chunk, 0, len);
            }
            if (total == 0) throw new IllegalStateException("empty");
        }
    }

    /** 文件总字节数：先问 SIZE 列，取不到 -1（判定侧以样本长度兜底） */
    private long querySize(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private void openImported(File dest, boolean py, byte[] bytes) {
        if (!py) {
            // 音乐插件类没有点播源首页：打开落它现行的管理页（登记仍在插件管理，见类头注）
            String format = null;
            try {
                format = MusicSourceManager.detectFormat(new String(bytes, StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
            if (format != null) {
                MusicPluginsActivity.startSites(this);
                finish();
                return;
            }
        }
        String fileName = dest.getName();
        String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String prefix = py ? "py_" : "js_";
        try {
            Site site = new Site();
            site.setKey(prefix + base);
            site.setName(base);
            site.setApi(dest.getAbsolutePath());
            site.setType(3);
            String savedExt = PyExtConfig.load(this, prefix + base);
            site.setExt(savedExt == null || savedExt.isEmpty() ? "{}" : savedExt);
            site.setJar("");
            VodConfig.get().registerDynamicSite(site);
            VodConfig.get().setHome(site);
            // 确定性打开凭据：setHome 真落到当前 home 才登记待开标记——首页
            // Fragment 在 initView/onResume/切回可见任一时点消费它补开，不再只靠
            // setHome 发的那发会丢的 RefreshEvent（fix168-pyimport2）
            if (site.getKey().equals(VodConfig.get().getHome().getKey())) {
                VodConfig.get().markImportedHome(site.getKey());
            }
        } catch (Throwable ignored) {
        }
        RefreshEvent.home();
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    /** 失败才出声：一句原因＋收尾。成功路径永不走这里 */
    private void fail(String reason) {
        runOnUiThread(() -> {
            ToastUtil.show(this, reason, Toast.LENGTH_LONG);
            finish();
        });
    }

    private static class TooLargeException extends Exception {
    }

    private byte[] readAll(Uri uri, long cap) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("openInputStream null");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int len;
            while ((len = in.read(chunk)) > 0) {
                if (buf.size() + len > cap) throw new TooLargeException();
                buf.write(chunk, 0, len);
            }
            return buf.toByteArray();
        }
    }

    /** 文件名：先问 DISPLAY_NAME，没有再退 lastPathSegment（照 PluginActivity.getFileName 口径） */
    private String queryDisplayName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        return result;
    }
}

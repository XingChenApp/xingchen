package com.example.hikerview.ui.rules.service.require;

import android.os.Environment;
import android.util.Log;

import com.example.hikerview.ui.rules.model.RequireDescription;
import com.example.hikerview.ui.rules.model.RequireItem;
import com.example.hikerview.utils.FileUtil;
import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 8.83 {@code com.example.hikerview.ui.rules.service.require.RequireUtils} 的
 * 同包名同签名补齐（RequireUtils.smali 全文逐法核，类账见
 * worknotes/hiker-codelib-ledger-20261111.md）。
 *
 * <p>原版里这个类是 App 包内真实类：官方预置库 Hikerurl.js 的模块系统在
 * http 模块落盘后直调 {@code generateRequireMap} 登记（Hikerurl.js:57/147），
 * 规则与代码库亦可经 Rhino Java 互操作按全包名直调其余各法。星辰此前没有
 * 这个类——JS 面只有 init 预置段嫁接的 no-op 桩（仅 generateRequireMap 一名），
 * 宿主侧移植（HkJsRuntime 私有静态）的登记守卫又抄反（官「标题与键皆非空
 * 才投递」，旧形反作「标题为空才投递」，标题恒非空 ⇒ 登记永不触发），
 * 整层登记/读回在咱家是死的。本类按原包名补回真实类，HkJsRuntime 移植改
 * 委托本类，两路共用同一份 require.json。</p>
 *
 * <p>置换记名：官方 JSON 走 fastjson、星辰走 Gson（字段名一致、格式互通）；
 * 官方异步投递走 HeavyTaskUtil、星辰无对应物，沿 HkJsRuntime 移植先例用
 * 普通线程；存储根照官 JSEngine.getFilesDir()＝getRootDir()＋"/rules/files/"，
 * getRootDir 与 HkJsRuntime.hkRootDir 同式（getExternalFilesDir(DOCUMENTS)）。</p>
 */
public class RequireUtils {

    private static final String TAG = "RequireUtils";
    private static final Gson GSON = new Gson();

    public RequireUtils() {
    }

    /** 8.83 UriUtils.getRootDir(ctx)：getExternalFilesDir(DOCUMENTS)，mkdirs。 */
    private static String getRootDir() {
        File docs = App.get().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docs != null && !docs.exists()) docs.mkdirs();
        return docs != null ? docs.getAbsolutePath() : "";
    }

    /** 8.83 JSEngine.getFilesDir()（smali:3563）：rootDir＋"/rules/files/"。 */
    private static String getFilesDir() {
        return getRootDir() + File.separator + "rules" + File.separator + "files" + File.separator;
    }

    /** 8.83 StringUtil.isEmpty。 */
    private static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    /** 8.83 StringUtil.md5：标准 MD5 十六进制小写。 */
    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // deleteCache [RequireUtils.smali 16-237]
    // 非 http 抛 Exception「require地址必须为http地址」；删
    // <root>/libs/<md5>.js（先 copy 到 .bak 再删）＋ <md5>.json。
    // ------------------------------------------------------------------
    public static void deleteCache(String url) throws Exception {
        if (!isEmpty(url) && url.startsWith("http")) {
            String md5 = md5(url);
            String jsName = md5 + ".js";
            String jsonName = md5 + ".json";
            String libsDir = getRootDir() + File.separator + "libs";
            File jsFile = new File(libsDir + File.separator + jsName);
            File jsonFile = new File(libsDir + File.separator + jsonName);
            if (jsFile.exists()) {
                File bak = new File(jsFile.getAbsolutePath() + ".bak");
                FileUtil.copy(jsFile, bak);
                jsFile.delete();
            }
            if (jsonFile.exists()) {
                jsonFile.delete();
            }
        } else {
            throw new Exception("require地址必须为http地址");
        }
    }

    // ------------------------------------------------------------------
    // deleteCacheByRule [RequireUtils.smali 238-285]
    // 登记非空时逐条 deleteRequireItem(item, false)，最后 deleteRequireMap。
    // ------------------------------------------------------------------
    public static void deleteCacheByRule(String title) {
        List<RequireItem> items = getRequireItems(title);
        if (items != null && !items.isEmpty()) {
            for (RequireItem item : items) {
                deleteRequireItem(item, false);
            }
            deleteRequireMap(title);
        }
    }

    // ------------------------------------------------------------------
    // deleteRequireItem [RequireUtils.smali 286-375]
    // 删 item.file（withBak 时先备 .bak）与同名 .json（.js→.json）；
    // 只删缓存文件、不动 require.json 条目。
    // ------------------------------------------------------------------
    public static void deleteRequireItem(RequireItem item, boolean withBak) {
        if (item == null) return;
        String file = item.getFile();
        String jsonPath = file.replace(".js", ".json");
        File jsFile = new File(file);
        File jsonFile = new File(jsonPath);
        if (jsFile.exists()) {
            if (withBak) {
                try {
                    FileUtil.copy(jsFile, new File(jsFile.getAbsolutePath() + ".bak"));
                } catch (Exception e) {
                    Log.e(TAG, "deleteRequireItem backup failed", e);
                }
            }
            jsFile.delete();
        }
        if (jsonFile.exists()) {
            jsonFile.delete();
        }
    }

    // ------------------------------------------------------------------
    // deleteRequireMap [RequireUtils.smali 376-430]
    // 删 <getFilesDir()>/<title>/require.json。
    // ------------------------------------------------------------------
    public static void deleteRequireMap(String title) {
        File f = new File(getFilesDir() + title + File.separator + "require.json");
        if (f.exists()) {
            f.delete();
        }
    }

    // ------------------------------------------------------------------
    // generateRequireMap [RequireUtils.smali 431-461] public，
    // generateRequireMapSync [649-745] 与 List 版 [462-648] private。
    // 守卫：p0 非空且 p1 非空才异步投递（分支逐条验：p0 空→return、
    // p1 空→return）。Sync 读 require.json 后走 List 版：按 url==key
    // 命中→刷 accessTime＋setProxy（proxy 与 key 相等则置 null）；
    // 未命中→新建 RequireItem(key, proxy, file, now)；列表 >100 时
    // 滤掉 accessTime < now-0x240c8400（7 天）；写回 require.json。
    // ------------------------------------------------------------------
    public static void generateRequireMap(final String title, final String key,
                                          final String proxy, final String file) {
        if (!isEmpty(title) && !isEmpty(key)) {
            // 8.83 投 HeavyTaskUtil 队列；星辰沿移植先例用普通线程。
            new Thread(() -> generateRequireMapSync(title, key, proxy, file)).start();
        }
    }

    private static void generateRequireMapSync(String title, String key, String proxy, String file) {
        String path = getFilesDir() + title + File.separator + "require.json";
        List<RequireItem> items;
        if (new File(path).exists()) {
            try {
                String json = FileUtil.fileToString(path);
                items = GSON.fromJson(json,
                        new TypeToken<List<RequireItem>>() {}.getType());
                if (items == null) items = new ArrayList<>();
            } catch (Exception e) {
                Log.e(TAG, "generateRequireMapSync read failed", e);
                items = new ArrayList<>();
            }
        } else {
            items = new ArrayList<>();
        }
        generateRequireMap(items, title, key, proxy, file);
    }

    private static void generateRequireMap(List<RequireItem> items, String title,
                                           String key, String proxy, String file) {
        long now = System.currentTimeMillis();
        boolean found = false;
        for (RequireItem item : items) {
            // 官 List 版逐字形：key.equals(item.getUrl())（键在前，条目 url 为
            // null 时跳过不炸）；proxy 比较用 StringUtils.equals（双边 null 安全）。
            if (key.equals(item.getUrl())) {
                item.setAccessTime(now);
                item.setProxy(java.util.Objects.equals(key, proxy) ? null : proxy);
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(new RequireItem(key, java.util.Objects.equals(key, proxy) ? null : proxy, file, now));
        }
        if (items.size() > 100) {
            long threshold = now - 604800000L; // 8.83 字面 0x240c8400 = 7 天毫秒
            List<RequireItem> kept = new ArrayList<>();
            for (RequireItem item : items) {
                if (item.getAccessTime() > threshold) kept.add(item);
            }
            items = kept;
        }
        try {
            String path = getFilesDir() + title + File.separator + "require.json";
            FileUtil.stringToFile(GSON.toJson(items), path);
        } catch (Exception e) {
            Log.e(TAG, "generateRequireMap persist failed", e);
        }
    }

    // ------------------------------------------------------------------
    // getRequireItems [RequireUtils.smali 747-865]
    // title 空→空表；读 <getFilesDir()>/<title>/require.json，
    // 缺文件/解析异常→空表。
    // ------------------------------------------------------------------
    public static List<RequireItem> getRequireItems(String title) {
        if (isEmpty(title)) {
            return new ArrayList<>();
        }
        String path = getFilesDir() + title + File.separator + "require.json";
        if (new File(path).exists()) {
            try {
                String json = FileUtil.fileToString(path);
                List<RequireItem> items = GSON.fromJson(json,
                        new TypeToken<List<RequireItem>>() {}.getType());
                if (items == null) items = new ArrayList<>();
                return items;
            } catch (Exception e) {
                Log.e(TAG, "getRequireItems failed", e);
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // getRequireVersion [RequireUtils.smali 866-913]
    // 读描述文件→RequireDescription.version；无则 Math.min(version, 0)。
    // ------------------------------------------------------------------
    public static int getRequireVersion(String path, int version) {
        if (new File(path).exists()) {
            String content = FileUtil.fileToString(path);
            RequireDescription desc = GSON.fromJson(content, RequireDescription.class);
            if (desc != null) return desc.getVersion();
        }
        return Math.min(version, 0);
    }

    // ------------------------------------------------------------------
    // updateDescription [RequireUtils.smali 946-974]
    // RequireDescription(url, version) 串化写 path；IOException 记日志吞掉。
    // ------------------------------------------------------------------
    public static void updateDescription(String path, String url, int version) {
        try {
            RequireDescription desc = new RequireDescription(url, version);
            FileUtil.stringToFile(GSON.toJson(desc), path);
        } catch (Exception e) {
            Log.e(TAG, "updateDescription failed", e);
        }
    }
}

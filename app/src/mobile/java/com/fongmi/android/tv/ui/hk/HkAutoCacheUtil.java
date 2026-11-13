package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.utils.Task;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 照抄 8.83 AutoCacheUtilKt（smali_classes2/com/example/hikerview/ui/home/AutoCacheUtilKt.smali）
 * 与 PageCache（同目录 PageCache.smali：字段 data/code，默认 data=空表、code=null）。
 *
 * 机制：规则给页面链接加 #autoCache#（或 #cacheOnly#）后，宿主以
 * md5(页面标题 + "￥￥￥" + 原始链接) 为键，把整页列表结果存到
 * files/cache/pages/&lt;md5&gt;.json；下次开页先灌缓存秒开，#cacheOnly# 命中缓存时不再走网络。
 * 与原版的存储格式差异仅在序列化库（原版 fastjson、星辰 Gson）与条目类（ArticleList→HkItem），
 * 文件为应用私有缓存、只由本应用读写，行为面与原版逐项对应。
 */
public class HkAutoCacheUtil {

    /**
     * 对应原版 PageCache。字段名由 @SerializedName 钉死：本类不在 proguard
     * 保名名单内，R8 改名后 Gson 写盘键会漂移成混淆名（1.4.60 成品实证
     * data→a、code→b），读回与写盘对不上账的一切按此钉死。
     */
    public static class PageCache {
        @com.google.gson.annotations.SerializedName("data")
        private List<HkItem> data;
        @com.google.gson.annotations.SerializedName("code")
        private String code;

        public PageCache() {
            this.data = new ArrayList<>();
            this.code = null;
        }

        public PageCache(List<HkItem> data, String code) {
            this.data = data;
            this.code = code;
        }

        public List<HkItem> getData() {
            return data == null ? new ArrayList<>() : data;
        }

        public void setData(List<HkItem> data) {
            this.data = data;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    private static final Gson GSON = new Gson();
    private static final Type ITEM_LIST_TYPE = TypeToken.getParameterized(List.class, HkItem.class).getType();
    /** clearCache 的过期线：原版常量 0x4d3f6400 毫秒 = 15 天。 */
    private static final long EXPIRE_MS = 1_296_000_000L;
    /** clearCache 的文件数上限：原版常量 0xc8 = 200，超出只留修改时间最新的 200 个。 */
    private static final int MAX_FILES = 200;

    /** 对应原版 initAutoCacheMd5：链接含 #autoCache# 或 #cacheOnly# 才生成键，否则 null。 */
    public static String initAutoCacheMd5(String title, String url) {
        if (url != null && (url.contains("#autoCache#") || url.contains("#cacheOnly#"))) {
            return md5((title == null ? "" : title) + "￥￥￥" + url);
        }
        return null;
    }

    /** 对应原版 isCacheOnly。 */
    public static boolean isCacheOnly(String url) {
        return url != null && url.contains("#cacheOnly#");
    }

    /** 对应原版 getCacheDir：UriUtils.getRootDir（星辰等价 filesDir）+ /cache/pages。 */
    private static File getCacheDir(Context context) {
        return new File(context.getFilesDir(), "cache" + File.separator + "pages");
    }

    private static File cacheFile(Context context, String md5) {
        return new File(getCacheDir(context), md5 + ".json");
    }

    /** 对应原版 hasCache：md5 非空且缓存文件存在。 */
    public static boolean hasCache(Context context, String md5) {
        return context != null && !TextUtils.isEmpty(md5) && cacheFile(context, md5).exists();
    }

    /**
     * 对应原版 getCache：md5 为空或读/解析失败一律回空 PageCache；
     * 文件内容是 JSON 数组（旧格式）时只取 data、code 留空；是对象时按树读。
     *
     * 对象形态不用反射整包读 PageCache（2026-10-29 1.4.60 真机崩定根，
     * 账 hk-list-cast-crash-20261029）：PageCache 与 HkItem 都不在保名名单，
     * 成品里字段被改名且 data 的 Signature 被剥——反射读出 LinkedTreeMap
     * 元素，而旧 retypeData 按源码键 "data" 查树，在成品文件里键已是混淆名
     * "a"、查空即静默跳过，毒元素直达 bindArrayToView 强转崩。现行读法：
     * 先解树，data 只许经 ITEM_LIST_TYPE 定型读入，定型失败整包回空
     * （按缓存未命中处理），反射结果永不外放。旧混淆键文件（键为 a/b）
     * 查不到 "data" 即按未命中回空，下次 saveCache 以钉名格式重写。
     */
    public static PageCache getCache(Context context, String md5) {
        PageCache empty = new PageCache();
        if (context == null || TextUtils.isEmpty(md5)) return empty;
        try {
            File file = cacheFile(context, md5);
            if (!file.exists()) return empty;
            String text = readFile(file);
            if (TextUtils.isEmpty(text)) return empty;
            if (text.startsWith("[") && text.endsWith("]")) {
                List<HkItem> list = GSON.fromJson(text, ITEM_LIST_TYPE);
                PageCache cache = new PageCache();
                if (list != null) cache.setData(list);
                return cache;
            }
            com.google.gson.JsonElement tree = GSON.fromJson(text, com.google.gson.JsonElement.class);
            if (tree == null || !tree.isJsonObject()) return empty;
            com.google.gson.JsonObject obj = tree.getAsJsonObject();
            PageCache cache = new PageCache();
            com.google.gson.JsonElement code = obj.get("code");
            if (code != null && code.isJsonPrimitive()) cache.setCode(code.getAsString());
            com.google.gson.JsonElement data = obj.get("data");
            if (data == null || data.isJsonNull()) return cache;
            if (!data.isJsonArray()) return empty;
            List<HkItem> typed = GSON.fromJson(data, ITEM_LIST_TYPE);
            if (typed == null) return empty;
            cache.setData(typed);
            return cache;
        } catch (Exception e) {
            return empty;
        }
    }

    /** 对应原版 saveCache(ctx, md5, list)：code 传 null 的便捷重载（原版 $default 同义）。 */
    public static void saveCache(Context context, String md5, List<HkItem> list) {
        saveCache(context, md5, list, null);
    }

    /**
     * 对应原版 saveCache(ctx, md5, list, code)：异步落盘（原版走 HeavyTaskUtil）；
     * 列表为空时删掉缓存文件；否则写 PageCache{data, code} 的 JSON。
     */
    public static void saveCache(Context context, String md5, List<HkItem> list, String code) {
        if (context == null || TextUtils.isEmpty(md5)) return;
        final Context appContext = context.getApplicationContext();
        final List<HkItem> copy = list == null ? new ArrayList<>() : new ArrayList<>(list);
        Task.execute(() -> {
            try {
                File file = cacheFile(appContext, md5);
                if (copy.isEmpty()) {
                    if (file.exists()) file.delete();
                    return;
                }
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                String json = GSON.toJson(new PageCache(copy, code));
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 对应原版 clearCache（AutoCacheUtilKt 源行 113-129，异步走 HeavyTaskUtil；
     * 原版由 MainActivity.initBackgroundTaskNotImportant 在启动后台任务里调用，
     * 星辰接线点在 HkListActivity.onCreate）：先删修改时间超过 15 天的文件（目录跳过），
     * 再重列目录，文件数超过 200 时按修改时间降序排序、删掉最新 200 个之外的全部。
     */
    public static void clearCache(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        Task.execute(() -> {
            try {
                File dir = getCacheDir(appContext);
                File[] files = dir.listFiles();
                if (files != null) {
                    long now = System.currentTimeMillis();
                    for (File file : files) {
                        if (now - file.lastModified() > EXPIRE_MS && !file.isDirectory()) {
                            file.delete();
                        }
                    }
                }
                File[] rest = dir.listFiles();
                if (rest != null && rest.length > MAX_FILES) {
                    List<File> list = new ArrayList<>(Arrays.asList(rest));
                    list.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (int i = MAX_FILES; i < list.size(); i++) {
                        list.get(i).delete();
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    /** 对应原版 clearAllCache：整目录删（原版 FileUtil.deleteDirs(getCacheDir)）。 */
    public static void clearAllCache(Context context) {
        if (context == null) return;
        deleteDirs(getCacheDir(context));
    }

    private static void deleteDirs(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteDirs(child);
        }
        file.delete();
    }

    private static String readFile(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int off = 0;
            int read;
            while (off < bytes.length && (read = in.read(bytes, off, bytes.length - off)) >= 0) {
                off += read;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 与原版 StringUtil.md5 同算法：UTF-8 串的 MD5 小写十六进制。 */
    private static String md5(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}

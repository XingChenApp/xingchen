package com.fongmi.android.tv.api.hk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 海阔规则的存取管（设计文档 §4.1）。
 * 存储：filesDir/plugins/hk/&lt;规则名&gt;.json，与 plugins/py、plugins/js 并列。
 * 规则 JS 数据：filesDir/plugins/hk/data/&lt;规则名&gt;/（setItem/getItem 持久化，M2 用）。
 */
public class HkRuleManager {

    private static final String DIR = "plugins/hk";
    private static final String DATA_DIR = "plugins/hk/data";
    private static final String PREFS = "hk_rule";
    private static final String KEY_ENABLED_PREFIX = "enabled_";

    // 云口令（云剪贴板）：格式 云{version}oooole/{path}，version 为 1~10。
    // 逆向自海阔视界官方 App（com.example.hikerviewg v8.83）的 NetCutImporter：
    //   云Noooole/p/{note_id}   → netcut.cn 云便签
    //   云Noooole/apidb/{key}   → textdb.online
    //   云Noooole/{id}/{pwd}    → netcut 带密码（云6 新格式）
    private static final Pattern CLOUD_CODE_PATTERN =
            Pattern.compile("^云([1-9]|10)oooole(/.*)$");
    private static final String NETCUT_API = "http://netcut.cn/api/note2/info/?note_id=";
    private static final String TEXTDB_API_UPDATE = "https://api.textdb.online/update/";
    private static final String TEXTDB_GET = "https://textdb.online/";

    private static volatile HkRuleManager instance;

    public static HkRuleManager get() {
        if (instance == null) {
            synchronized (HkRuleManager.class) {
                if (instance == null) instance = new HkRuleManager();
            }
        }
        return instance;
    }

    private Context appContext() {
        return App.get();
    }

    /** 规则目录，不存在则创建。 */
    public File getDir() {
        Context ctx = appContext();
        File dir = ctx == null ? new File(DIR) : new File(ctx.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 某规则的持久化数据目录（setItem/getItem 用，M2）。 */
    public File getDataDir(String title) {
        File dir = new File(getDir(), "data/" + safeFileName(title));
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * 从 JSON 文本导入规则：解析 → 校验 → 落盘（保留原始 JSON 文本，不丢失未知字段）。
     *
     * @return 导入成功的规则
     * @throws Exception 解析失败或校验不通过时抛出，message 可直接提示用户
     */
    public HkRule importJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("规则内容为空");
        HkRule rule;
        try {
            rule = new Gson().fromJson(json, HkRule.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("规则 JSON 解析失败：" + e.getMessage());
        }
        if (rule == null) throw new IllegalArgumentException("规则 JSON 解析失败");
        rule.validate();
        File target = new File(getDir(), safeFileName(rule.getTitle()) + ".json");
        Files.write(target.toPath(), json.getBytes(StandardCharsets.UTF_8));
        rule.setEnabled(isEnabled(rule.getTitle()));
        return rule;
    }

    /** 从文件导入规则。 */
    public HkRule importFile(File src) throws Exception {
        if (src == null || !src.exists()) throw new IllegalArgumentException("规则文件不存在");
        byte[] bytes = Files.readAllBytes(src.toPath());
        return importJson(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * 从 zip 包导入规则：解压后找出所有 rule.json（根目录或子目录均可），逐个导入。
     * 一个 zip 可包含多个规则；zip 内条目也可能是规则数组；单个损坏的条目只跳过、不影响其它。
     *
     * @return 导入成功的规则列表
     * @throws Exception zip 损坏 / 其中没有合法规则时抛出，message 可直接提示用户
     */
    public List<HkRule> importZip(File zipFile) throws Exception {
        if (zipFile == null || !zipFile.exists()) throw new IllegalArgumentException("zip 文件不存在");
        List<HkRule> imported = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                // 跳过 macOS 垃圾与隐藏文件
                String base = name.substring(name.lastIndexOf('/') + 1);
                if (base.startsWith(".")) continue;
                if (name.startsWith("__MACOSX/")) continue;
                // 只认 .json
                if (!base.toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                byte[] bytes = readStream(zip.getInputStream(entry));
                try {
                    // 条目可能是单个规则，也可能是规则数组
                    imported.addAll(importJsonList(new String(bytes, StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    failed.add(base + "（" + e.getMessage() + "）");
                }
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("不是有效的 zip 包或文件已损坏");
        }
        if (imported.isEmpty()) {
            if (!failed.isEmpty()) throw new IllegalArgumentException("zip 中没有合法规则：" + failed.get(0));
            throw new IllegalArgumentException("zip 中没有找到 rule.json");
        }
        return imported;
    }

    /**
     * 从 .hkzip 包导入：内容为 rule.json + require.json + libs.zip。
     * rule.json 走导入；require.json 与 libs.zip 解压到规则数据目录（data/&lt;规则名&gt;/），
     * 供 require() 本地优先加载（无网络时也能用）。
     *
     * @return 导入成功的规则列表
     */
    public List<HkRule> importHkZip(File hkzipFile) throws Exception {
        if (hkzipFile == null || !hkzipFile.exists()) throw new IllegalArgumentException("hkzip 文件不存在");
        List<HkRule> imported = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        byte[] requireJson = null;
        Map<String, byte[]> libs = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(hkzipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String base = name.substring(name.lastIndexOf('/') + 1);
                if (base.startsWith(".") || name.startsWith("__MACOSX/")) continue;
                String lower = base.toLowerCase(Locale.ROOT);
                byte[] bytes = readStream(zip.getInputStream(entry));
                if (lower.equals("require.json")) {
                    requireJson = bytes;
                } else if (lower.equals("libs.zip")) {
                    // 内层 zip：解出所有 js 库
                    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                        ZipEntry e;
                        while ((e = zin.getNextEntry()) != null) {
                            if (!e.isDirectory()) {
                                String en = e.getName();
                                String eb = en.substring(en.lastIndexOf('/') + 1);
                                if (!eb.startsWith(".") && eb.toLowerCase(Locale.ROOT).endsWith(".js")) {
                                    libs.put(eb, readStream(zin));
                                }
                            }
                            zin.closeEntry();
                        }
                    } catch (Exception ignored) {
                    }
                } else if (lower.endsWith(".json")) {
                    try {
                        imported.addAll(importJsonList(new String(bytes, StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        failed.add(base + "（" + e.getMessage() + "）");
                    }
                }
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("不是有效的 hkzip 包或文件已损坏");
        }
        if (imported.isEmpty()) {
            if (!failed.isEmpty()) throw new IllegalArgumentException("hkzip 中没有合法规则：" + failed.get(0));
            throw new IllegalArgumentException("hkzip 中没有找到 rule.json");
        }
        // 落盘 require.json 与 libs，供 require() 本地加载
        for (HkRule rule : imported) {
            File dataDir = getDataDir(rule.getTitle());
            if (requireJson != null) {
                try {
                    Files.write(new File(dataDir, "require.json").toPath(), requireJson);
                } catch (Exception ignored) {
                }
            }
            if (!libs.isEmpty()) {
                File libsDir = new File(dataDir, "libs");
                libsDir.mkdirs();
                for (Map.Entry<String, byte[]> e : libs.entrySet()) {
                    try {
                        Files.write(new File(libsDir, e.getKey()).toPath(), e.getValue());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return imported;
    }

    /** 经典 IO 读流（API 24 可用，不依赖 java.nio）。 */
    private static byte[] readStream(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    /**
     * 导入 JSON 文本：兼容单个规则对象与规则数组（一个文件多个小程序）。
     * 数组中单个损坏/不合规的条目只跳过、不影响其它。
     *
     * @return 导入成功的规则列表（单个对象时为 1 个元素的列表）
     * @throws Exception 没有任何合法规则时抛出，message 可直接提示用户
     */
    public List<HkRule> importJsonList(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("规则内容为空");
        String t = json.trim();
        if (!t.startsWith("[")) {
            List<HkRule> single = new ArrayList<>();
            single.add(importJson(t));
            return single;
        }
        JsonArray arr;
        try {
            arr = JsonParser.parseString(t).getAsJsonArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("规则 JSON 数组解析失败：" + e.getMessage());
        }
        List<HkRule> imported = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Gson gson = new Gson();
        int idx = 0;
        for (JsonElement el : arr) {
            idx++;
            if (!el.isJsonObject()) {
                failed.add("第" + idx + "项不是对象");
                continue;
            }
            String title = "";
            try {
                if (el.getAsJsonObject().has("title")) title = el.getAsJsonObject().get("title").getAsString();
            } catch (Exception ignored) {
            }
            String label = title.isEmpty() ? "第" + idx + "项" : "「" + title + "」";
            try {
                imported.add(importJson(gson.toJson(el)));
            } catch (Exception e) {
                failed.add(label + "：" + e.getMessage());
            }
        }
        if (imported.isEmpty()) {
            throw new IllegalArgumentException("没有合法规则" + (failed.isEmpty() ? "" : "，" + failed.get(0)));
        }
        return imported;
    }

    /** 是否为 js: 规则文本（以 "js:" 开头，后面是 JS 代码）。 */
    public static boolean isJsRuleText(String text) {
        return text != null && text.trim().startsWith("js:");
    }

    /**
     * 从 JS 规则文本导入：内容以 "js:" 开头，后面是 JS 代码。
     * 包装成 rule.json（type=video，find_rule=js 内容），标题取自文件名。
     *
     * @param jsContent js: 开头的规则文本
     * @param fileName  来源文件名（可为 null），用于取标题
     * @return 导入成功的规则
     */
    public HkRule importJsRule(String jsContent, String fileName) throws Exception {
        if (jsContent == null || jsContent.trim().isEmpty()) throw new IllegalArgumentException("JS 规则内容为空");
        String t = jsContent.trim();
        if (!t.startsWith("js:")) throw new IllegalArgumentException("不是 js: 格式的规则");
        String title = fileName;
        if (title != null) {
            int slash = Math.max(title.lastIndexOf('/'), title.lastIndexOf('\\'));
            if (slash >= 0) title = title.substring(slash + 1);
            if (title.toLowerCase(Locale.ROOT).endsWith(".js")) title = title.substring(0, title.length() - 3);
            title = title.trim();
        }
        if (title == null || title.isEmpty()) title = "JS规则";
        JsonObject obj = new JsonObject();
        obj.addProperty("title", title);
        obj.addProperty("type", "video");
        obj.addProperty("url", "");
        obj.addProperty("find_rule", t);
        return importJson(new Gson().toJson(obj));
    }

    /** 是否为云口令格式（云1~云10 开头）。 */
    public static boolean isCloudCode(String text) {
        if (text == null) return false;
        return CLOUD_CODE_PATTERN.matcher(text.trim()).matches();
    }

    /**
     * 从云口令导入规则：解析口令 → 从云端拉取 rule.json → 走 importJson 落盘。
     *
     * @return 导入成功的规则
     * @throws Exception 口令格式错误 / 网络失败 / 内容不是合法规则时抛出
     */
    public HkRule importByCloudCode(String code) throws Exception {
        if (code == null || code.trim().isEmpty()) throw new IllegalArgumentException("口令为空");
        String raw = code.trim();
        Matcher m = CLOUD_CODE_PATTERN.matcher(raw);
        if (!m.matches()) throw new IllegalArgumentException("不是有效的云口令（需云1~云10开头）");
        String path = m.group(2); // 如 /p/xxxx、/apidb/yyyy、/id/pwd
        String json = fetchCloudJson(path);
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("云端返回内容为空");
        return importJson(json.trim());
    }

    /** 按口令 path 从云端拉取 rule.json 文本。 */
    private String fetchCloudJson(String path) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        // 1) /apidb/{key} → textdb.online（官方分享走的通道，已验证可用）
        if (path.startsWith("/apidb/")) {
            String key = path.substring("/apidb/".length()).split("[\\s/]")[0];
            if (key.isEmpty()) throw new IllegalArgumentException("口令中 apidb key 为空");
            Request req = new Request.Builder().url(TEXTDB_GET + key)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null)
                    throw new IllegalArgumentException("云端请求失败（HTTP " + resp.code() + "）");
                String b64 = resp.body().string().trim();
                String json = safeBase64Decode(b64);
                if (json == null) throw new IllegalArgumentException("云端内容不是有效的 base64 规则");
                return json;
            }
        }
        // 2) /p/{note_id} → netcut.cn 云便签（官方旧通道，可能已失效，best effort）
        if (path.startsWith("/p/")) {
            String noteId = path.substring("/p/".length()).split("[\\s/]")[0];
            if (noteId.isEmpty()) throw new IllegalArgumentException("口令中 note_id 为空");
            FormBody body = new FormBody.Builder().build();
            Request req = new Request.Builder().url(NETCUT_API + noteId).post(body)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null)
                    throw new IllegalArgumentException("云端请求失败（HTTP " + resp.code() + "）");
                String text = resp.body().string();
                String json = extractRuleJsonFromNetcut(text);
                if (json == null) throw new IllegalArgumentException("云端未返回有效规则（netcut 接口可能已变更）");
                return json;
            }
        }
        // 3) /{id}/{pwd} → 云6 新格式：netcut 带密码便签（best effort）
        String[] segs = path.split("/");
        // segs[0] 为空（path 以 / 开头），segs[1]=id，segs[2]=pwd（可选）
        if (segs.length >= 2 && !segs[1].isEmpty()) {
            String noteId = segs[1];
            FormBody.Builder fb = new FormBody.Builder();
            if (segs.length >= 3 && !segs[2].isEmpty()) fb.add("password", segs[2]);
            Request req = new Request.Builder().url(NETCUT_API + noteId).post(fb.build())
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null)
                    throw new IllegalArgumentException("云端请求失败（HTTP " + resp.code() + "）");
                String text = resp.body().string();
                String json = extractRuleJsonFromNetcut(text);
                if (json == null) throw new IllegalArgumentException("云端未返回有效规则（netcut 接口可能已变更）");
                return json;
            }
        }
        throw new IllegalArgumentException("无法识别的云口令路径格式");
    }

    /** 从 netcut API 响应中提取 rule.json（JSON 字段 data.content 或直接内容）。 */
    private String extractRuleJsonFromNetcut(String text) {
        if (text == null) return null;
        String t = text.trim();
        // 可能是直接返回的 JSON 文本
        if (t.startsWith("{") && t.contains("\"title\"")) return t;
        // 可能是包装过的 JSON：{"data": {"content": "..."}} 等形态，尽力提取
        try {
            JsonObject obj = JsonParser.parseString(t).getAsJsonObject();
            if (obj.has("data")) {
                JsonObject data = obj.getAsJsonObject("data");
                if (data.has("content")) {
                    String content = data.get("content").getAsString();
                    if (content.contains("\"title\"")) return content;
                }
                // content 可能是 base64
                if (data.has("content")) {
                    String decoded = safeBase64Decode(data.get("content").getAsString());
                    if (decoded != null && decoded.contains("\"title\"")) return decoded;
                }
            }
            if (obj.has("content")) {
                String content = obj.get("content").getAsString();
                if (content.contains("\"title\"")) return content;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 兼容标准 / URL 安全两种 base64 的解码，失败返回 null。 */
    private String safeBase64Decode(String b64) {
        if (b64 == null) return null;
        String s = b64.trim().replaceAll("\\s+", "");
        // 补齐 padding
        int mod = s.length() % 4;
        if (mod == 1) return null;
        String padded = s + "====".substring(0, (4 - mod) % 4);
        int[] flags = {Base64.DEFAULT, Base64.URL_SAFE, Base64.NO_WRAP};
        for (int flag : flags) {
            try {
                byte[] bytes = Base64.decode(padded, flag);
                String decoded = new String(bytes, StandardCharsets.UTF_8);
                if (decoded.contains("{") && decoded.contains("}")) return decoded;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 导出规则为云口令：rule.json → base64 → 上传 textdb.online → 返回 云6oooole/apidb/{key}。
     * 与官方分享走同一通道，官方 App 也可导入。
     *
     * @return 生成的云口令
     */
    public String exportAsCloudCode(String title) throws Exception {
        File f = new File(getDir(), safeFileName(title) + ".json");
        if (!f.exists()) throw new IllegalArgumentException("规则不存在：" + title);
        byte[] bytes = Files.readAllBytes(f.toPath());
        String json = new String(bytes, StandardCharsets.UTF_8);
        String b64 = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String key = "xc" + System.currentTimeMillis() / 1000 + "_" + Math.abs(title.hashCode());

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        FormBody body = new FormBody.Builder()
                .add("key", key)
                .add("value", b64)
                .build();
        Request req = new Request.Builder().url(TEXTDB_API_UPDATE).post(body)
                .header("User-Agent", "Mozilla/5.0").build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new IllegalArgumentException("上传失败（HTTP " + resp.code() + "）");
            String respText = resp.body().string();
            try {
                JsonObject obj = JsonParser.parseString(respText).getAsJsonObject();
                if (obj.has("status") && obj.get("status").getAsInt() == 1) {
                    return "云6oooole/apidb/" + key;
                }
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("上传失败：服务器返回异常");
        }
    }

    /**
     * 导出规则为文件：复制 rule.json 到指定目录（默认 Download）。
     *
     * @return 导出的文件
     */
    public File exportAsFile(String title, File destDir) throws Exception {
        File src = new File(getDir(), safeFileName(title) + ".json");
        if (!src.exists()) throw new IllegalArgumentException("规则不存在：" + title);
        if (destDir != null && !destDir.exists()) destDir.mkdirs();
        File dest = new File(destDir != null ? destDir : getDir(),
                safeFileName(title) + ".hkrule.json");
        byte[] bytes = Files.readAllBytes(src.toPath());
        Files.write(dest.toPath(), bytes);
        return dest;
    }

    /** 列出所有已导入规则（损坏的文件跳过，不影响整体）。 */
    public List<HkRule> getRules() {
        List<HkRule> list = new ArrayList<>();
        File dir = getDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return list;
        Gson gson = new Gson();
        for (File f : files) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                HkRule rule = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), HkRule.class);
                if (rule == null) continue;
                rule.setEnabled(isEnabled(rule.getTitle()));
                list.add(rule);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    /** 按标题取规则（标题即文件名）。 */
    public HkRule getRule(String title) {
        if (title == null) return null;
        File f = new File(getDir(), safeFileName(title) + ".json");
        if (!f.exists()) return null;
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            HkRule rule = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), HkRule.class);
            if (rule != null) rule.setEnabled(isEnabled(rule.getTitle()));
            return rule;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 删除规则（含启用开关记录）。 */
    public boolean delete(String title) {
        if (title == null) return false;
        File f = new File(getDir(), safeFileName(title) + ".json");
        boolean ok = !f.exists() || f.delete();
        try {
            Context ctx = appContext();
            if (ctx != null) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_ENABLED_PREFIX + title).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ok;
    }

    public void setEnabled(String title, boolean enabled) {
        if (title == null) return;
        try {
            Context ctx = appContext();
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                sp.edit().putBoolean(KEY_ENABLED_PREFIX + title, enabled).apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 默认启用；只有用户手动关闭后才为 false。 */
    public boolean isEnabled(String title) {
        try {
            Context ctx = appContext();
            if (ctx == null) return true;
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED_PREFIX + title, true);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    /** 只返回启用中的规则。 */
    public List<HkRule> getEnabledRules() {
        List<HkRule> all = getRules();
        List<HkRule> enabled = new ArrayList<>();
        for (HkRule r : all) if (r.isEnabled()) enabled.add(r);
        return enabled;
    }

    /** 文件名安全化：去非法字符，防路径穿越。 */
    private static String safeFileName(String title) {
        if (title == null) return "unnamed";
        String s = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (s.isEmpty()) s = "unnamed";
        if (s.length() > 80) s = s.substring(0, 80);
        return s;
    }
}

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
import java.util.zip.ZipOutputStream;

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
    // 逆向自海阔视界官方 App（com.example.hikerviewg v8.83）各 Importer 的真实通道（2026-10-10 实测）：
    //   云1oooole/{id}        → pastebin.com（需官方内嵌 API Key，本 App 不用）
    //   云2oooole/apidb/{key} → textdb.online（可用）
    //   云2oooole/p/{note_id} → netcut.cn（官方已下线 API，失效）
    //   云5oooole/{id}        → cmd.im（可用：GET https://cmd.im/{id} 取 .test_box 文本）
    //   云6oooole/xxxxxx/{path}(@{pwd}) → pasteme.tyrantg.com（可用：GET /api/getContent/{path}(@{pwd})）
    //   云7/9/10              → note.ms / txtpad.cn / hastebin（未验证，暂不支持）
    private static final Pattern CLOUD_CODE_PATTERN =
            Pattern.compile("^云([1-9]|10)oooole(/.*)$");
    private static final String TEXTDB_API_UPDATE = "https://api.textdb.online/update/";
    private static final String TEXTDB_GET = "https://textdb.online/";
    private static final String PASTEME_API = "https://pasteme.tyrantg.com/api/getContent/";
    private static final String PASTEME_REFERER = "https://pasteme.tyrantg.com/";
    private static final String CMDIM_BASE = "https://cmd.im";

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

    private static final String CONFIG_DIR = "plugins/hk/config";

    /** 规则配置目录（海阔"长按规则→设置"的 key-value，如 config.host），不存在则创建。 */
    public File getConfigDir() {
        Context ctx = appContext();
        File dir = ctx == null ? new File(CONFIG_DIR) : new File(ctx.getFilesDir(), CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** 某规则的配置文件（JSON 对象），不存在返回 null。 */
    public File getRuleConfigFile(String title) {
        File f = new File(getConfigDir(), safeFileName(title) + ".json");
        return f.exists() ? f : null;
    }

    /** 读某规则的配置 JSON 文本，不存在/读失败返回 "{}"。 */
    public String loadRuleConfig(String title) {
        try {
            File f = getRuleConfigFile(title);
            if (f == null) return "{}";
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            String s = new String(bytes, java.nio.charset.Charset.forName("UTF-8")).trim();
            return s.isEmpty() ? "{}" : s;
        } catch (Throwable ignored) {
            return "{}";
        }
    }

    /** 写某规则的配置 JSON 文本（供后续"规则设置"页用）。 */
    public void saveRuleConfig(String title, String json) {
        try {
            File f = new File(getConfigDir(), safeFileName(title) + ".json");
            java.nio.file.Files.write(f.toPath(),
                    (json == null || json.trim().isEmpty() ? "{}" : json.trim()).getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 解析后尚未落盘的规则（含原始 JSON 文本，落盘时原样写入，不丢失未知字段）。
     * 一键导入先全部解析 → 弹窗让用户勾选 → 只把选中的 saveRule 落盘。
     */
    public static class ParsedRule {
        public final HkRule rule;
        public final String json;

        public ParsedRule(HkRule rule, String json) {
            this.rule = rule;
            this.json = json;
        }
    }

    /** .hkzip 包的解析结果：规则（未落盘）+ 附带资源（require.json / libs）。 */
    public static class HkZipData {
        public final List<ParsedRule> rules = new ArrayList<>();
        public byte[] requireJson;
        public final Map<String, byte[]> libs = new LinkedHashMap<>();
    }

    /**
     * 从 JSON 文本解析规则：解析 → 校验，不落盘。
     *
     * @return 解析通过的规则
     * @throws Exception 解析失败或校验不通过时抛出，message 可直接提示用户
     */
    public HkRule parseJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("规则内容为空");
        HkRule rule;
        try {
            rule = new Gson().fromJson(json, HkRule.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("规则 JSON 解析失败：" + e.getMessage());
        }
        if (rule == null) throw new IllegalArgumentException("规则 JSON 解析失败");
        rule.validate();
        return rule;
    }

    /** 把解析好的规则落盘（保留原始 JSON 文本，不丢失未知字段）。 */
    public void saveRule(HkRule rule, String rawJson) throws Exception {
        if (rule == null) throw new IllegalArgumentException("规则为空");
        if (rawJson == null || rawJson.isEmpty()) rawJson = new Gson().toJson(rule);
        File target = new File(getDir(), safeFileName(rule.getTitle()) + ".json");
        Files.write(target.toPath(), rawJson.getBytes(StandardCharsets.UTF_8));
        rule.setEnabled(isEnabled(rule.getTitle()));
    }

    /**
     * 从 JSON 文本导入规则：解析 → 校验 → 落盘（保留原始 JSON 文本，不丢失未知字段）。
     *
     * @return 导入成功的规则
     * @throws Exception 解析失败或校验不通过时抛出，message 可直接提示用户
     */
    public HkRule importJson(String json) throws Exception {
        HkRule rule = parseJson(json);
        saveRule(rule, json);
        return rule;
    }

    /** 从文件导入规则。 */
    public HkRule importFile(File src) throws Exception {
        if (src == null || !src.exists()) throw new IllegalArgumentException("规则文件不存在");
        byte[] bytes = Files.readAllBytes(src.toPath());
        return importJson(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * 从 zip 包解析规则（不落盘）：解压后找出所有 .json（根目录或子目录均可），逐个解析。
     * 一个 zip 可包含多个规则；zip 内条目也可能是规则数组；单个损坏的条目只跳过、不影响其它。
     *
     * @return 解析成功的规则列表
     * @throws Exception zip 损坏 / 其中没有合法规则时抛出，message 可直接提示用户
     */
    public List<ParsedRule> parseZip(File zipFile) throws Exception {
        if (zipFile == null || !zipFile.exists()) throw new IllegalArgumentException("zip 文件不存在");
        List<ParsedRule> parsed = new ArrayList<>();
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
                    parsed.addAll(parseJsonList(new String(bytes, StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    failed.add(base + "（" + e.getMessage() + "）");
                }
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("不是有效的 zip 包或文件已损坏");
        }
        if (parsed.isEmpty()) {
            if (!failed.isEmpty()) throw new IllegalArgumentException("zip 中没有合法规则：" + failed.get(0));
            throw new IllegalArgumentException("zip 中没有找到 rule.json");
        }
        return parsed;
    }

    /**
     * 从 zip 包导入规则：解压后找出所有 rule.json（根目录或子目录均可），逐个导入。
     * 一个 zip 可包含多个规则；zip 内条目也可能是规则数组；单个损坏的条目只跳过、不影响其它。
     *
     * @return 导入成功的规则列表
     * @throws Exception zip 损坏 / 其中没有合法规则时抛出，message 可直接提示用户
     */
    public List<HkRule> importZip(File zipFile) throws Exception {
        List<ParsedRule> parsed = parseZip(zipFile);
        List<HkRule> imported = new ArrayList<>();
        for (ParsedRule p : parsed) {
            saveRule(p.rule, p.json);
            imported.add(p.rule);
        }
        return imported;
    }

    /**
     * 从 .hkzip 包解析：内容为 rule.json + require.json + libs.zip。
     * rule.json 只解析不落盘；require.json 与 libs.zip 缓存在返回对象中，
     * 用户勾选后只把选中规则的附带资源落盘到 data/&lt;规则名&gt;/。
     *
     * @return 解析结果
     * @throws Exception 包损坏 / 其中没有合法规则时抛出
     */
    public HkZipData parseHkZip(File hkzipFile) throws Exception {
        if (hkzipFile == null || !hkzipFile.exists()) throw new IllegalArgumentException("hkzip 文件不存在");
        HkZipData data = new HkZipData();
        List<String> failed = new ArrayList<>();
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
                    data.requireJson = bytes;
                } else if (lower.equals("libs.zip")) {
                    // 内层 zip：解出所有 js 库
                    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                        ZipEntry e;
                        while ((e = zin.getNextEntry()) != null) {
                            if (!e.isDirectory()) {
                                String en = e.getName();
                                String eb = en.substring(en.lastIndexOf('/') + 1);
                                if (!eb.startsWith(".") && eb.toLowerCase(Locale.ROOT).endsWith(".js")) {
                                    data.libs.put(eb, readStream(zin));
                                }
                            }
                            zin.closeEntry();
                        }
                    } catch (Exception ignored) {
                    }
                } else if (lower.endsWith(".json")) {
                    try {
                        data.rules.addAll(parseJsonList(new String(bytes, StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        failed.add(base + "（" + e.getMessage() + "）");
                    }
                }
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("不是有效的 hkzip 包或文件已损坏");
        }
        if (data.rules.isEmpty()) {
            if (!failed.isEmpty()) throw new IllegalArgumentException("hkzip 中没有合法规则：" + failed.get(0));
            throw new IllegalArgumentException("hkzip 中没有找到 rule.json");
        }
        return data;
    }

    /** 把 .hkzip 的附带资源（require.json / libs）落盘到指定规则的数据目录，供 require() 本地加载。 */
    public void saveHkZipAssets(HkZipData data, List<HkRule> rules) {
        if (data == null || rules == null) return;
        for (HkRule rule : rules) {
            File dataDir = getDataDir(rule.getTitle());
            if (data.requireJson != null) {
                try {
                    Files.write(new File(dataDir, "require.json").toPath(), data.requireJson);
                } catch (Exception ignored) {
                }
            }
            if (!data.libs.isEmpty()) {
                File libsDir = new File(dataDir, "libs");
                libsDir.mkdirs();
                for (Map.Entry<String, byte[]> e : data.libs.entrySet()) {
                    try {
                        Files.write(new File(libsDir, e.getKey()).toPath(), e.getValue());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * 从 .hkzip 包导入：内容为 rule.json + require.json + libs.zip。
     * rule.json 走导入；require.json 与 libs.zip 解压到规则数据目录（data/&lt;规则名&gt;/），
     * 供 require() 本地优先加载（无网络时也能用）。
     *
     * @return 导入成功的规则列表
     */
    public List<HkRule> importHkZip(File hkzipFile) throws Exception {
        HkZipData data = parseHkZip(hkzipFile);
        List<HkRule> imported = new ArrayList<>();
        for (ParsedRule p : data.rules) {
            saveRule(p.rule, p.json);
            imported.add(p.rule);
        }
        // 落盘 require.json 与 libs，供 require() 本地加载
        saveHkZipAssets(data, imported);
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
     * 解析 JSON 文本（不落盘）：兼容单个规则对象与规则数组（一个文件多个小程序）。
     * 数组中单个损坏/不合规的条目只跳过、不影响其它。
     *
     * @return 解析成功的规则列表（单个对象时为 1 个元素的列表）
     * @throws Exception 没有任何合法规则时抛出，message 可直接提示用户
     */
    public List<ParsedRule> parseJsonList(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("规则内容为空");
        String t = json.trim();
        if (!t.startsWith("[")) {
            List<ParsedRule> single = new ArrayList<>();
            HkRule rule = parseJson(t);
            single.add(new ParsedRule(rule, t));
            return single;
        }
        JsonArray arr;
        try {
            arr = JsonParser.parseString(t).getAsJsonArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("规则 JSON 数组解析失败：" + e.getMessage());
        }
        List<ParsedRule> parsed = new ArrayList<>();
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
                String raw = gson.toJson(el);
                HkRule rule = parseJson(raw);
                parsed.add(new ParsedRule(rule, raw));
            } catch (Exception e) {
                failed.add(label + "：" + e.getMessage());
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("没有合法规则" + (failed.isEmpty() ? "" : "，" + failed.get(0)));
        }
        return parsed;
    }

    /**
     * 导入 JSON 文本：兼容单个规则对象与规则数组（一个文件多个小程序）。
     * 数组中单个损坏/不合规的条目只跳过、不影响其它。
     *
     * @return 导入成功的规则列表（单个对象时为 1 个元素的列表）
     * @throws Exception 没有任何合法规则时抛出，message 可直接提示用户
     */
    public List<HkRule> importJsonList(String json) throws Exception {
        List<ParsedRule> parsed = parseJsonList(json);
        List<HkRule> imported = new ArrayList<>();
        for (ParsedRule p : parsed) {
            saveRule(p.rule, p.json);
            imported.add(p.rule);
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
        String version = m.group(1);
        String path = m.group(2); // 如 /p/xxxx、/apidb/yyyy、/xxxxxx/pppp、/f7c6
        String json = fetchCloudJson(path, version);
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("云端返回内容为空");
        return importJson(json.trim());
    }

    /** 按口令 path 从云端拉取 rule.json 文本（通道按官方 App 逆向结果路由）。 */
    private String fetchCloudJson(String path, String version) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        // 1) /apidb/{key} → textdb.online（官方云2分享通道，已验证可用）
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
        // 2) /xxxxxx/{path}(@{pwd}) → pasteme.tyrantg.com（官方云6通道，白阑剪贴板，已验证可用）
        if (path.startsWith("/xxxxxx/")) {
            String rest = path.substring("/xxxxxx/".length()).split("\\s")[0];
            if (rest.isEmpty()) throw new IllegalArgumentException("口令中缺少便签ID");
            Request req = new Request.Builder().url(PASTEME_API + rest)
                    .header("referer", PASTEME_REFERER)
                    .header("cookie", "")
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null)
                    throw new IllegalArgumentException("云端请求失败（HTTP " + resp.code() + "）");
                String json = extractRuleJsonFromPasteme(resp.body().string());
                if (json == null) throw new IllegalArgumentException("云端未返回有效规则（便签不存在或密码错误）");
                return json;
            }
        }
        // 3) 云5 → cmd.im（官方云5通道，已验证可用）
        if ("5".equals(version)) {
            String id = path.substring(1).split("[\\s/]")[0];
            if (id.isEmpty()) throw new IllegalArgumentException("口令中缺少便签ID");
            Request req = new Request.Builder().url(CMDIM_BASE + "/" + id)
                    .header("User-Agent", "Mozilla/5.0").build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null)
                    throw new IllegalArgumentException("云端请求失败（HTTP " + resp.code() + "）");
                String json = extractRuleJsonFromCmdIm(resp.body().string());
                if (json == null) throw new IllegalArgumentException("云端未返回有效规则（便签不存在或内容格式不对）");
                return json;
            }
        }
        // 4) /p/{note_id} → netcut.cn：官方已下线该接口，通道失效，给明确提示
        if (path.startsWith("/p/")) {
            throw new IllegalArgumentException("该口令走 netcut 通道，netcut 已停止开放接口，无法拉取。"
                    + "请让分享者用「导出为云口令」（云5/云6通道）重新分享，或索取规则文件导入。");
        }
        throw new IllegalArgumentException("暂不支持该云口令通道（当前支持：云2/apidb、云5、云6）");
    }

    /** 从 pasteme API 响应中提取规则内容：{"return_code":0,"data":"...rule.json..."}。 */
    private String extractRuleJsonFromPasteme(String text) {
        if (text == null) return null;
        try {
            JsonObject obj = JsonParser.parseString(text.trim()).getAsJsonObject();
            if (obj.has("return_code") && obj.get("return_code").getAsInt() != 0) return null;
            if (!obj.has("data") || obj.get("data").isJsonNull()) return null;
            String data = obj.get("data").getAsString().trim();
            if (data.isEmpty()) return null;
            return unwrapShareText(data);
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 从 cmd.im 页面中提取 .test_box 文本（规则内容）。 */
    private String extractRuleJsonFromCmdIm(String html) {
        if (html == null) return null;
        try {
            Matcher m = Pattern.compile("class=\"test_box\"[^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html);
            if (!m.find()) return null;
            String text = m.group(1).replaceAll("<[^>]+>", "").trim();
            // HTML 转义还原
            text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&quot;", "\"").replace("&#39;", "'");
            if (text.isEmpty()) return null;
            return unwrapShareText(text);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 还原分享包装：内容可能是 ￥…￥base64://@标题@BASE64 包装（cmd.im 常见），
     * 也可能是裸 rule.json / 规则数组 / js: 文本。
     */
    private String unwrapShareText(String text) {
        if (text == null) return null;
        String t = text.trim();
        int idx = t.indexOf("base64://");
        if (idx >= 0) {
            // base64://@标题@BASE64 → 取 @ 分隔的第 3 段解码（官方 importRuleByRuleText 同款）
            String[] segs = t.substring(idx + "base64://".length()).split("@");
            if (segs.length >= 3) {
                String decoded = safeBase64Decode(segs[2].replaceAll("\\s+", ""));
                if (decoded != null) return decoded;
            }
            return null;
        }
        return t;
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
     * 导出规则为云口令（textdb 通道）：rule.json → base64 → 上传 textdb.online
     * → 返回 云2oooole/apidb/{key}（与官方 App 的云2分享格式一致，官方 App 可导入）。
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
                    return "云2oooole/apidb/" + key;
                }
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("上传失败：服务器返回异常");
        }
    }

    /**
     * 导出规则为云5短口令（cmd.im 通道）：POST rule.json → 302 跳转取短 ID
     * → 返回 云5oooole/{id}（短格式，与官方 App 的云5分享格式一致，官方 App 可导入）。
     *
     * @return 生成的云口令
     */
    public String exportAsCmdImCode(String title) throws Exception {
        File f = new File(getDir(), safeFileName(title) + ".json");
        if (!f.exists()) throw new IllegalArgumentException("规则不存在：" + title);
        byte[] bytes = Files.readAllBytes(f.toPath());
        String json = new String(bytes, StandardCharsets.UTF_8);

        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        FormBody body = new FormBody.Builder().add("txt", json).build();
        Request req = new Request.Builder().url(CMDIM_BASE + "/").post(body)
                .header("Origin", CMDIM_BASE)
                .header("Referer", CMDIM_BASE + "/")
                .header("User-Agent", "Mozilla/5.0").build();
        try (Response resp = client.newCall(req).execute()) {
            String loc = resp.header("Location");
            if (loc == null || loc.isEmpty())
                throw new IllegalArgumentException("上传失败：未返回短链接（HTTP " + resp.code() + "）");
            // Location 形如 /8xwm
            String id = loc.substring(loc.lastIndexOf('/') + 1).split("[\\s?]")[0];
            if (id.isEmpty()) throw new IllegalArgumentException("上传失败：短链接格式异常");
            return "云5oooole/" + id;
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

    /**
     * 导出规则为 .hkzip 包：rule.json + require.json（若有）+ libs.zip（若有附带库）。
     *
     * @return 导出的文件
     */
    public File exportAsHkZip(String title, File destDir) throws Exception {
        File src = new File(getDir(), safeFileName(title) + ".json");
        if (!src.exists()) throw new IllegalArgumentException("规则不存在：" + title);
        if (destDir != null && !destDir.exists()) destDir.mkdirs();
        File dest = new File(destDir != null ? destDir : getDir(), safeFileName(title) + ".hkzip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(dest.toPath()))) {
            writeZipEntry(zos, "rule.json", Files.readAllBytes(src.toPath()));
            File dataDir = getDataDir(title);
            File require = new File(dataDir, "require.json");
            if (require.exists()) writeZipEntry(zos, "require.json", Files.readAllBytes(require.toPath()));
            File libsDir = new File(dataDir, "libs");
            File[] libs = libsDir.isDirectory() ? libsDir.listFiles() : null;
            if (libs != null && libs.length > 0) {
                ByteArrayOutputStream libsZip = new ByteArrayOutputStream();
                try (ZipOutputStream lzos = new ZipOutputStream(libsZip)) {
                    for (File f : libs) {
                        if (f.isFile()) writeZipEntry(lzos, f.getName(), Files.readAllBytes(f.toPath()));
                    }
                }
                writeZipEntry(zos, "libs.zip", libsZip.toByteArray());
            }
        }
        return dest;
    }

    private void writeZipEntry(ZipOutputStream zos, String name, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(bytes);
        zos.closeEntry();
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

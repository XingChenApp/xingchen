package com.fongmi.android.tv.api.hk;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * M3u8 广告清理工具（完整移植自 8.83）。
 *
 * <p>8.83 原文：{@code smali_classes2/com/example/hikerview/ui/video/util/M3u8ClearUtil.smali}
 * （2193 行）+ {@code model/M3u8ClearAdRule.smali}。
 *
 * <p>8.83 语义（JSEngine.proxyClearM3u8, JSEngine.smali:21401）：
 * <ol>
 *   <li>若 URL 带 {@code ?url=xxx.m3u8} 参数，递归处理内层 URL；</li>
 *   <li>按规则 hosts 匹配 URL（hosts 为空则全匹配）；</li>
 *   <li>拉取 m3u8 内容；若为 master 列表（含 #EXT-X-STREAM-INF），跟随第一个子列表递归；</li>
 *   <li>补全相对 URL（分片地址、#EXT-X-KEY 的 URI）；</li>
 *   <li>按 M3u8ClearAdRule 规则删除广告片段；</li>
 *   <li>返回清理后的内容；若为空则调用方返回原 URL。</li>
 * </ol>
 *
 * <p>适配说明（逻辑不动，仅换依赖）：
 * <ul>
 *   <li>fastjson → Gson；</li>
 *   <li>HttpRequestUtil.getResponseString → HkHttp.client()（OkHttp）；</li>
 *   <li>ExoPlayer UriUtil.resolve → java.net.URI.resolve；</li>
 *   <li>Timber → Log；ThreadTool.async → 单线程池；</li>
 *   <li>规则持久化：存 app filesDir/hk_m3u8_rules.json（8.83 走文件）。</li>
 * </ul>
 */
public class HkM3u8ClearUtil {

    private static final String TAG = "HkM3u8ClearUtil";
    private static final String RULES_FILE = "hk_m3u8_rules.json";

    private static final Pattern REGEX_MEDIA_DURATION = Pattern.compile("#EXTINF:([\\d\\.]+)\\b");
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");
    private static final Pattern REGEX_X_DISCONTINUITY =
            Pattern.compile("#EXT-X-DISCONTINUITY[\\s\\S]*?(?=#EXT-X-DISCONTINUITY|$)");

    private static final String TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY";
    private static final String TAG_KEY = "#EXT-X-KEY";
    private static final String TAG_MEDIA_DURATION = "#EXTINF";

    private static volatile List<HkM3u8ClearAdRule> rules = new ArrayList<>();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    private HkM3u8ClearUtil() {
    }

    /**
     * 1.3.28 状态病收尾：m3u8 清理缓存目录只增不减，越用越大。
     * 每次写缓存后调一次：只留最新的 100 个文件，总量超 200MB 时从最旧的删。
     * 纯文件操作，删错了下次播自动重建，不影响播放。
     */
    public static void pruneCacheDir(File dir) {
        try {
            if (dir == null || !dir.isDirectory()) return;
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            long total = 0;
            for (File f : files) total += f.length();
            int remain = files.length;
            for (File f : files) {
                if (remain <= 100 && total <= 200L * 1024 * 1024) break;
                long len = f.length();
                if (f.delete()) {
                    total -= len;
                    remain--;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 规则管理（对应 8.83 addRule/deleteRule/updateRule/getRules/saveByJSON）
    // ------------------------------------------------------------------

    public static List<HkM3u8ClearAdRule> getRules() {
        return rules;
    }

    public static void addRule(HkM3u8ClearAdRule rule) {
        if (rule == null) return;
        rules.add(rule);
        persistAsync();
    }

    public static void deleteRule(HkM3u8ClearAdRule rule) {
        if (rule == null) return;
        rules.remove(rule);
        persistAsync();
    }

    public static void updateRule(HkM3u8ClearAdRule rule) {
        if (rule == null) return;
        // 8.83 updateRule：按 name 匹配替换（smali:2106）
        for (int i = 0; i < rules.size(); i++) {
            HkM3u8ClearAdRule r = rules.get(i);
            if (r != null && TextUtils.equals(r.getName(), rule.getName())) {
                rules.set(i, rule);
                persistAsync();
                return;
            }
        }
        rules.add(rule);
        persistAsync();
    }

    /**
     * 从 JSON 保存规则（对应 8.83 saveByJSON）。
     * 空 JSON 则清空规则。
     */
    public static void saveByJSON(String json) {
        try {
            if (TextUtils.isEmpty(json)) {
                rules.clear();
            } else {
                List<HkM3u8ClearAdRule> list = GSON.fromJson(json,
                        TypeToken.getParameterized(List.class, HkM3u8ClearAdRule.class).getType());
                rules = list != null ? list : new ArrayList<HkM3u8ClearAdRule>();
            }
            persistAsync();
        } catch (Throwable e) {
            Log.e(TAG, "saveByJSON failed", e);
        }
    }

    public static String getJSONString() {
        try {
            return GSON.toJson(rules);
        } catch (Throwable e) {
            return "[]";
        }
    }

    /** 从文件加载规则（8.83 initRules/loadSub 的本地部分）。 */
    public static void initRules() {
        EXEC.execute(() -> {
            try {
                File f = rulesFile();
                if (f != null && f.exists()) {
                    String json = readFile(f);
                    if (!TextUtils.isEmpty(json)) {
                        List<HkM3u8ClearAdRule> list = GSON.fromJson(json,
                                TypeToken.getParameterized(List.class, HkM3u8ClearAdRule.class).getType());
                        if (list != null) rules = list;
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "initRules failed", e);
            }
        });
    }

    private static void persistAsync() {
        final String json = getJSONString();
        EXEC.execute(() -> {
            try {
                File f = rulesFile();
                if (f != null) writeFile(f, json);
            } catch (Throwable e) {
                Log.e(TAG, "persist rules failed", e);
            }
        });
    }

    private static File rulesFile() {
        try {
            return new File(com.fongmi.android.tv.App.get().getFilesDir(), RULES_FILE);
        } catch (Throwable e) {
            return null;
        }
    }

    private static String readFile(File f) {
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            try {
                int off = 0, n;
                while (off < data.length && (n = fis.read(data, off, data.length - off)) != -1) off += n;
            } finally {
                fis.close();
            }
            return new String(data, "UTF-8");
        } catch (Throwable e) {
            return null;
        }
    }

    private static void writeFile(File f, String content) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(f);
            try {
                fw.write(content != null ? content : "");
            } finally {
                fw.close();
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // 主入口（对应 8.83 getProxyContent 三个重载）
    // ------------------------------------------------------------------

    public static String getProxyContent(String url, Map<String, String> headers) {
        return getProxyContent(url, headers, getRules());
    }

    public static String getProxyContent(String url, String ua) {
        Map<String, String> headers = null;
        if (!TextUtils.isEmpty(ua)) {
            headers = new java.util.HashMap<>();
            headers.put("User-Agent", ua);
        }
        return getProxyContent(url, headers, getRules());
    }

    /**
     * 完整逻辑（8.83 smali:446-779）。
     *
     * @return 清理后的 m3u8 文本；失败/无匹配规则返回 ""
     */
    public static String getProxyContent(String url, Map<String, String> headers,
                                         List<HkM3u8ClearAdRule> ruleList) {
        return getProxyContent(url, headers, ruleList, new HashSet<>(), 0);
    }

    private static final int PROXY_CONTENT_MAX_DEPTH = 10;

    private static String getProxyContent(String url, Map<String, String> headers,
                                          List<HkM3u8ClearAdRule> ruleList,
                                          Set<String> visited, int depth) {
        try {
            if (TextUtils.isEmpty(url)) return "";
            if (depth > PROXY_CONTENT_MAX_DEPTH) return "";
            if (!visited.add(url)) return "";
            if (ruleList == null) ruleList = getRules();

            // 1. ?url=xxx.m3u8 内层递归（smali:258-264）
            Uri uri = Uri.parse(url);
            String urlParam = uri.getQueryParameter("url");
            if (!TextUtils.isEmpty(urlParam) && urlParam.contains(".m3u8")) {
                String inner = getProxyContent(urlParam, headers, ruleList, visited, depth + 1);
                if (!TextUtils.isEmpty(inner) && inner.contains("#EXTM3U")) return inner;
            }

            // 2. host 匹配串 "host1,host2"（smali:266）
            String hostStr = host(uri) + "," + host(urlParam);

            for (HkM3u8ClearAdRule rule : ruleList) {
                if (rule == null) continue;
                List<String> hosts = rule.getHosts();
                if (hosts == null || hosts.isEmpty()) {
                    hosts = new ArrayList<>();
                    hosts.add(""); // hosts 为空则全匹配（smali:269-271）
                }
                for (String h : hosts) {
                    if (TextUtils.isEmpty(h) || containOrMatch(hostStr, h)) {
                        // 3. 命中规则：拉取内容（smali:275）
                        String body = fetchContent(url, headers);
                        if (TextUtils.isEmpty(body)) return body;
                        if (!body.contains("#EXTM3U")) return "";

                        body = body.replace("\r\n", "\n");

                        // 4. master 列表：跟随第一个子列表递归（smali:284-287）
                        String[] lines = body.split("\n");
                        for (int i = 0; i < lines.length; i++) {
                            if (lines[i].startsWith("#EXT-X-STREAM-INF") && i < lines.length - 1) {
                                String sub = resolveUrl(url, lines[i + 1]);
                                return getProxyContent(sub, headers, ruleList, visited, depth + 1);
                            }
                        }

                        // 5. 补全相对 URL（smali:290-292）
                        StringBuilder sb = new StringBuilder();
                        for (String line : body.split("\n")) {
                            if (shouldResolve(line)) line = resolve(url, line);
                            sb.append(line).append("\n");
                        }

                        // 6. 按规则删除广告片段（smali:293-294）
                        return clean(sb.toString(), rule.getRegex());
                    }
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "getProxyContent failed", e);
        }
        return "";
    }

    // ------------------------------------------------------------------
    // 广告清理核心（8.83 smali:100-198 clean / 1725-2050 scan）
    // ------------------------------------------------------------------

    /**
     * 按 regex 列表清理广告（8.83 clean）。
     * <p>
     * regex 分两种：
     * <ul>
     *   <li>含 {@code #EXTINF}/{@code #EXT-X-DISCONTINUITY} 的：按正则直接删除；</li>
     *   <li>纯数字的：记标记，走 {@link #scan} 按分片总时长匹配删除。</li>
     * </ul>
     */
    private static String clean(String content, List<String> regexList) {
        if (content == null) return "";
        if (regexList == null || regexList.isEmpty()) return content;

        boolean needScan = false;
        for (String regex : regexList) {
            if (regex == null) continue;
            if (regex.contains(TAG_DISCONTINUITY) || regex.contains(TAG_MEDIA_DURATION)) {
                content = content.replaceAll(regex, "");
            } else if (isDouble(regex)) {
                needScan = true;
            }
        }
        if (needScan) {
            content = scan(content, regexList);
        }
        return content;
    }

    /**
     * 按分片总时长扫描删除广告段（8.83 scan, smali:1725-2050）。
     * <p>
     * 逻辑：按 {@code #EXT-X-DISCONTINUITY} 切块；先算 DISCONTINUITY 之前部分的总时长，
     * 再逐块算总时长；若总时长字符串以某个数字 regex 开头，则删除该段。
     * 被删段若含 {@code #EXT-X-ENDLIST}，则在末尾补回该标记。
     */
    private static String scan(String content, List<String> regexList) {
        Matcher m = REGEX_X_DISCONTINUITY.matcher(content);
        boolean computedHead = false;

        while (m.find()) {
            if (!computedHead) {
                // 首段：DISCONTINUITY 之前、从第一个 #EXTINF 起的内容
                String[] lines = content.split("\n");
                List<String> head = new ArrayList<>();
                boolean foundExtinf = false;
                for (String line : lines) {
                    if (line.startsWith(TAG_MEDIA_DURATION)) foundExtinf = true;
                    if (foundExtinf) head.add(line);
                    if (line.startsWith(TAG_DISCONTINUITY)) break;
                }
                String segment = join(head, "\n") + "\n";
                BigDecimal total = sumDurations(segment);

                for (String regex : regexList) {
                    if (regex != null && total.toString().startsWith(regex)) {
                        content = content.replace(segment, "");
                        if (segment.contains("#EXT-X-ENDLIST")) {
                            content = content + "\n#EXT-X-ENDLIST";
                        }
                        break;
                    }
                }
                computedHead = true;
            }

            // 每个 DISCONTINUITY 块
            String block = m.group();
            BigDecimal total = sumDurations(block);
            for (String regex : regexList) {
                if (regex != null && total.toString().startsWith(regex)) {
                    content = content.replace(block, "");
                    if (block.contains("#EXT-X-ENDLIST")) {
                        content = content + "\n#EXT-X-ENDLIST";
                    }
                    break;
                }
            }
            // 注意：8.83 在 while(m.find()) 循环内直接改 content；
            // 为与原文行为一致，这里同样在原串上替换（matcher 已基于旧串，不影响）。
        }
        return content;
    }

    private static BigDecimal sumDurations(String text) {
        BigDecimal total = BigDecimal.ZERO;
        Matcher m = REGEX_MEDIA_DURATION.matcher(text);
        while (m.find()) {
            try {
                total = total.add(new BigDecimal(m.group(1)));
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 小工具（8.83 原文直译）
    // ------------------------------------------------------------------

    /** 8.83 containOrMatch (smali:198)：contains 或正则 matches，异常返回 false。 */
    private static boolean containOrMatch(String text, String rule) {
        try {
            if (text == null || rule == null) return false;
            if (text.contains(rule)) return true;
            return text.matches(rule);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 8.83 isDouble (smali:1091)：可解析为 double 且 > 0。 */
    private static boolean isDouble(String s) {
        try {
            return Double.parseDouble(s) > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 8.83 host(Uri) (smali:1025)：host 小写去空格，null → ""。 */
    public static String host(Uri uri) {
        if (uri == null) return "";
        String h = uri.getHost();
        if (h == null) return "";
        return h.toLowerCase().trim();
    }

    /** 8.83 host(String) (smali:1053)。 */
    public static String host(String url) {
        if (url == null) return "";
        try {
            return host(Uri.parse(url));
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * 8.83 resolve (smali:1558)：补全相对 URL。
     * #EXT-X-KEY 行只解析其 URI="..." 部分并替换；其他行整体 resolve。
     */
    private static String resolve(String baseUrl, String line) {
        if (line.startsWith(TAG_KEY)) {
            Matcher m = REGEX_URI.matcher(line);
            if (m.find()) {
                String uri = m.group(1);
                if (uri != null) {
                    String abs = resolveUrl(baseUrl, uri);
                    return line.replace(uri, abs);
                }
            }
            return line;
        }
        return resolveUrl(baseUrl, line);
    }

    /** 8.83 shouldResolve (smali:2050)：相对分片地址、#EXT-X-KEY 行返回 true。 */
    private static boolean shouldResolve(String line) {
        if (line == null) return false;
        if (!line.startsWith("#")) {
            // 非 # 开头：http 绝对地址不解析，其他（相对地址）解析
            return !line.startsWith("http");
        }
        // # 开头：只有 #EXT-X-KEY 需要解析其 URI
        return line.startsWith(TAG_KEY);
    }

    /** URL 拼接（8.83 用 ExoPlayer UriUtil.resolve；此处用 java.net.URI 等价实现）。 */
    private static String resolveUrl(String base, String relative) {
        if (TextUtils.isEmpty(relative)) return relative;
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative;
        try {
            return new URI(base).resolve(relative).toString();
        } catch (Throwable e) {
            // 兜底：简单拼接
            try {
                int q = base.indexOf('?');
                String cleanBase = q >= 0 ? base.substring(0, q) : base;
                int slash = cleanBase.lastIndexOf('/');
                String dir = slash >= 0 ? cleanBase.substring(0, slash + 1) : cleanBase + "/";
                return dir + relative;
            } catch (Throwable ignored) {
                return relative;
            }
        }
    }

    /** 拉取 URL 文本（8.83 HttpRequestUtil.getResponseString 的等价实现，走 HkHttp OkHttp）。 */
    private static String fetchContent(String url, Map<String, String> headers) {
        try {
            OkHttpClient client = HkHttp.client();
            Request.Builder rb = new Request.Builder().url(url);
            rb.header("User-Agent", "Mozilla/5.0");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        try {
                            rb.header(e.getKey(), e.getValue());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            Response resp = client.newCall(rb.build()).execute();
            try {
                if (resp == null || !resp.isSuccessful()) return "";
                ResponseBody body = resp.body();
                return body != null ? body.string() : "";
            } finally {
                if (resp != null) resp.close();
            }
        } catch (Throwable e) {
            Log.d(TAG, "fetchContent failed: " + e.getMessage());
            return "";
        }
    }
}

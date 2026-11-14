package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.event.video.PlayChapterEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.iki.elonen.NanoHTTPD;

/**
 * 局域网服务控制器（8.83 ui/webdlan/DlanWebController 24 路径的星辰转写，
 * /proxy 本体在 HkProxyServer 不动，其余 23 条在此）。
 * 官账：worknotes/hiker-gapfix-stage8-receipt-20261110.md G44 节（逐端点出处）。
 * 传输差异：官为 AndServer，本树为 NanoHTTPD——响应体语义照官，错误体形态从简。
 */
public final class HkDlanController {

    private static final Gson GSON = new Gson();
    private static final String MIME_M3U8 = "application/vnd.apple.mpegurl";

    /** /homeRules 的收藏/历史/进度三子项导出钩（存储在 mobile 层，缺省不产出）。 */
    public interface SyncExportProvider {
        String exportCollections(boolean simple);

        String exportHistories(boolean simple);

        String exportPlayPos();
    }

    public static volatile SyncExportProvider syncExportProvider;

    private HkDlanController() {
    }

    // ── 响应值对象（NanoHTTPD.Response 构造为 protected，由 HkProxyServer 转建） ──

    public static final class Resp {
        public final NanoHTTPD.Response.Status status;
        public final String mime;
        public final byte[] body;
        public final Map<String, String> headers = new LinkedHashMap<>();
        /** >0 时 HkProxyServer 以此原始状态码建 Response（绕开 NanoHTTPD 枚举缺 302）。 */
        public int rawStatus = -1;

        public Resp(NanoHTTPD.Response.Status status, String mime, byte[] body) {
            this.status = status;
            this.mime = mime;
            this.body = body == null ? new byte[0] : body;
        }
    }

    private static Resp text(String body) {
        return text(NanoHTTPD.Response.Status.OK, "text/plain; charset=utf-8", body == null ? "" : body);
    }

    private static Resp text(NanoHTTPD.Response.Status status, String mime, String body) {
        return new Resp(status, mime, (body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
    }

    private static Resp redirect(String location, String body) {
        // 官 AndServer sendRedirect＝302；NanoHTTPD 的 REDIRECT 常量是 301（永久缓存、
        // 每集都变的 /redirectPlayUrl 目标址会被旧址缓存坑死，复核中项），以 rawStatus 出 302。
        Resp resp = text(NanoHTTPD.Response.Status.REDIRECT, "text/plain; charset=utf-8", body);
        resp.rawStatus = 302;
        resp.headers.put("Location", location == null ? "" : location);
        return resp;
    }

    private static Resp paramMissing(String name) {
        return text(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain; charset=utf-8",
                "ParamMissing: " + name);
    }

    private static Resp methodNotAllowed() {
        Resp resp = text(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain; charset=utf-8", "");
        return resp;
    }

    private static Resp asset(String assetPath) {
        try (InputStream in = App.get().getAssets().open(assetPath)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new Resp(NanoHTTPD.Response.Status.OK, mimeOf(assetPath), out.toByteArray());
        } catch (Throwable t) {
            return text(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "");
        }
    }

    private static String mimeOf(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    // ── 入口分发 ──────────────────────────────────────────────────────────

    /** @return 已承接的响应；null＝不是本控制器的路径（由 HkProxyServer 回 404）。 */
    public static Resp handle(NanoHTTPD.IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null) return null;
        String path = uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        try {
            switch (path) {
                case "/":
                    // 官 home() → forward:/player.html（assets/dplayer/player.html）。
                    return asset("dplayer/player.html");
                case "/player.html":
                case "/DPlayer.min.js":
                case "/hls.min.js":
                case "/jquery.min.js":
                case "/boot.css":
                    return asset("dplayer" + path);
                case "/ruleEdit":
                    // 官 ruleEdit() → forward:/index.html（assets/webs/index.html）。
                    return asset("webs/index.html");
                case "/test":
                    // 官 test()：同步模式缺省 NO_SYNC → ""；咱家无同步模式设置面，恒此形。
                    return text("");
                case "/playUrl":
                    return getUrl(boolParam(session, "enhance"));
                case "/getPlayList":
                    return getPlayList(boolParam(session, "enhance"));
                case "/playMe":
                    return playMe(session);
                case "/playNext":
                    return text(playChapter(-1, null));
                case "/redirectPlayUrl":
                    return redirect(realUrlForRemotedPlay(HkDlanState.getPlayUrl()),
                            "redirect:" + realUrlForRemotedPlay(HkDlanState.getPlayUrl()));
                case "/proxyM3u8":
                    return proxyM3u8();
                case "/clearM3u8":
                    return clearM3u8(session);
                case "/file":
                    return getFile(session);
                case "/proxyFile":
                    return proxyFile(session);
                case "/checkRuleExist":
                    return checkRuleExist(session);
                case "/getAllRuleTitles":
                    return text(allRuleTitles());
                case "/getRuleContent":
                    // 官 Adapter 明示方法约束两处：getRuleContent=GET、saveJs=POST（复核 L9）。
                    if (session.getMethod() != NanoHTTPD.Method.GET) return methodNotAllowed();
                    return getRuleContent(session);
                case "/getColTypes":
                    return text(GSON.toJson(HkJsRuntime.hkColTypeCodes()));
                case "/getAllJsTitles":
                    return text(allJsTitles(boolParam(session, "fromSync")));
                case "/getJsContent":
                    return getJsContent(session);
                case "/homeRules":
                    return text(homeRules(boolParam(session, "simple")));
                case "/saveJs":
                    if (session.getMethod() != NanoHTTPD.Method.POST) return methodNotAllowed();
                    return text(saveJs(readBody(session)));
                case "/saveRule":
                    return text(saveRule(readBody(session)));
                case "/proxyDownload":
                case "/proxyM3u8Download":
                    // 官无此下载记录时回 "error"；咱家无下载记录面，恒此形（记名未落本体）。
                    return text("error");
                default:
                    if (path.startsWith("/static/") || path.equals("/asset-manifest.json")
                            || path.equals("/manifest.json") || path.equals("/favicon.ico")
                            || path.equals("/service-worker.js") || path.equals("/robots.txt")
                            || path.startsWith("/precache-manifest")) {
                        return asset("webs" + path);
                    }
                    return null;
            }
        } catch (Throwable t) {
            return text(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "");
        }
    }

    private static String param(NanoHTTPD.IHTTPSession session, String name) {
        try {
            List<String> values = session.getParameters().get(name);
            if (values == null || values.isEmpty()) return null;
            return values.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean boolParam(NanoHTTPD.IHTTPSession session, String name) {
        return Boolean.parseBoolean(param(session, name));
    }

    private static String readBody(NanoHTTPD.IHTTPSession session) {
        try {
            String len = session.getHeaders().get("content-length");
            int total = len == null ? -1 : Integer.parseInt(len.trim());
            if (total > 16 * 1024 * 1024) return "";
            InputStream in = session.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(total > 0 ? total : 8192);
            byte[] buf = new byte[8192];
            if (total >= 0) {
                int read = 0;
                while (read < total) {
                    int n = in.read(buf, 0, Math.min(buf.length, total - read));
                    if (n < 0) break;
                    out.write(buf, 0, n);
                    read += n;
                }
            } else {
                // 无 Content-Length（chunked）：NanoHTTPD 的输入流已解 chunk、末块后回 -1，
                // 读到流尾，16MB 封顶（官 AndServer RequestBody.string() 无长度前提，复核 L10）。
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 16 * 1024 * 1024) return "";
                }
            }
            return out.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    // ── 投屏四接口 ────────────────────────────────────────────────────────

    /**
     * LocalServerParser.getRealUrlForRemotedPlay（smali :142-147＋getRealUrl 亲读）：
     * 先经 getRealLocalUrl（video:// 剥前缀；file:// 走 WebServerManager 文件服务——
     * 咱家无 11111 文件服务面，此支恒等、记名），仅当结果以 "http://127.0.0.1"
     * 开头才把主机换成局域网 IP（前缀锚定，非全串替换；localhost 官无此支，复核 L7）。
     */
    static String realUrlForRemotedPlay(String url) {
        if (TextUtils.isEmpty(url)) return url == null ? "" : url;
        String out = url;
        // 官 .line 144-147：video:// 支剥前缀后立即 return、不进 getRealUrl 的
        // 127 换 IP（复核 L7 二次订正）。
        if (out.startsWith("video://")) return out.substring("video://".length());
        if (out.startsWith("http://127.0.0.1")) {
            String lan = HkProxyServer.lanIp();
            if (!TextUtils.isEmpty(lan)) {
                out = "http://" + lan + out.substring("http://127.0.0.1".length());
            }
        }
        return out;
    }

    private static Resp getUrl(boolean enhance) {
        if (!enhance) {
            return text(realUrlForRemotedPlay(HkDlanState.getPlayUrl()));
        }
        HkDlanState.Dto dto = HkDlanState.getDto();
        HkDlanState.Dto out = dto == null ? new HkDlanState.Dto() : dto.copy();
        String serverUrl = HkProxyServer.instance().getLanServerUrl();
        if (!TextUtils.isEmpty(out.danmu) && !out.danmu.startsWith("hiker") && !out.danmu.startsWith("file")) {
            out.danmu = serverUrl + "/file?name=danmu." + extensionOf(out.danmu);
        }
        if (!TextUtils.isEmpty(out.subtitle) && !out.subtitle.startsWith("hiker") && !out.subtitle.startsWith("file")) {
            out.subtitle = serverUrl + "/file?name=subtitle." + extensionOf(out.subtitle);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", out.url);
        map.put("title", out.title);
        if (out.headers != null) map.put("headers", out.headers);
        map.put("danmu", out.danmu);
        map.put("subtitle", out.subtitle);
        map.put("audioUrl", out.audioUrl);
        map.put("jumpStartDuration", out.jumpStartDuration);
        map.put("jumpEndDuration", out.jumpEndDuration);
        return text(GSON.toJson(map));
    }

    /** FileUtil.getExtension 口径：先剥 "?" 与 "##" 段再取末段扩展名（复核 L11）。 */
    private static String extensionOf(String path) {
        if (path == null) return "";
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf("##");
        if (h >= 0) p = p.substring(0, h);
        int dot = p.lastIndexOf('.');
        int slash = p.lastIndexOf('/');
        return dot > slash && dot >= 0 ? p.substring(dot + 1) : "";
    }

    private static Resp getPlayList(boolean enhance) {
        // 官 VideoPlayerActivity.getChapters 的 "-" 分段映射（HkDlanState.buildChapterMap）：
        // 列表只出过滤后的末段标题，enhance 形为 末段@_@原集 url。
        List<String[]> chapters = HkDlanState.getChapters();
        List<String> rawTitles = new ArrayList<>();
        for (String[] pair : chapters) {
            rawTitles.add(pair == null || pair.length == 0 ? null : pair[0]);
        }
        HkDlanState.ChapterMap map = HkDlanState.buildChapterMap(rawTitles);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < map.titles.size(); i++) {
            String[] pair = chapters.get(map.origIndices.get(i));
            String url = pair != null && pair.length > 1 && pair[1] != null ? pair[1] : "";
            out.add(enhance ? map.titles.get(i) + "@_@" + url : map.titles.get(i));
        }
        return text(GSON.toJson(out));
    }

    private static Resp playMe(NanoHTTPD.IHTTPSession session) {
        String title = param(session, "title");
        if (TextUtils.isEmpty(title)) return paramMissing("title");
        String index = param(session, "index");
        if (TextUtils.isEmpty(index)) return paramMissing("index");
        try {
            return text(playChapter(Integer.parseInt(index), title));
        } catch (NumberFormatException e) {
            return text("false");
        }
    }

    private static String playChapter(int position, String title) {
        PlayChapterEvent event = new PlayChapterEvent(position, title);
        if (!EventBus.getDefault().hasSubscriberForEvent(PlayChapterEvent.class)) return "false";
        EventBus.getDefault().post(event);
        return "true";
    }

    // ── m3u8 代理两条 ─────────────────────────────────────────────────────

    private static Resp proxyM3u8() {
        HkDlanState.Dto dto = HkDlanState.getDto();
        if (dto == null || TextUtils.isEmpty(dto.url)) {
            // 官在此处解空 DTO 自崩（不复刻），回空体。
            return text("");
        }
        String result = proxyM3u8(dto.url, dto.headers);
        if (!TextUtils.isEmpty(result) && !result.startsWith("http")) {
            return text(NanoHTTPD.Response.Status.OK, MIME_M3U8, result);
        }
        String target = dto.url + "#noProxy";
        return redirect(target, target);
    }

    /**
     * M3u8Utils.proxyM3u8 本体（smali 1034-1252 二次亲读订正，复核 M4）：
     * ① isNotM3u8 命中（url 含 "#isM3u8#" 强制标可豁免）即回原 url、不取流；
     * ② 取流得 {body, realUrl}（官为 JE.fetchWithHeadersInterceptor 的 JSON
     * 信封 {url, body} 且带 HeadersInterceptor 读体门——咱家 HkHttp 直取同构：
     * body＝过门后读得的响应体、realUrl＝重定向链末址、DTO 头透传、栈层默认
     * UA/Accept-Language 先塞后由 DTO 头覆盖；读体门见 shouldReadPlaylistBody）；③ body 空回原 url；realUrl 非
     * http 开头先 joinUrl(原 url, realUrl)、仍非 http 回原 url——realUrl 只作
     * 此门、从不作改写基；④ body 不含 "#EXT" 回原 url；⑤ fixPath(body, 原
     * url, 递归本法) 的结果含 "#EXT" 才用、余回原 url。
     * 回原 url（http 开头）即控制器 sendRedirect(dto.url + "#noProxy") 的信号，
     * 与官 DlanWebController.proxyM3u8 .line 200-201 同形。
     */
    private static String proxyM3u8(String url, Map<String, String> headers) {
        boolean forceM3u8 = url != null && url.contains("#isM3u8#");
        if (!forceM3u8 && isNotM3u8Url(url)) return url;
        String body = "";
        String realUrl = url;
        try {
            okhttp3.OkHttpClient client = HkHttp.client();
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(url);
            // 栈层默认头（官 OkGo Request 构造器在 options 头之下先塞
            // User-Agent＝MOBILE 串[与 HkHttp.DEFAULT_UA 逐字同]＋Accept-Language，
            // DTO 头随后覆盖——四次复核订正：UA 在栈层不在 options 层，此前
            // 摘显式 UA 摘过头）。Accept-Language 照官 HttpHeaders.getAcceptLanguage
            // 公式以 Locale 生成：<语言>-<国家>,<语言>;q=0.8（五次复核订正，
            // 此前写死 q=0.9 串与官不符；公式已逐字抠实：国家为空时后缀整块
            // 跳过、只回语言单段，咱家同构；生成值为空时官调用方有 isEmpty
            // 守卫不下发该头，咱家同守）。
            java.util.Locale locale = java.util.Locale.getDefault();
            String lang = locale.getLanguage();
            String country = locale.getCountry();
            rb.header("User-Agent", HkHttp.defaultUa());
            String acceptLanguage = TextUtils.isEmpty(country)
                    ? lang : lang + "-" + country + "," + lang + ";q=0.8";
            if (!TextUtils.isEmpty(acceptLanguage)) {
                rb.header("Accept-Language", acceptLanguage);
            }
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        try {
                            rb.header(e.getKey(), e.getValue());
                        } catch (Throwable ignored) {
                            // DTO 头名非法时跳过该头（官 Map 头经其取流栈直传，
                            // 此吞支为咱家微差，记名于回执）。
                        }
                    }
                }
            }
            try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
                realUrl = resp.request().url().toString();
                // 读体门（官 HeadersInterceptor：M3u8Utils$proxyM3u8$lambda$1
                // ＋HttpHelper$1 的 intercept 极性亲读）：强制标直读；余按响应
                // 类型推断——可识别媒体类型或恰 "text/html" 不读体（body 空→
                // 回原 url 走重定向），未知（-1）/防盗链假图（-100）才读体。
                // 无状态码门（官 CharsetStringConvert 不看码）；Content-Length
                // 超 0x1400000 官抛错断链，咱家同归 body 空（回原 url）。
                boolean readBody = forceM3u8 || shouldReadPlaylistBody(url, resp);
                if (readBody && resp.body() != null
                        && resp.body().contentLength() <= 0x1400000L) {
                    body = resp.body().string();
                }
            }
        } catch (Throwable t) {
            body = "";
        }
        if (TextUtils.isEmpty(body)) return url;
        if (realUrl == null || !realUrl.startsWith("http")) {
            realUrl = joinUrl(url, realUrl == null ? "" : realUrl);
        }
        if (realUrl == null || !realUrl.startsWith("http")) return url;
        if (!body.contains("#EXT")) return url;
        String fixed = fixPath(body, url, headers);
        if (fixed != null && fixed.contains("#EXT")) return fixed;
        return url;
    }

    /**
     * M3u8Utils.fixPath（smali 367-）：按 "\n" 切行（尾空行保留、\r 不剥，与官
     * Kotlin split 同形）；#EXT-X-KEY:／#EXT-X-MAP: 行只改 URI="…"（值已 http
     * 开头不动，余解析后替换）；以 # 或 http 开头的行原样；其余行以
     * java.net.URL(URL(基), 行) 解析——首个被解析行若以 .m3u8 结尾且解析结果
     * 不等于基址，整函数即回递归 proxyM3u8 对该址的结果；解析过的行此后不再
     * 触发拦截。空行解析为基址本身（URL 语义，与官同）。行以 "\n" 拼回。
     */
    private static String fixPath(String content, String base, Map<String, String> headers) {
        String[] lines = content.split("\n", -1);
        List<String> out = new ArrayList<>();
        boolean intercepted = false;
        for (String line : lines) {
            if (line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-MAP:")) {
                out.add(rewriteUriAttr(base, line));
                continue;
            }
            if (line.startsWith("#") || line.startsWith("http")) {
                out.add(line);
                continue;
            }
            String resolved = joinUrl(base, line);
            if (!intercepted && line.endsWith(".m3u8") && !resolved.equals(base)) {
                return proxyM3u8(resolved, headers);
            }
            out.add(resolved);
            intercepted = true;
        }
        return String.join("\n", out);
    }

    /** 读体判（官 interceptor 极性）：infer ∈ {-100, -1} 才读体。 */
    private static boolean shouldReadPlaylistBody(String url, okhttp3.Response resp) {
        int infer = inferFileTypeFromResponse(url, resp);
        return infer == -100 || infer == -1;
    }

    /**
     * HttpUtil.inferFileTypeFromResponse 转写（smali 270-402 亲读）：headers
     * 为 null（咱家 OkHttp 恒有 headers 对象，此支不可达）回 -1；Content-Type
     * 按精确键 "Content-Type"、取不到回退精确键 "content-type" 取首值；判
     * isFuckImage→-100、恰 "text/html"→-101；含 ";" 时把首值替换为 ";" 前段
     * 后交 FileTypes 推断（官 ExoPlayer 只认精确 "Content-Type" 键——map 无
     * 此键直接 -1；推断本体按官表自落 exoInferFileType，media3 表不等值不用）。
     */
    private static int inferFileTypeFromResponse(String url, okhttp3.Response resp) {
        Map<String, java.util.List<String>> map = new LinkedHashMap<>();
        for (String name : resp.headers().names()) {
            map.put(name, resp.headers(name));
        }
        java.util.List<String> ctValues = map.get("Content-Type");
        if (ctValues == null) ctValues = map.get("content-type");
        String ct = (ctValues == null || ctValues.isEmpty()) ? null : ctValues.get(0);
        if (isFuckImage(url, ct)) return -100;
        if ("text/html".equals(ct)) return -101;
        if (!map.containsKey("Content-Type")) return -1;
        if (ct != null && ct.contains(";")) {
            java.util.List<String> replaced = new java.util.ArrayList<>(map.get("Content-Type"));
            replaced.add(ct.split(";")[0]);
            replaced.remove(0);
            map.put("Content-Type", replaced);
        }
        return exoInferFileType(map.get("Content-Type"));
    }

    /** 官 Exo FileTypes 的 mime 表（FileTypes.smali 逐串抄，24 项）。 */
    private static final java.util.Set<String> EXO_FILE_TYPE_MIMES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "application/mp4", "application/webm", "audio/3gpp", "audio/ac3",
                    "audio/ac4", "audio/amr", "audio/amr-wb", "audio/eac3", "audio/eac3-joc",
                    "audio/flac", "audio/mp4", "audio/mpeg", "audio/ogg", "audio/wav",
                    "audio/webm", "audio/x-matroska", "image/jpeg", "text/vtt", "video/mp2p",
                    "video/mp2t", "video/mp4", "video/webm", "video/x-flv", "video/x-matroska"));

    /**
     * Exo FileTypes.inferFileTypeFromResponseHeaders 的等价（五次复核订正：
     * media3 的表多认 image/png、video/avi 等、会翻转读体门，故按官表自落）：
     * 取 Content-Type 首值，normalize 后查表。官 normalizeMimeType 全程精确
     * 匹配——无 trim、无小写化，只有三别名（audio/mp3→audio/mpeg、audio/x-wav
     * →audio/wav、audio/x-flac→audio/flac，逐条亲读；六次复核订正，此前误加
     * trim+小写会使大写 Content-Type 翻转读体门）。本面只消费「可识别/未知」
     * 之分：可识别回 0 占位、未知回 -1（类型常量具体值无消费方读）。
     */
    private static int exoInferFileType(java.util.List<String> ctValues) {
        if (ctValues == null || ctValues.isEmpty()) return -1;
        String mime = ctValues.get(0);
        if (mime == null) return -1;
        switch (mime) {
            case "audio/mp3":
                mime = "audio/mpeg";
                break;
            case "audio/x-wav":
                mime = "audio/wav";
                break;
            case "audio/x-flac":
                mime = "audio/flac";
                break;
            default:
                break;
        }
        return EXO_FILE_TYPE_MIMES.contains(mime) ? 0 : -1;
    }

    /**
     * HttpUtil.isFuckImage 转写（smali 404-464 跳转逐条亲读，四次复核订正——
     * 初版两支判据写反）：ct 为 null 回假；支一：ct 含 "text/html" 且 url
     * **含** ".m3u8" 为真（HTML 冒充列表）；支二：url **不是**图片且 ct 含
     * "image/" 为真（图片冒充媒体）。其后 "image/jpeg" 恰等支在官码不可达。
     */
    private static boolean isFuckImage(String url, String ct) {
        if (ct == null) return false;
        if (ct.contains("text/html") && url != null && url.contains(".m3u8")) return true;
        return !isImageUrl(url) && ct.contains("image/");
    }

    private static final String[] IMAGE_TOKENS = {".ico", ".png", ".PNG", ".jpg", ".JPG",
            ".jpeg", ".JPEG", ".gif", ".GIF", ".webp", ".svg", ".avif"};

    /**
     * UrlDetector.isImage 转写（smali 1885-2060 亲读）：空/x5Play:// 回假；
     * 含 @rule=/@lazyRule=/isVideo=true 任一标记回假；removeDom 后含
     * ignoreImg=true 回假；含 IMAGE_TOKENS 任一标记回真。两支未逐字搬、
     * 记名：ImageUrlMapEnum 命中回真（其表全为 hiker://images/* 内置图标，
     * 本调用面是 http 媒体址、碰不到）；apps 包名后缀结尾回假（媒体址以
     * 包名结尾不现于本面）。
     */
    private static boolean isImageUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (url.startsWith("x5Play://")) return false;
        if (url.contains("@rule=") || url.contains("@lazyRule=")) return false;
        if (url.contains("isVideo=true")) return false;
        String rest = removeDom(url);
        if (rest != null && rest.contains("ignoreImg=true")) return false;
        if (rest != null) {
            for (String token : IMAGE_TOKENS) {
                if (rest.contains(token)) return true;
            }
        }
        return false;
    }

    /** StringUtil.removeDom 转写：剥 http(s):// 前缀后去掉首个 "/" 前的域名段。 */
    private static String removeDom(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            String s = url.replaceFirst("http://", "").replaceFirst("https://", "");
            String[] parts = s.split("/");
            if (parts.length > 1) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) sb.append('/');
                    sb.append(parts[i]);
                }
                return sb.toString();
            }
            return s;
        } catch (Throwable t) {
            return url;
        }
    }

    /** M3u8Utils.isNotM3u8 的 URL 支（与 HkJsRuntime 内同名转写同口径）。 */
    private static boolean isNotM3u8Url(String url) {
        if (url == null) return false;
        if (url.contains(".flv") || url.contains(".m4a") || url.contains(".mp3")
                || url.contains("mine_type=video_mp4")) return true;
        return url.contains("mp4") && !url.contains("m3u8");
    }

    /** fixPath 的 KEY/MAP 支（官 URI="(.*?)" 正则＋值 trim）：值已 http(s)
     * 开头整行不动；余以基址解析后替换值段；无 URI 属性整行原样。 */
    private static String rewriteUriAttr(String base, String line) {
        int start = line.indexOf("URI=\"");
        if (start < 0) return line;
        int from = start + 5;
        int end = line.indexOf('"', from);
        if (end < 0) return line;
        String value = line.substring(from, end).trim();
        if (value.startsWith("http://") || value.startsWith("https://")) return line;
        return line.substring(0, from) + joinUrl(base, value) + line.substring(end);
    }

    /** CommonParser.joinUrl 照抄（smali 1571-1611 亲读）：基址空回 ref；
     * 余 new URL(URL(基), ref).toExternalForm()，MalformedURLException 回基址。 */
    private static String joinUrl(String base, String ref) {
        if (TextUtils.isEmpty(base)) return ref;
        try {
            return new java.net.URL(new java.net.URL(base), ref).toExternalForm();
        } catch (java.net.MalformedURLException e) {
            return base;
        }
    }

    private static Resp clearM3u8(NanoHTTPD.IHTTPSession session) {
        String url = param(session, "url");
        String headersText = param(session, "headers");
        Map<String, String> headers = new HashMap<>();
        if (!TextUtils.isEmpty(headersText) && !"null".equals(headersText)) {
            try {
                Map<String, Object> parsed = GSON.fromJson(headersText,
                        new TypeToken<Map<String, Object>>() {
                        }.getType());
                if (parsed != null) {
                    for (Map.Entry<String, Object> e : parsed.entrySet()) {
                        if (e.getKey() != null && e.getValue() != null) {
                            headers.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String content = HkM3u8ClearUtil.getProxyContent(url == null ? "" : url, headers);
        if (!TextUtils.isEmpty(content) && content.trim().startsWith("#EXTM3U")) {
            return text(NanoHTTPD.Response.Status.OK, MIME_M3U8, content);
        }
        return redirect(url == null ? "" : url, url == null ? "" : url);
    }

    // ── 文件两条 ──────────────────────────────────────────────────────────

    private static Resp getFile(NanoHTTPD.IHTTPSession session) {
        String name = param(session, "name");
        if (TextUtils.isEmpty(name)) return paramMissing("name");
        HkDlanState.Dto dto = HkDlanState.getDto();
        String path = null;
        if (name.startsWith("danmu")) path = dto == null ? null : dto.danmu;
        else if (name.startsWith("subtitle")) path = dto == null ? null : dto.subtitle;
        if (TextUtils.isEmpty(path)) return text("");
        try {
            File f = new File(HkJsRuntime.getFilePath(path));
            if (!f.exists()) return text("");
            return text(readFileLines(f));
        } catch (Throwable t) {
            return text("");
        }
    }

    /** FileUtil.fileToString：逐行以 "\n" 拼、无尾随换行。 */
    private static String readFileLines(File f) throws Exception {
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        return String.join("\n", lines);
    }

    private static Resp proxyFile(NanoHTTPD.IHTTPSession session) {
        String pathParam = param(session, "path");
        if (TextUtils.isEmpty(pathParam)) return paramMissing("path");
        String decoded;
        try {
            decoded = new String(Base64.decode(pathParam, Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // 官此处异常上抛（AndServer 500）；咱家以 500 空体对齐状态码（复核 L12）。
            return text(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "");
        }
        File f = new File(decoded);
        if (!f.exists() || !f.isFile()) return text("");
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            return new Resp(NanoHTTPD.Response.Status.OK, mimeOf(decoded), bytes);
        } catch (Throwable t) {
            return text("");
        }
    }

    // ── 规则/插件管理面 ───────────────────────────────────────────────────

    private static Resp checkRuleExist(NanoHTTPD.IHTTPSession session) {
        String title = param(session, "title");
        if (TextUtils.isEmpty(title)) return paramMissing("title");
        boolean exist;
        try {
            exist = HkRuleManager.get().getInstalledRule(title) != null;
        } catch (Throwable t) {
            exist = false;
        }
        return text(exist ? "true" : "false");
    }

    private static String allRuleTitles() {
        List<String> titles = new ArrayList<>();
        // 不在此吞异常：取规则表失败必须与「真没有规则」区分开——异常上抛到
        // handle() 顶层 catch 回 500，Web 端能感知失败，不再冒充空名单。
        for (HkRule rule : HkRuleManager.get().getRules()) {
            if (rule != null && rule.getTitle() != null) titles.add(rule.getTitle());
        }
        return GSON.toJson(titles);
    }

    private static Resp getRuleContent(NanoHTTPD.IHTTPSession session) {
        String title = param(session, "title");
        if (TextUtils.isEmpty(title)) return paramMissing("title");
        try {
            String json = HkRuleManager.get().loadRuleJson(title);
            return text(json == null ? "" : json);
        } catch (Throwable t) {
            return text("");
        }
    }

    private static File jsDir() {
        return new File(HkJsRuntime.getFilePath("hiker://files/rules/js/"));
    }

    private static String allJsTitles(boolean fromSync) {
        // 官：fromSync 且 syncList[4]（网页插件同步）关 → "[]"；咱家无该设置面，不关（记名）。
        List<String> names = new ArrayList<>();
        // 同 allRuleTitles：列目录失败上抛到 handle() 顶层回 500，不冒充空名单。
        File[] files = jsDir().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile()) names.add(f.getName());
            }
        }
        return GSON.toJson(names);
    }

    private static Resp getJsContent(NanoHTTPD.IHTTPSession session) {
        String name = param(session, "name");
        if (TextUtils.isEmpty(name)) return paramMissing("name");
        try {
            File f = new File(jsDir(), new File(name).getName());
            if (f.exists() && f.isFile()) return text(readFileLines(f));
            // JSManager.getJsByFileName 的资产回落支。
            try (InputStream in = App.get().getAssets().open(new File(name).getName())) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return text(out.toString("UTF-8"));
            } catch (Throwable ignored) {
                return text("");
            }
        } catch (Throwable t) {
            return text("");
        }
    }

    private static String saveJs(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body == null ? "" : body).getAsJsonObject();
            // 面②#8：name/content 改走 strOf 宽容读（旧形 getAsString 硬取，
            // 值为对象/数组时整单保存失败；官走 fastjson 进 bean 收敛）。
            String name = strOf(obj, "name");
            String content = strOf(obj, "content");
            if (TextUtils.isEmpty(name)) {
                return "{\"isSuccess\":false, \"errorMsg\":\"解析数据失败，插件名称不能为空\"}";
            }
            File dir = jsDir();
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, new File(name).getName());
            Files.write(f.toPath(), (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return "{\"isSuccess\":true}";
        } catch (Exception e) {
            return "{\"isSuccess\":false, \"errorMsg\":\"" + (e.getMessage() == null ? "" : e.getMessage()) + "\"}";
        }
    }

    // ── 同步导出 ──────────────────────────────────────────────────────────

    private static final Set<String> SHARE_EXCLUDES = new HashSet<>(Arrays.asList(
            "associatedModelsMapForJoinTable", "associatedModelsMapWithFK", "associatedModelsMapWithoutFK",
            "fieldsToSetToDefault", "listToClearAssociatedFK", "listToClearSelfFK", "saved",
            "order", "id", "gmtCreate", "lastUseTime", "displayName", "pageList"));
    private static final Set<String> SHARE_SIMPLE_INCLUDES = new HashSet<>(Arrays.asList(
            "title", "time", "mTitle", "version", "gmtModified"));

    private static String homeRules(boolean simple) {
        Map<String, Object> dto = new LinkedHashMap<>();
        try {
            JsonArray arr = new JsonArray();
            for (HkRule rule : HkRuleManager.get().getRules()) {
                if (rule == null) continue;
                String raw = HkRuleManager.get().loadRuleJson(rule.getTitle());
                if (raw == null) continue;
                try {
                    JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                    JsonObject filtered = new JsonObject();
                    for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                        if (simple && !SHARE_SIMPLE_INCLUDES.contains(e.getKey())) continue;
                        if (!simple && SHARE_EXCLUDES.contains(e.getKey())) continue;
                        filtered.add(e.getKey(), e.getValue());
                    }
                    arr.add(filtered);
                } catch (Throwable t) {
                    // 单条规则 JSON 损坏只跳过该条、其余照常导出（分条隔离不变），
                    // 但被跳过的规则必须按名留痕，不许静默从导出里消失。
                    android.util.Log.e("HkDlanController",
                            "homeRules skip unparsable rule title=" + rule.getTitle(), t);
                }
            }
            dto.put("homeRules", GSON.toJson(arr));
        } catch (Throwable ignored) {
        }
        SyncExportProvider provider = syncExportProvider;
        if (provider != null) {
            try {
                String collections = provider.exportCollections(simple);
                if (collections != null) dto.put("collections", collections);
            } catch (Throwable ignored) {
            }
            try {
                String histories = provider.exportHistories(simple);
                if (histories != null) dto.put("histories", histories);
            } catch (Throwable ignored) {
            }
            try {
                String playPos = provider.exportPlayPos();
                if (playPos != null) dto.put("playPos", playPos);
            } catch (Throwable ignored) {
            }
        }
        return GSON.toJson(dto);
    }

    // ── saveRule ──────────────────────────────────────────────────────────

    private static final String[] SAVE_RULE_COPY_FIELDS = {
            "url", "type", "author", "version", "col_type", "class_name", "class_url",
            "year_name", "year_url", "area_name", "area_url", "sort_name", "sort_url",
            "find_rule", "search_url", "searchFind", "group", "detail_col_type",
            "detail_find_rule", "sdetail_col_type", "sdetail_find_rule", "ua", "preRule",
            "last_chapter_rule", "proxy", "icon"};

    private static String saveRule(String body) {
        try {
            JsonObject jo = JsonParser.parseString(body == null ? "" : body).getAsJsonObject();
            // 面②#8：title/ua 同 saveJs 改走 strOf 宽容读（理由同上）。
            String title = strOf(jo, "title");
            if (TextUtils.isEmpty(title)) {
                return "{\"isSuccess\":false, \"errorMsg\":\"解析数据失败，标题不能为空\"}";
            }
            // ua 空 → MOBILE 码（官 saveRule:618-620）。
            if (!jo.has("ua") || jo.get("ua").isJsonNull() || TextUtils.isEmpty(strOf(jo, "ua"))) {
                jo.addProperty("ua", HkUaEnum.MOBILE.getCode());
            }
            String[][] groups = {
                    {"area_name", "area_url", "地区名称和地区替换词长度不一致"},
                    {"year_name", "year_url", "年份名称和年份替换词长度不一致"},
                    {"class_name", "class_url", "分类名称和分类替换词长度不一致"},
                    {"sort_name", "sort_url", "排序名称和排序替换词长度不一致"}};
            for (String[] g : groups) {
                String name = strOf(jo, g[0]);
                String url = strOf(jo, g[1]);
                if (url == null || name == null) continue;
                // 官 FilterUtil.hasFilterWord(String) 是硬编码恒 false 的 stub（smali 亲验），
                // 「含有违禁词」在 8.83 永不触发——咱家此前接了真词表会误拒，整支摘除（复核 M5）。
                if (url.split("&").length != name.split("&").length) {
                    return "{\"isSuccess\":false, \"errorMsg\":\"" + g[2] + "\"}";
                }
            }
            HkRuleManager manager = HkRuleManager.get();
            HkRule existing = manager.getInstalledRule(title);
            if (existing != null) {
                String newMain = !TextUtils.isEmpty(strOf(jo, "url")) ? strOf(jo, "url") : strOf(jo, "search_url");
                String oldMain = !TextUtils.isEmpty(existing.getUrl()) ? existing.getUrl() : existing.getSearchUrl();
                String newDom = domainOf(newMain);
                String oldDom = domainOf(oldMain);
                // 官 saveRule .line 662-665：双非 null 门——任一 dom 为 null 直接
                // 跳过比较（放行更新），只有双非 null 且 StringUtils.equals 为假
                // 才拒（复核 L8 二次订正，此前记反）。domainOf 的去端口/小写化是
                // 首轮既有近似、非本批引入，记名保留。
                if (newDom != null && oldDom != null && !newDom.equals(oldDom)) {
                    return "{\"isSuccess\":false, \"errorMsg\":\"存在同名不同域名的规则，不能在Web端直接更新\"}";
                }
                // 固定字段集拷贝覆盖到存量原始 JSON（官逐字段 set 到存量记录的等价形）。
                String raw = manager.loadRuleJson(title);
                JsonObject merged = raw == null ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
                for (String field : SAVE_RULE_COPY_FIELDS) {
                    if (jo.has(field)) merged.add(field, jo.get(field));
                    else merged.remove(field);
                }
                if (jo.has("pages")) merged.add("pages", stripPagePrefix(jo.get("pages")));
                HkRule parsed = manager.parseJson(GSON.toJson(merged));
                manager.saveRule(parsed, GSON.toJson(merged));
            } else {
                HkRule parsed = manager.parseJson(body);
                manager.saveRule(parsed, body);
            }
            // 官尾步 post OnArticleListRuleChangedEvent；咱家规则页以 saveStamp 轮询感知（存盘已 bump）。
            return "{\"isSuccess\":true}";
        } catch (Exception e) {
            return "{\"isSuccess\":false, \"errorMsg\":\"" + (e.getMessage() == null ? "" : e.getMessage()) + "\"}";
        }
    }

    /**
     * 宽容读串（面②#8）：官 fastjson getString/parseString 口径——串原样、
     * 数字保词形、布尔转串、对象/数组回 JSON 文本；键缺失/JsonNull 回 null。
     * 旧形对复合值 getAsString 抛错被吞成 null（分组校验被跳过、path 读不到），
     * 与官收敛分叉，改经 HkRuleManager.fastJsonValueText 统一口径。本法全部
     * 消费点（saveRule 分组对/主址、saveJs、saveRule title/ua、stripPagePrefix
     * 的 path）都是官 getString 语境，一并对齐。
     */
    private static String strOf(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        return HkRuleManager.fastJsonValueText(obj.get(key));
    }

    /** 域名提取（官 HkStringUtil.getDom 的转写在 mobile 层、main 不可达，此处同口径本地形）。 */
    private static String domainOf(String url) {
        if (TextUtils.isEmpty(url)) return null;
        String s = url.trim();
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(0, colon);
        return s.isEmpty() ? null : s.toLowerCase();
    }

    /** pageList 逐项 path 以 StringUtils.replace 全局剥 "hiker://page/"（官 saveRule:695-699，复核 L8）。 */
    private static JsonElement stripPagePrefix(JsonElement pages) {
        if (pages == null || !pages.isJsonArray()) return pages;
        JsonArray out = new JsonArray();
        for (JsonElement el : pages.getAsJsonArray()) {
            if (el != null && el.isJsonObject()) {
                JsonObject page = el.getAsJsonObject();
                String path = strOf(page, "path");
                if (path != null && path.contains("hiker://page/")) {
                    page.addProperty("path", path.replace("hiker://page/", ""));
                }
            }
            out.add(el);
        }
        return out;
    }
}

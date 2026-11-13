package com.fongmi.android.tv.api.hk;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本地代理服务器（8.83 RemoteServerManager + DlanWebController /proxy 端点的移植）。
 * 8.83 原文：
 *   RemoteServerManager.smali（374 行）：单例，port=0xcb34(52020)，setProxyJS/startServer/
 *     getServerUrl/destroyServer/isRunning，基于 AndServer。
 *   DlanWebController.proxy (smali:1464)：/proxy 端点，取 proxyJS，用请求参数拼 MY_PARAMS，
 *     调 JSEngine.evalJSForObj 求值，按 ProxyResult{statusCode,headers,body} 回包。
 * 星辰实现：AndServer 换成项目已有的 NanoHTTPD；只实现 startProxyServer 需要的 /proxy 端点，
 *   DLNA/web 管理等其它端点属 App 壳子，不移植。
 */
public class HkProxyServer extends NanoHTTPD {

    public static final int PORT = 0xcb34;
    /** 端口占用时的递增上限（照 8.83 RemoteServerManager：52020 起、最大 52025）。 */
    public static final int PORT_MAX = 0xcb39;

    private static volatile HkProxyServer sInstance;

    private volatile String proxyJS;
    private volatile HkJsRuntime evaluator;
    /** 实际绑定成功的端口（可能因占用递增过）。 */
    private final int boundPort;

    private static final Gson GSON = new Gson();

    private HkProxyServer(int port) {
        super(port);
        this.boundPort = port;
    }

    public static HkProxyServer instance() {
        return instance(PORT);
    }

    /** 从指定端口起找/建实例：单例已存在且端口不小于要求时直接复用。 */
    public static synchronized HkProxyServer instance(int port) {
        if (sInstance == null || sInstance.boundPort < port) {
            HkProxyServer old = sInstance;
            sInstance = new HkProxyServer(port);
            if (old != null) migrateAndDestroy(old, sInstance);
        }
        return sInstance;
    }

    /**
     * 换端口起新实例时：先把旧实例的代理 JS 与求值器迁到新实例，再彻底销毁旧实例。
     * 调用方（HkJsRuntime.startProxyServer）在 startWithFallback 后会重新 set 一次，
     * 迁移是兜底——保证即使调用方只 set 一次、换实例后不再 set，/proxy 照样能求值。
     * 全局只允许一个存活实例，不留僵尸端口。
     */
    private static void migrateAndDestroy(HkProxyServer old, HkProxyServer now) {
        // 先迁后毁：此前先毁旧实例，destroy 抛错会连迁移一起吞掉、代理 JS 与
        // 求值器无痕丢失；现在迁移先落新实例，销毁失败单独走 hkproxy 诊断日志。
        String js = old.getProxyJS();
        HkJsRuntime rt = old.getEvaluator();
        if (now.getProxyJS() == null && js != null) now.setProxyJS(js);
        if (now.getEvaluator() == null && rt != null) now.setEvaluator(rt);
        try {
            old.destroyServer();
        } catch (Throwable t) {
            com.github.catvod.crawler.SpiderDebug.log("hkproxy", "migrate destroy old failed: %s: %s",
                    t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
        }
    }

    public void setProxyJS(String js) {
        this.proxyJS = js;
    }

    public String getProxyJS() {
        return proxyJS;
    }

    public void setEvaluator(HkJsRuntime runtime) {
        this.evaluator = runtime;
    }

    public HkJsRuntime getEvaluator() {
        return evaluator;
    }

    public boolean isRunning() {
        return super.isAlive();
    }

    public synchronized void startServer() throws Exception {
        if (!isRunning()) {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        }
    }

    /**
     * 照 8.83 RemoteServerManager 的端口回退：从当前端口起启动，
     * 遇绑定失败（多为端口被占用）就换下一个端口的新实例重试，最大到 PORT_MAX。
     * 成功时单例已切换到实际端口的实例；全失败抛最后一个异常。
     */
    public static HkProxyServer startWithFallback() throws Exception {
        Exception last = null;
        for (int port = PORT; port <= PORT_MAX; port++) {
            HkProxyServer server = instance(port);
            if (server.isRunning()) return server;
            try {
                server.startServer();
                return server;
            } catch (Exception e) {
                last = e;
                // 端口被占用：这个实例（从未启动成功）彻底销毁再试下一个端口，不留僵尸实例。
                // 下一轮 instance(port+1) 会建新实例；旧的 proxyJS/evaluator 经 destroyServer 清空。
                try {
                    server.destroyServer();
                } catch (Throwable ignored) {
                }
                synchronized (HkProxyServer.class) {
                    if (sInstance == server) sInstance = null;
                }
            }
        }
        if (last != null) throw last;
        throw new IllegalStateException("no port available");
    }

    public synchronized void destroyServer() {
        if (isRunning()) stop();
        proxyJS = null;
        evaluator = null;
    }

    /**
     * 环44 规则 JS 启动路（官 JSEngine.startProxyServer 的宿主口）：规则 JS 调
     * startProxyServer(js) 时经此真拉起本服务本体（含 /proxy 与投屏四接口等
     * 全部既有端点）——先在当前单例上落代理 JS 与求值器，已在跑则直接复用
     * （重复调不重起、端口不变），未跑则走端口递增回退启动并在实际实例上
     * 回填两件。全端口启动失败回 null（JS 侧按失败口径回空串）。
     * 停止路仍是 destroyServer（官 JS 面无停止名，本体口不新增）；
     * JS 没调时本服务不起，无常驻启动点、无开关。
     */
    public static HkProxyServer startFromJs(String js, HkJsRuntime evaluator) {
        HkProxyServer server = instance();
        server.setProxyJS(js);
        server.setEvaluator(evaluator);
        if (server.isRunning()) return server;
        try {
            server = startWithFallback();
        } catch (Throwable t) {
            return null;
        }
        server.setProxyJS(js);
        server.setEvaluator(evaluator);
        return server.isRunning() ? server : null;
    }

    public String getServerUrl() {
        // 诊断：127.0.0.1 是本机通信唯一永远可靠的地址。
        // 以前取第一个非回环 IPv4（getLocalIp），他走手机流量回连不可靠，代理连上无响应。
        // 最小修法：直接回环，不再调 getLocalIp。
        return "http://127.0.0.1:" + boundPort;
    }

    private static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getHostAddress() != null && a.getHostAddress().indexOf(':') < 0) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 局域网地址（DLNA/投屏客户端回连用，与 getServerUrl 的本机回环口径分开）。 */
    public static String lanIp() {
        return getLocalIp();
    }

    /** 局域网形态的服务地址（DlanWebController 拼 /file 等回连址用）。 */
    public String getLanServerUrl() {
        String ip = getLocalIp();
        if (ip == null || ip.isEmpty()) return getServerUrl();
        return "http://" + ip + ":" + boundPort;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "");
        if (uri.equals("/proxy") || uri.startsWith("/proxy?") || uri.startsWith("/proxy/")) {
            return handleProxy(session);
        }
        // 8.83 DlanWebController 其余 23 路径（G44）：HkDlanController 承接，未命中回 404。
        HkDlanController.Resp dlan = HkDlanController.handle(session);
        if (dlan != null) {
            Response resp;
            if (dlan.rawStatus > 0) {
                // NanoHTTPD 状态枚举无 302，匿名 IStatus 直出原始码（官 sendRedirect 形）。
                final int code = dlan.rawStatus;
                Response.IStatus raw = new Response.IStatus() {
                    @Override
                    public String getDescription() {
                        return code + " Redirect";
                    }

                    @Override
                    public int getRequestStatus() {
                        return code;
                    }
                };
                resp = newFixedLengthResponse(raw, dlan.mime,
                        new ByteArrayInputStream(dlan.body), dlan.body.length);
            } else {
                resp = newFixedLengthResponse(dlan.status, dlan.mime,
                        new ByteArrayInputStream(dlan.body), dlan.body.length);
            }
            for (Map.Entry<String, String> header : dlan.headers.entrySet()) {
                resp.addHeader(header.getKey(), header.getValue());
            }
            return resp;
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "");
    }

    /**
     * 8.83 DlanWebController.proxy 语义：
     *   proxyJS 为空 -> 返回 ""；
     *   否则把请求参数转 JSON 拼成 var MY_PARAMS = JSON.parse('...');\n + proxyJS，
     *   用 JSEngine.evalJSForObj(js, requestPath) 求值；
     *   结果是 "{...}" 形式的字符串 -> 按 ProxyResult{statusCode,headers,body} 回包；
     *   Location 头为 file:// 开头 -> 返回本地文件。
     */
    private Response handleProxy(IHTTPSession session) {
        String js = proxyJS;
        if (js == null || js.isEmpty()) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }
        HkJsRuntime rt = evaluator;
        if (rt == null) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }
        try {
            Map<String, List<String>> params = session.getParameters();
            // MY_PARAMS 形态照 8.83：参数恒为数组 {"k":["v"]}（fastjson 序列化 MultiValueMap）。
            // 旧实现把单值压成字符串，规则按 MY_PARAMS.k[0] 取参会取到首字符。
            Map<String, List<String>> shaped = new HashMap<>();
            if (params != null) {
                for (Map.Entry<String, List<String>> e : params.entrySet()) {
                    List<String> v = e.getValue();
                    if (v != null && !v.isEmpty()) shaped.put(e.getKey(), new ArrayList<>(v));
                }
            }
            String paramsJson = GSON.toJson(shaped);
            // 诊断：每次代理请求留下"第几集"的铁证（现有 maskedPath 把查询值脱敏成 rg=*，看不出集数）。
            java.util.List<String> _rg = shaped.get("rg"), _pp = shaped.get("p");
            com.github.catvod.crawler.SpiderDebug.log("hkproxy", "serve rg=%s p=%s",
                    (_rg == null || _rg.isEmpty()) ? "?" : _rg.get(0),
                    (_pp == null || _pp.isEmpty()) ? "?" : _pp.get(0));
            String script = "var MY_PARAMS = JSON.parse('" + escapeJs(paramsJson) + "');\n" + js;
            String path = session.getUri();
            Object result = rt.evalJSForObj(script, path);
            return buildProxyResponse(result);
        } catch (Throwable t) {
            // 之前静默吞异常回空包，播放器只看到 200 空响应、无从排查（实证）。
            com.github.catvod.crawler.SpiderDebug.log("hkproxy", "eval failed: %s: %s",
                    t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }
    }

    private Response buildProxyResponse(Object result) {
        try {
            com.github.catvod.crawler.SpiderDebug.log("hkproxy", "result type=%s",
                    result == null ? "null" : result.getClass().getSimpleName()
                            + (result instanceof Map ? " keys=" + ((Map<?, ?>) result).keySet()
                            + " bodyType=" + (((Map<?, ?>) result).get("body") == null ? "null"
                            : ((Map<?, ?>) result).get("body").getClass().getSimpleName()) : "")
                            + (result instanceof String ? " len=" + ((String) result).length() : ""));
            if (result instanceof String) {
                String s = (String) result;
                if (s != null && s.startsWith("{") && s.endsWith("}")) {
                    try {
                        ProxyResult pr = GSON.fromJson(s, ProxyResult.class);
                        if (pr != null && ((pr.headers != null && !pr.headers.isEmpty()) || (pr.body != null && !pr.body.isEmpty()))) {
                            return proxyResultResponse(pr);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, s == null ? "" : s);
            }
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) result;
                return proxyResultMapResponse(map);
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, result == null ? "" : String.valueOf(result));
        } catch (Throwable t) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
        }
    }

    private Response proxyResultResponse(ProxyResult pr) {
        Response.Status status = Response.Status.OK;
        if (pr.statusCode >= 200) {
            Response.Status mapped = Response.Status.lookup(pr.statusCode);
            if (mapped != null) status = mapped;
        }
        if (pr.headers != null && pr.headers.containsKey("Location")) {
            String loc = String.valueOf(pr.headers.get("Location"));
            if (loc != null && loc.startsWith("file://")) {
                try {
                    File f = new File(loc.replace("file://", ""));
                    if (f.exists() && f.isFile()) {
                        // 8.83 FileBody 按文件扩展名定类型（如 .mp4 → video/mp4），别写死二进制流
                        String mime = getMimeTypeForFile(loc);
                        return newFixedLengthResponse(Response.Status.OK, mime != null ? mime : "application/octet-stream", new FileInputStream(f), f.length());
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        Response resp = newFixedLengthResponse(status, MIME_PLAINTEXT, pr.body == null ? "" : pr.body);
        if (pr.headers != null) {
            for (Map.Entry<String, String> e : pr.headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    resp.addHeader(e.getKey(), e.getValue());
                }
            }
        }
        return resp;
    }

    /**
     * 照 8.83 DlanWebController.proxy 的 JSONObject 分支重写：
     * headers 里 Location 以 file:// 开头 → 回整文件（FileBody）；
     * body 是 InputStream → StreamBody（chunked 原样透传，绝不能 String.valueOf，
     * 短剧的解密流就是这样被写成一行 toString 文本、播放器解不开）；
     * body 是 byte[] → 包成流同 StreamBody；其余（String 等）按文本回。
     * 响应类型以 headers 的 Content-Type 为准。
     */
    private Response proxyResultMapResponse(Map<String, Object> map) {
        Response.Status status = Response.Status.OK;
        if (map.containsKey("statusCode")) {
            // 规则回包的 statusCode 可能是数字也可能是数字字符串：字符串此前
            // 强转 Number 必抛、被吞成默认 200，错误状态码无痕变成功。现在两形
            // 都认；真认不出走 hkproxy 诊断日志留痕，再回默认 200。
            Object rawCode = map.get("statusCode");
            Integer code = null;
            if (rawCode instanceof Number) {
                code = ((Number) rawCode).intValue();
            } else if (rawCode != null) {
                try {
                    code = Integer.parseInt(String.valueOf(rawCode).trim());
                } catch (Throwable parseFailed) {
                    com.github.catvod.crawler.SpiderDebug.log("hkproxy", "bad statusCode=%s (%s)",
                            String.valueOf(rawCode), parseFailed.getClass().getSimpleName());
                }
            }
            if (code != null) {
                Response.Status mapped = Response.Status.lookup(code);
                if (mapped != null) status = mapped;
            }
        }
        Map<String, Object> hm = null;
        Object headers = map.get("headers");
        if (headers instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) headers;
            hm = casted;
        }
        String contentType = null;
        if (hm != null) {
            for (Map.Entry<String, Object> e : hm.entrySet()) {
                if (e.getKey() != null && e.getValue() != null
                        && "Content-Type".equalsIgnoreCase(e.getKey())) {
                    contentType = String.valueOf(e.getValue());
                }
            }
        }
        // Location: file:// → 整文件回（明文已落盘的快路径）
        if (hm != null && hm.containsKey("Location")) {
            String loc = String.valueOf(hm.get("Location"));
            if (loc != null && loc.startsWith("file://")) {
                try {
                    File f = new File(loc.replace("file://", ""));
                    if (f.exists() && f.isFile()) {
                        Response resp = newFixedLengthResponse(Response.Status.OK,
                                contentType != null ? contentType : "video/mp4",
                                new FileInputStream(f), f.length());
                        addProxyHeaders(resp, hm, true);
                        return resp;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        Object body = map.get("body");
        Response resp;
        if (body instanceof InputStream) {
            resp = newChunkedResponse(status,
                    contentType != null ? contentType : "application/octet-stream", (InputStream) body);
        } else if (body instanceof byte[]) {
            byte[] bytes = (byte[]) body;
            resp = newChunkedResponse(status,
                    contentType != null ? contentType : "application/octet-stream",
                    new ByteArrayInputStream(bytes));
        } else {
            resp = newFixedLengthResponse(status,
                    contentType != null ? contentType : MIME_PLAINTEXT,
                    body == null ? "" : String.valueOf(body));
        }
        addProxyHeaders(resp, hm, false);
        return resp;
    }

    /** 把规则 headers 逐项加上（Content-Type 已用作响应类型、被消费的 Location 不重复加）。 */
    private void addProxyHeaders(Response resp, Map<String, Object> hm, boolean locationConsumed) {
        if (hm == null) return;
        for (Map.Entry<String, Object> e : hm.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if ("Content-Type".equalsIgnoreCase(e.getKey())) continue;
            if (locationConsumed && "Location".equals(e.getKey())) continue;
            resp.addHeader(e.getKey(), String.valueOf(e.getValue()));
        }
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    static class ProxyResult {
        String body;
        Map<String, String> headers;
        int statusCode;
    }
}

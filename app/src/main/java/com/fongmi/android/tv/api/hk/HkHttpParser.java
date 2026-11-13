package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.api.hk.util.HkCodeUtil;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 直接移植自海阔 8.83 {@code com.example.hikerview.service.parser.HttpParser}（do not rewrite）。
 * <p>
 * 适配说明（仅做必要的依赖替换，逻辑不动）：
 * <ul>
 * <li>fastjson → Gson</li>
 * <li>okgo HttpParams → 内置 {@link HttpParams}</li>
 * <li>CodeUtil.get/post → {@link HkCodeUtil}（含 hiker:// / file:// 本地分发，与 8.83 同款）</li>
 * <li>Timber → android.util.Log</li>
 * <li>JSEngine.getInstance() → {@link JsBridge}（由宿主注入）</li>
 * <li>StringUtil/CollectionUtil/StringUtils → {@link HkParserKit}</li>
 * <li>UAEnum → {@link HkUaEnum}</li>
 * <li>PlayData → {@link HkPlayData}</li>
 * </ul>
 */
public class HkHttpParser {
    private static final String TAG = "HkHttpParser";
    private static final Gson GSON = new Gson();

    /** JS 求值桥（宿主在初始化时注入，对应 8.83 的 JSEngine 单例）。 */
    public interface JsBridge {
        String evalJs(String js, String input);

        /** 对应 8.83 JSEngine.fetchCodeByWebView；无 WebView 基础设施时返回 null。 */
        String fetchCodeByWebView(String url, Map<String, Object> options);

        /** 对应 8.83 WebkitFetcher.canParse：url 是否走 WebView 抓取。 */
        boolean canWebViewParse(String url);
    }

    private static volatile JsBridge jsBridge;

    public static void setJsBridge(JsBridge bridge) {
        jsBridge = bridge;
    }

    /** okgo HttpParams 的最小替代（仅 8.83 用到的 put 语义）。 */
    public static class HttpParams {
        private final LinkedHashMap<String, String> map = new LinkedHashMap<>();

        public void put(String key, String value, boolean... isReplace) {
            boolean replace = isReplace != null && isReplace.length > 0 && isReplace[0];
            if (!replace && map.containsKey(key)) {
                return;
            }
            map.put(key, value);
        }

        public Map<String, String> getMap() {
            return map;
        }

        FormBody toFormBody() {
            FormBody.Builder b = new FormBody.Builder();
            for (Map.Entry<String, String> e : map.entrySet()) {
                b.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
            return b.build();
        }
    }

    public interface OnSearchCallBack {
        void onFailure(int code, String msg);

        void onSuccess(String url, String code);
    }

    public static void parseSearchUrlForHtml(String keyword, OnSearchCallBack callBack) {
        parseSearchUrlForHtml(keyword.contains("%%") ? "%%" : "**", keyword, callBack);
    }

    public static String parseSearchUrl(String url, String keyword) {
        return parseSearchUrl(url, keyword, false);
    }

    public static String parseSearchUrl(String url, String keyword, boolean keepFull) {
        String[] split = url.split(";");
        if (split.length == 1) {
            return replaceKey(url, encodeUrl1(keyword, "UTF-8"));
        }
        String encoded;
        if (split.length == 2) {
            if ("get".equalsIgnoreCase(split[1]) || "*".equals(split[1]) || "post".equalsIgnoreCase(split[1])) {
                encoded = encodeUrl1(keyword, "UTF-8");
            } else {
                encoded = encodeUrl1(keyword, split[1]);
            }
            if (!keepFull) {
                url = split[0];
            }
            return replaceKey(url, encoded);
        }
        encoded = encodeUrl1(keyword, split[2]);
        if (!keepFull) {
            url = split[0];
        }
        return replaceKey(url, encoded);
    }

    private static String replaceKey(String url, String keyword) {
        if (HkParserKit.isEmpty(url) || HkParserKit.isEmpty(keyword)) {
            return url;
        }
        if (url.contains("%%")) {
            return url.replace("%%", keyword);
        }
        return url.replace("**", keyword);
    }

    public static void parseSearchUrlForHtml(String keyword, String url, OnSearchCallBack callBack) {
        String[] split = url.split(";");
        if (split.length == 1) {
            String u = parseParamsByJs(replaceKey(split[0], encodeUrl(keyword, "UTF-8")));
            split[0] = u;
            get(u, null, null, callBack);
            return;
        }
        if (split.length == 2) {
            if ("get".equalsIgnoreCase(split[1]) || "*".equals(split[1])) {
                String u = parseParamsByJs(replaceKey(split[0], encodeUrl(keyword, "UTF-8")));
                split[0] = u;
                get(u, null, getHeaders(url), callBack);
                return;
            }
            if ("post".equalsIgnoreCase(split[1])) {
                String u = parseParamsByJs(replaceKey(split[0], encodeUrl(keyword, "UTF-8")));
                split[0] = u;
                String[] parts = HkParserKit.splitUrlByQuestionMark(u);
                String[] kvs = parts.length > 1 ? parts[1].split("&") : new String[0];
                HttpParams params = new HttpParams();
                for (String kv : kvs) {
                    if (!TextUtils.isEmpty(kv)) {
                        String[] kvSplit = kv.split("=");
                        if (kvSplit.length >= 2) {
                            params.put(kvSplit[0], HkParserKit.arrayToString(kvSplit, 1, "="));
                        }
                    }
                }
                post(parts[0], null, getHeaders(url), params, callBack);
                return;
            }
            if (split[1].startsWith("{") && split[1].endsWith("}")) {
                split[1] = "utf-8";
            }
            String u = parseParamsByJs(replaceKey(split[0], encodeUrl(keyword, split[1])));
            split[0] = u;
            get(u, split[1], getHeaders(url), callBack);
            return;
        }
        String encoded = encodeUrl(keyword, split[2]);
        if ("post".equalsIgnoreCase(split[1])) {
            if (HkParserKit.isNotEmpty(split[2]) && !"utf-8".equalsIgnoreCase(split[2])) {
                encoded = decodeUrl(encoded, split[2]);
            }
            String u = parseParamsByJs(replaceKey(split[0], encoded));
            split[0] = u;
            String[] parts = HkParserKit.splitUrlByQuestionMark(u);
            String[] kvs = parts.length > 1 ? parts[1].split("&") : new String[0];
            HttpParams params = new HttpParams();
            for (String kv : kvs) {
                if (!TextUtils.isEmpty(kv)) {
                    String[] kvSplit = kv.split("=");
                    if (kvSplit.length >= 2) {
                        params.put(kvSplit[0], HkParserKit.arrayToString(kvSplit, 1, "="));
                    }
                }
            }
            post(parts[0], split[2], getHeaders(url), params, callBack);
            return;
        }
        String u = parseParamsByJs(replaceKey(split[0], encoded));
        split[0] = u;
        get(u, split[2], getHeaders(url), callBack);
    }

    public static String getUrlAppendUA(String url, String uaCode) {
        if (HkParserKit.isEmpty(uaCode) || HkUaEnum.AUTO.getCode().equals(uaCode)) {
            return url;
        }
        String[] split = url.split(";");
        if (split.length == 1) {
            return url + ";get;UTF-8;{User-Agent@" + getRealUa(uaCode) + "}";
        }
        if (split.length == 2) {
            return url + ";UTF-8;{User-Agent@" + getRealUa(uaCode) + "}";
        }
        if (split.length == 3) {
            return url + ";{User-Agent@" + getRealUa(uaCode) + "}";
        }
        if (url.contains("User-Agent@")) {
            return url;
        }
        String last = split[split.length - 1];
        if (!last.startsWith("{") || !last.endsWith("}")) {
            return url;
        }
        String inner = last.substring(0, last.length() - 1);
        if (inner.length() == 1) {
            return HkParserKit.arrayToString(split, 0, split.length - 1, ";") + ";{User-Agent@" + getRealUa(uaCode) + "}";
        }
        return HkParserKit.arrayToString(split, 0, split.length - 1, ";") + ";" + (inner + "&&User-Agent@" + getRealUa(uaCode) + "}");
    }

    public static String getUrlReplaceUA(String url, String uaCode) {
        if (HkParserKit.isEmpty(uaCode)) {
            return url;
        }
        if (HkUaEnum.AUTO.getCode().equals(uaCode)) {
            uaCode = "";
        }
        String[] split = url.split(";");
        if (split.length == 1) {
            return url + ";get;UTF-8;{User-Agent@" + getRealUa(uaCode) + "}";
        }
        if (split.length == 2) {
            return url + ";UTF-8;{User-Agent@" + getRealUa(uaCode) + "}";
        }
        if (split.length == 3) {
            return url + ";{User-Agent@" + getRealUa(uaCode) + "}";
        }
        if (url.contains("User-Agent@")) {
            String last = split[split.length - 1];
            if (!last.startsWith("{") || !last.endsWith("}")) {
                return url;
            }
            String inner = last.substring(1, last.length() - 1);
            if (inner.length() == 0) {
                return HkParserKit.arrayToString(split, 0, split.length - 1, ";") + ";{User-Agent@" + getRealUa(uaCode) + "}";
            }
            String[] kvs = inner.split("&&");
            for (int i = 0; i < kvs.length; i++) {
                if (kvs[i].startsWith("User-Agent@")) {
                    kvs[i] = "User-Agent@" + getRealUa(uaCode);
                    break;
                }
            }
            return HkParserKit.arrayToString(split, 0, split.length - 1, ";") + ";" + ("{" + HkParserKit.arrayToString(kvs, 0, "&&") + "}");
        }
        return getUrlAppendUA(url, uaCode);
    }

    private static String getRealUa(String uaCode) {
        for (HkUaEnum e : HkUaEnum.values()) {
            if (e.getCode().equals(uaCode)) {
                return e.getContent().replace(";", "；；");
            }
        }
        return uaCode.replace(";", "；；");
    }

    private static String parseParamsByJs(String url) {
        String[] parts = HkParserKit.splitUrlByQuestionMark(url);
        if (parts.length > 1) {
            String[] kvs = parts[1].split("&");
            for (int i = 0; i < kvs.length; i++) {
                String[] kv = kvs[i].split("=");
                if (kv.length > 1) {
                    String v = HkParserKit.arrayToString(kv, 1, "=");
                    String[] jsSplit = v.split("\\.js:");
                    if (jsSplit.length > 1 && jsBridge != null) {
                        v = jsBridge.evalJs(jsSplit[1], jsSplit[0]);
                    }
                    kvs[i] = kv[0] + "=" + v;
                }
            }
            url = parts[0] + "?" + HkParserKit.listToString(Arrays.asList(kvs), "&");
        }
        String[] qSplit = url.split("\\?");
        qSplit[0] = getUrlByJs(qSplit[0]);
        return HkParserKit.arrayToString(qSplit, 0, "?");
    }

    private static String getUrlByJs(String url) {
        String[] split = url.split("\\.js:");
        if (split.length > 1 && jsBridge != null) {
            return jsBridge.evalJs(split[1], split[0]);
        }
        return url;
    }

    private static String parseHeadersByJs(String headers) {
        String[] kvs = headers.split("&&");
        for (int i = 0; i < kvs.length; i++) {
            String[] kv = kvs[i].split("@");
            if (kv.length > 1) {
                String[] jsSplit = kv[1].split("\\.js:");
                if (jsSplit.length > 1 && jsBridge != null) {
                    kv[1] = jsBridge.evalJs(jsSplit[1], jsSplit[0]);
                }
                kvs[i] = kv[0] + "@" + kv[1];
            }
        }
        return HkParserKit.listToString(Arrays.asList(kvs), "&&");
    }

    public static String getCode(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        String[] split = url.split(";");
        String code = split.length >= 3 ? split[2] : null;
        return "*".equals(code) ? "UTF-8" : code;
    }

    public static String getThirdDownloadSource(String url) {
        return HkParserKit.isEmpty(url) ? url : url.replace("download://", "").split(";")[0].split("##")[0];
    }

    public static Map<String, String> getHeaders(String url, HkPlayData playData, int index) {
        if (playData.getHeaders() != null && playData.getHeaders().size() > index) {
            Map<String, String> map = playData.getHeaders().get(index);
            if (map == null || map.isEmpty()) {
                return map;
            }
            for (String k : map.keySet()) {
                map.put(k, HkParserKit.decodeConflictStr(map.get(k)));
            }
            return map;
        }
        return getHeaders(url);
    }

    public static Map<String, String> getHeaders(String url) {
        if (!TextUtils.isEmpty(url) && (!url.startsWith("{") || !url.endsWith("}"))) {
            String[] split = url.split(";");
            String last = split[split.length - 1];
            if (last.startsWith("{") && last.endsWith("}")) {
                String decoded = HkParserKit.decodeConflictStr(last);
                HashMap<String, String> map = new HashMap<>();
                String inner = decoded.substring(1);
                inner = inner.substring(0, inner.length() - 1);
                for (String kv : parseHeadersByJs(inner).split("&&")) {
                    String[] kvSplit = kv.split("@");
                    if (kvSplit.length >= 2) {
                        if ("getTimeStamp()".equals(kvSplit[1])) {
                            map.put(kvSplit[0], System.currentTimeMillis() + "");
                        } else {
                            if ("cookie".equalsIgnoreCase(kvSplit[0]) && HkParserKit.containsChinese(kvSplit[1])) {
                                String[] cookies = kvSplit[1].split(";");
                                for (int i = 0; i < cookies.length; i++) {
                                    String[] ckv = cookies[i].split("=");
                                    if (HkParserKit.containsChinese(ckv[0])) {
                                        ckv[0] = encodeUrl(ckv[0]);
                                    }
                                    if (ckv.length > 1 && HkParserKit.containsChinese(ckv[1])) {
                                        ckv[1] = encodeUrl(ckv[1]);
                                    }
                                    cookies[i] = HkParserKit.arrayToString(ckv, 0, "=");
                                }
                                kvSplit[1] = HkParserKit.arrayToString(cookies, 0, ";");
                            }
                            map.put(kvSplit[0], kvSplit[1]);
                        }
                    }
                }
                return map;
            }
        }
        return null;
    }

    public static String getHeadersStr(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append("&&");
            }
            sb.append(e.getKey()).append("@").append(e.getValue().replace(";", "；；"));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static String getRealUrlFilterHeaders(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String[] split = url.split(";");
        String last = split[split.length - 1];
        return (last.startsWith("{") && last.endsWith("}")) ? split[0] : url;
    }

    public static HkPlayData getPlayData(String url) {
        if (HkParserKit.isEmpty(url)) {
            return new HkPlayData();
        }
        if (url.startsWith("{") && url.endsWith("}")) {
            try {
                HkPlayData data = GSON.fromJson(url, HkPlayData.class);
                if (data.getUrls() != null && !data.getUrls().isEmpty()) {
                    if (data.getNames() == null || data.getNames().isEmpty()) {
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < data.getUrls().size(); i++) {
                            names.add("线路" + (i + 1));
                        }
                        data.setNames(names);
                    } else if (data.getNames().size() > data.getUrls().size()) {
                        data.setNames(new ArrayList<>(data.getNames().subList(0, data.getUrls().size())));
                    }
                }
                return data;
            } catch (Exception e) {
                Log.e(TAG, "getPlayData", e);
                HkPlayData data = new HkPlayData();
                data.setUrl(url);
                return data;
            }
        }
        HkPlayData data = new HkPlayData();
        data.setUrl(url.split(";")[0]);
        data.setHeader(getHeaders(url));
        return data;
    }

    public static String getPlayUrlWithHeader(String url) {
        return getUrlWithHeader(url, getPosUrl(url));
    }

    private static String getPosUrl(String url) {
        HkPlayData data = getPlayData(url);
        return (data.getUrls() != null && !data.getUrls().isEmpty()) ? data.getUrls().get(0) : url;
    }

    private static String getUrlWithHeader(String url, String posUrl) {
        String[] split = url.split(";");
        return split.length <= 1 ? posUrl : posUrl + ";" + HkParserKit.arrayToString(split, 1, ";");
    }

    /** 8.83 CodeUtil.get 语义：charset 可空，headers 可空，回调 onSuccess(url, code)。 */
    public static void get(String url, String charset, Map<String, String> headers, final OnSearchCallBack callBack) {
        try {
            final String decodedUrl = HkParserKit.decodeConflictStr(url);
            JsBridge bridge = jsBridge;
            if (bridge != null && bridge.canWebViewParse(decodedUrl)) {
                final Map<String, Object> options = new HashMap<>();
                options.put("headers", headers);
                options.put("timeout", 10000);
                final String noScheme = HkParserKit.replaceOnceIgnoreCase(decodedUrl, "webview://", "");
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        String code = bridge.fetchCodeByWebView(noScheme, options);
                        callBack.onSuccess(decodedUrl, code);
                    } catch (Exception e) {
                        Log.e(TAG, "webview get", e);
                        callBack.onFailure(500, e.toString());
                    }
                });
                return;
            }
            HkCodeUtil.get(decodedUrl, charset, headers, new HkCodeUtil.OnCodeGetListener() {
                @Override
                public void onSuccess(String result) {
                    callBack.onSuccess(decodedUrl, result);
                }

                @Override
                public void onFailure(int code, String msg) {
                    callBack.onFailure(code, msg);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "get", e);
            callBack.onFailure(500, e.toString());
        }
    }

    /** 8.83 HttpParser.post 语义：内部委托 CodeUtil.post（HttpParser$2 包装同款）。 */
    public static void post(String url, String charset, Map<String, String> headers, HttpParams params, final OnSearchCallBack callBack) {
        try {
            final String decodedUrl = HkParserKit.decodeConflictStr(url);
            HkCodeUtil.post(decodedUrl, params != null ? params.getMap() : null, charset, headers,
                    new HkCodeUtil.OnCodeGetListener() {
                        @Override
                        public void onSuccess(String result) {
                            callBack.onSuccess(decodedUrl, result);
                        }

                        @Override
                        public void onFailure(int code, String msg) {
                            callBack.onFailure(code, msg);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "post", e);
            callBack.onFailure(500, e.toString());
        }
    }

    public static String encodeUrl(String str, String charset) {
        if (HkParserKit.isEmpty(charset) || "UTF-8".equalsIgnoreCase(charset) || "*".equals(charset)) {
            return str;
        }
        try {
            return URLEncoder.encode(str, charset);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "encodeUrl", e);
            return str;
        }
    }

    public static String encodeUrl1(String str, String charset) {
        if (HkParserKit.isEmpty(charset) || "*".equals(charset)) {
            return str;
        }
        try {
            return URLEncoder.encode(str, charset);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "encodeUrl1", e);
            return str;
        }
    }

    public static String encodeUrl(String str) {
        if (str == null) {
            return null;
        }
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "encodeUrl", e);
            return str;
        }
    }

    public static String decodeUrl(String str, String charset) {
        if (str == null) {
            return null;
        }
        try {
            return URLDecoder.decode(str.replaceAll("%(?![0-9a-fA-F]{2})", "%25").replaceAll("\\+", "%2B"), charset);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "decodeUrl", e);
            return str;
        }
    }

    /**
     * 8.83 原文（HttpParser.smali:370-884，jadx 未能反编译，按 smali 逐分支移植）：
     * 去掉 x5://、webview://、x5Rule://、webRule://、download://、video://、
     * hiker://empty 系列前缀后，尾段统一处理：;[firstPage=...] 取括号内、
     * fypage@ 算式（以 1 为初值按 @ 分段顺序做 -/+/×/÷ 整数运算）求值、
     * 兜底 fypage 替换为 "1"。异常时返回当前已处理到的串（原文 catch 返回 p0）。
     * 注：本方法此前转写丢了整个尾段（empty 分支提前 return、fypage 两段全无、
     * 兜底返全量 url），第④块按 smali 补回；形态与 ui/hk 的 HkStringUtil 同名实现一致。
     */
    public static String getFirstPageUrl(String url) {
        if (HkParserKit.isEmpty(url)) {
            return url;
        }
        String p0 = url;
        try {
            if (p0.startsWith("x5://")) {
                return p0.replace("x5://", "");
            }
            if (p0.startsWith("webview://")) {
                return p0.replace("webview://", "");
            }
            if (p0.startsWith("x5Rule://")) {
                return p0.split("@")[0].replace("x5Rule://", "");
            }
            if (p0.startsWith("webRule://")) {
                return p0.split("@")[0].replace("webRule://", "");
            }
            if (p0.startsWith("hiker://empty##https%3A%2F%2F") || p0.startsWith("hiker://empty##http%3A%2F%2F")) {
                p0 = decodeUrl(HkParserKit.replaceOnce(p0, "hiker://empty##", ""), "UTF-8");
            } else if (p0.startsWith("hiker://empty#http")) {
                p0 = HkParserKit.replaceOnce(p0, "hiker://empty#", "");
            } else if (p0.startsWith("hiker://empty##http")) {
                p0 = HkParserKit.replaceOnce(p0, "hiker://empty##", "");
            } else if (p0.startsWith("hiker://empty$http")) {
                p0 = HkParserKit.replaceOnce(p0, "hiker://empty$", "");
            } else if (p0.startsWith("hiker://empty$$http")) {
                p0 = HkParserKit.replaceOnce(p0, "hiker://empty$$", "");
            } else if (p0.startsWith("hiker://empty$$$http")) {
                p0 = HkParserKit.replaceOnce(p0, "hiker://empty$$$", "");
            } else if (p0.startsWith("download://")) {
                return p0.replace("download://", "");
            } else if (p0.startsWith("video://")) {
                return p0.replace("video://", "");
            }
            String seg = p0.split(";")[0];
            String[] fpSplit = seg.split("\\[firstPage=");
            if (fpSplit.length > 1) {
                return fpSplit[1].split("]")[0];
            }
            if (seg.contains("fypage@")) {
                String[] parts = seg.split("fypage@");
                String[] expr = parts[1].split("@");
                int page = 1;
                for (int i = 0; i < expr.length - 1; i++) {
                    String t = expr[i];
                    if (t.startsWith("-")) {
                        page -= Integer.parseInt(t.replace("-", ""));
                    } else if (t.startsWith("+")) {
                        page += Integer.parseInt(t.replace("+", ""));
                    } else if (t.startsWith("*")) {
                        page *= Integer.parseInt(t.replace("*", ""));
                    } else if (t.startsWith("/")) {
                        page /= Integer.parseInt(t.replace("/", ""));
                    }
                }
                return parts[0] + page + expr[expr.length - 1];
            }
            return seg.replace("fypage", "1");
        } catch (Exception e) {
            Log.e(TAG, "getFirstPageUrl", e);
            return p0;
        }
    }

    public static String replaceQuery(String url, String key, String value) {
        if (HkParserKit.isEmpty(url)) {
            return url;
        }
        String[] parts = HkParserKit.splitUrlByQuestionMark(url.split(";")[0]);
        if (parts.length > 1) {
            String[] kvs = parts[1].split("&");
            Map<String, String> map = new LinkedHashMap<>();
            for (String kv : kvs) {
                if (!TextUtils.isEmpty(kv)) {
                    String[] kvSplit = kv.split("=");
                    if (kvSplit.length >= 2) {
                        map.put(kvSplit[0], HkParserKit.decodeConflictStr(HkParserKit.arrayToString(kvSplit, 1, "=")));
                    }
                }
            }
            StringBuilder sb = new StringBuilder(parts[0]).append("?");
            boolean first = true;
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                if (key.equals(e.getKey())) {
                    sb.append(e.getKey()).append("=").append(value);
                } else {
                    sb.append(e.getKey()).append("=").append(e.getValue());
                }
                first = false;
            }
            return sb.toString();
        }
        return url + "?" + key + "=" + value;
    }

    public static Map<String, String> getParamsByUrl(String url) {
        Map<String, String> map = new HashMap<>();
        if (HkParserKit.isEmpty(url)) {
            return map;
        }
        String[] split = url.split(";");
        String[] parts = HkParserKit.splitUrlByQuestionMark(split[0]);
        String[] kvs = parts.length > 1 ? parts[1].split("&") : new String[0];
        for (String kv : kvs) {
            if (!TextUtils.isEmpty(kv)) {
                String[] kvSplit = kv.split("=");
                if (kvSplit.length >= 2) {
                    map.put(kvSplit[0], HkParserKit.decodeConflictStr(HkParserKit.arrayToString(kvSplit, 1, "=")));
                }
            }
        }
        return map;
    }
}

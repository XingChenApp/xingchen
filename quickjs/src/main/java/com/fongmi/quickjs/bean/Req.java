package com.fongmi.quickjs.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Req {

    @SerializedName("buffer")
    private Integer buffer;
    @SerializedName("redirect")
    private Integer redirect;
    @SerializedName("timeout")
    private Integer timeout;
    @SerializedName("postType")
    private String postType;
    @SerializedName("method")
    private String method;
    @SerializedName("body")
    private String body;
    @SerializedName("data")
    private JsonElement data;
    @SerializedName("headers")
    private JsonElement headers;
    @SerializedName("rejectCoding")
    private Boolean rejectCoding;
    // 8.83 fetch0 的 per-request dns 选项（HttpHelper.smali:944 区取键、装配末段
    // buildAutoDNSClient(dns,url,client)）：http 开头＝DoH 地址、否则静态 IP 表
    @SerializedName("dns")
    private String dns;

    public static Req objectFrom(String json) {
        return new Gson().fromJson(normalizeBody(json), Req.class);
    }

    // 8.83 选项语义归一化（HttpHelper.fetch0）：在 Gson 严格反序列化前处理，否则布尔 redirect、
    // 对象 body、单数 header 等合法写法都会让 fromJson 抛错、请求根本发不出去。
    private static String normalizeBody(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) return json;
            JsonObject obj = parsed.getAsJsonObject();
            boolean changed = false;
            // redirect 允许布尔（8.83 (Boolean) 强转），转 1/0
            JsonElement redirect = obj.get("redirect");
            if (redirect != null && redirect.isJsonPrimitive() && redirect.getAsJsonPrimitive().isBoolean()) {
                obj.addProperty("redirect", redirect.getAsBoolean() ? 1 : 0);
                changed = true;
            }
            // headers 取不到时认单数 header（HttpHelper:84-87）
            if (!obj.has("headers") && obj.has("header")) {
                obj.add("headers", obj.get("header"));
                changed = true;
            }
            // headers 里的 body 键同时是请求体缺省值（HttpHelper:149-153），options.body 优先
            if (!obj.has("body")) {
                JsonElement hdrs = obj.get("headers");
                if (hdrs != null && hdrs.isJsonObject()) {
                    JsonElement hb = hdrs.getAsJsonObject().get("body");
                    if (hb != null && hb.isJsonPrimitive() && hb.getAsJsonPrimitive().isString()) {
                        obj.addProperty("body", hb.getAsString());
                        changed = true;
                    }
                }
            }
            JsonElement body = obj.get("body");
            if (body != null && !body.isJsonNull()
                    && !(body.isJsonPrimitive() && body.getAsJsonPrimitive().isString())) {
                // body 允许对象/数组：原版取出后直接序列化成 JSON 字符串（HttpHelper:167-176）；
                // 本类 body 字段是 String，不预处理的话 Gson 会抛 JsonSyntaxException
                Gson gson = new Gson();
                obj.addProperty("body", gson.toJson(body));
                changed = true;
                // 非字符串 body 且 method 不在 PUT/GET/HEAD 时强制 POST（HttpHelper:171-173）
                JsonElement method = obj.get("method");
                String m = method != null && method.isJsonPrimitive() && method.getAsJsonPrimitive().isString()
                        ? method.getAsString() : null;
                if (m == null || !(m.equalsIgnoreCase("PUT") || m.equalsIgnoreCase("GET") || m.equalsIgnoreCase("HEAD"))) {
                    obj.addProperty("method", "POST");
                }
            }
            return changed ? new Gson().toJson(obj) : json;
        } catch (Exception e) {
            return json;
        }
    }

    public int getBuffer() {
        return buffer == null ? 0 : buffer;
    }

    public String getDns() {
        return dns;
    }

    public Integer getRedirect() {
        return redirect == null ? 1 : redirect;
    }

    public Integer getTimeout() {
        return timeout == null ? 10000 : timeout;
    }

    public String getPostType() {
        return TextUtils.isEmpty(postType) ? "json" : postType;
    }

    public String getMethod() {
        return TextUtils.isEmpty(method) ? "get" : method;
    }

    public String getBody() {
        return body;
    }

    public JsonElement getData() {
        return data;
    }

    private JsonElement getHeaders() {
        return headers;
    }

    public boolean isRedirect() {
        return getRedirect() == 1;
    }

    public boolean isRejectCoding() {
        return rejectCoding != null && rejectCoding;
    }

    public Map<String, String> getHeader() {
        return Json.toMap(getHeaders());
    }

    public String getCharset() {
        Map<String, String> header = getHeader();
        // 8.83 HttpHelper 认的三种写法（按此顺序）：content-type / Content-Type / Content-type
        // 环 24-5（硬门审计 B）：键序改为首个非空值定夺——键在场但值为空串时不算
        // 定夺、继续下探下一拼写（旧形 containsKey 即定夺，空值把真值挡死）。
        List<String> keys = Arrays.asList("content-type", "Content-Type", "Content-type");
        for (String key : keys) {
            String v = header.get(key);
            if (v != null && !v.isEmpty()) return getCharset(v);
        }
        return "UTF-8";
    }

    private String getCharset(String value) {
        // 环 24-5：官切分＝整值 indexOf("charset=") 取标记后的全余串（不按 ";" 分段、
        // 不 trim），无果再试官自己的笔误拼写 "charst="；旧分段切法在
        // "charset=UTF-8; boundary=x" 这类多参数值上与官分叉（官得全余串）。
        String rest = charsetRemainder(value);
        return rest != null ? rest : "UTF-8";
    }

    /** 官 getCharset 切分本体：先 "charset=" 后 "charst="，取标记后全余串；皆无回 null。 */
    private static String charsetRemainder(String value) {
        int i = value.indexOf("charset=");
        if (i >= 0) return value.substring(i + "charset=".length());
        int j = value.indexOf("charst=");
        if (j >= 0) return value.substring(j + "charst=".length());
        return null;
    }

    /**
     * 请求头 content-type 里显式声明的 charset（charset=/charst=），未声明回 null。
     * 8.83 CharsetStringConvert 以 charset 空/非空分流：非空按其解码、空走
     * ResponseBody.string()（按响应头/BOM 推断）；getCharset() 的 "UTF-8" 缺省
     * 分不清显式声明与未声明，故另设此口，键序与 getCharset() 一致（首个
     * 非空的 content-type 键定夺，环 24-5；定夺值内无标记回 null、不下探）。
     */
    public String getCharsetOrNull() {
        Map<String, String> header = getHeader();
        List<String> keys = Arrays.asList("content-type", "Content-Type", "Content-type");
        for (String key : keys) {
            String v = header.get(key);
            if (v != null && !v.isEmpty()) return declaredCharset(v);
        }
        return null;
    }

    private String declaredCharset(String value) {
        // 环 24-5：与 getCharset(String) 共用官切分（整值取全余串）。
        return charsetRemainder(value);
    }
}

package com.fongmi.android.tv.api.hk;

import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.utils.Logger;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 海阔图片解密链接处理（官方 help_js.json）。
 *
 * <p>{@code $(url,headers).image(fn,...)} 生成 {@code url@headers={}@js=(fn)()} 格式的
 * 图片解密链接：图片加载器下载原图后，以图片字节（base64）为 {@code input} 运行 JS 解密，
 * 显示解密结果。失败时返回 null，调用方回退加载原 URL。</p>
 */
public class HkImage {

    private static final String TAG = "HkImage";
    private static volatile HkJsRuntime decryptRt;

    /** 是否为图片解密链接（pic_url 含 @js=）。 */
    public static boolean isDecryptUrl(String url) {
        return !TextUtils.isEmpty(url) && url.contains("@js=");
    }

    /** 从解密链接中提取原图 URL（@headers= 之前部分）。失败返回原串。 */
    public static String baseUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        int hi = url.indexOf("@headers=");
        int ji = url.indexOf("@js=");
        int cut = hi >= 0 ? hi : ji;
        return cut >= 0 ? url.substring(0, cut).trim() : url.trim();
    }

    /**
     * 下载并解密，返回可显示的图片字节；任何失败返回 null。
     * 在后台线程调用。
     */
    public static byte[] decrypt(String picUrl) {
        try {
            int ji = picUrl.indexOf("@js=");
            if (ji < 0) return null;
            int hi = picUrl.indexOf("@headers=");
            String base = (hi >= 0 ? picUrl.substring(0, hi) : picUrl.substring(0, ji)).trim();
            String headersJson = "{}";
            if (hi >= 0) headersJson = picUrl.substring(hi + 9, ji).trim();
            String jsCode = picUrl.substring(ji + 4).trim();
            if (TextUtils.isEmpty(base) || TextUtils.isEmpty(jsCode)) return null;

            Map<String, String> headers = parseHeaders(headersJson);
            byte[] raw = HkHttp.getBytes(base, headers);
            if (raw == null || raw.length == 0) return null;

            String b64 = Base64.encodeToString(raw, Base64.NO_WRAP);
            String result = evalDecrypt(jsCode, b64);
            if (TextUtils.isEmpty(result)) return null;
            return toBytes(result.trim(), headers);
        } catch (Throwable e) {
            Logger.t(TAG).d("decrypt failed: %s", e.getMessage());
            return null;
        }
    }

    private static Map<String, String> parseHeaders(String json) {
        Map<String, String> map = new HashMap<>();
        try {
            JSONObject o = new JSONObject(json);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, o.optString(k, ""));
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    /** 解密结果 → 字节：dataURL / base64 / http直链。 */
    private static byte[] toBytes(String result, Map<String, String> headers) {
        try {
            if (result.startsWith("data:")) {
                int c = result.indexOf(',');
                if (c > 0) return Base64.decode(result.substring(c + 1).trim(), Base64.DEFAULT);
                return null;
            }
            if (result.startsWith("http://") || result.startsWith("https://")) {
                return HkHttp.getBytes(result, headers);
            }
            // 纯 base64
            String clean = result.replaceAll("\\s+", "");
            if (clean.length() > 100 && clean.matches("[A-Za-z0-9+/=_-]+")) {
                return Base64.decode(clean, Base64.DEFAULT);
            }
        } catch (Throwable e) {
            Logger.t(TAG).d("toBytes failed: %s", e.getMessage());
        }
        return null;
    }

    /** 共享解密 JS 运行时（input=图片 base64，失败返回 null 不抛错）。 */
    private static String evalDecrypt(String jsCode, String base64Input) {
        try {
            HkJsRuntime rt = getDecryptRt();
            if (rt == null) return null;
            String r = rt.eval(jsCode, base64Input);
            if (!TextUtils.isEmpty(rt.getError())) return null;
            return r;
        } catch (Throwable e) {
            Logger.t(TAG).d("evalDecrypt failed: %s", e.getMessage());
            return null;
        }
    }

    private static synchronized HkJsRuntime getDecryptRt() {
        if (decryptRt == null) {
            try {
                HkJsRuntime rt = new HkJsRuntime(null);
                rt.init();
                decryptRt = rt;
            } catch (Throwable e) {
                Logger.t(TAG).d("decryptRt init failed: %s", e.getMessage());
                return null;
            }
        }
        return decryptRt;
    }
}

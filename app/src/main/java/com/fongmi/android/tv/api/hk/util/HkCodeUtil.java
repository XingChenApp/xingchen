package com.fongmi.android.tv.api.hk.util;

import android.util.Log;

import com.fongmi.android.tv.api.hk.HkHttp;
import com.fongmi.android.tv.api.hk.HkJsRuntime;
import com.fongmi.android.tv.api.hk.HkParserKit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.Inflater;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 移植自 8.83 {@code com.example.hikerview.service.http.CodeUtil}（smali 1724 行）。
 * <p>
 * 8.83 原文基于 OkGo；星辰无 OkGo 依赖，此处按相同语义用 OkHttp（{@link HkHttp#client()}，
 * CookieJar/拦截器均生效、DNS 系统默认）重写：
 * <ul>
 *   <li>{@code hiker://files/} / {@code file://} / {@code /} 开头 → 本地文件逻辑（8.83 同款）</li>
 *   <li>{@code hiker://}（非 files）→ 走 {@link HkHikerRuleUtil}（8.83 dealFileByHiker 同款）</li>
 *   <li>其余 http(s) → OkHttp 异步/同步请求</li>
 * </ul>
 */
public final class HkCodeUtil {

    private static final String TAG = "HkCodeUtil";

    private HkCodeUtil() {
    }

    /** 8.83 CodeUtil.OnCodeGetListener 原样移植。 */
    public interface OnCodeGetListener {
        void onSuccess(String result);

        void onFailure(int code, String msg);
    }

    /**
     * 8.83 CodeUtil.decompress(byte[]) 原样移植（smali 115-234）：
     * Inflater(nowrap=true) 解压，异常时打日志并返回原输入（8.83 行为：catch 后仍 return p0）。
     */
    public static byte[] decompress(byte[] input) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.reset();
            inflater.setInput(input);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
            try {
                byte[] buf = new byte[1024];
                while (!inflater.finished()) {
                    int n = inflater.inflate(buf);
                    bos.write(buf, 0, n);
                }
                input = bos.toByteArray();
            } catch (Exception e) {
                Log.e(TAG, "decompress", e);
            } finally {
                try {
                    bos.close();
                } catch (IOException e) {
                    Log.e(TAG, "decompress close", e);
                }
            }
        } finally {
            inflater.end();
        }
        return input;
    }

    /** 8.83 CodeUtil.get(url, listener) → get(url, "UTF-8", null, listener)。 */
    public static void get(String url, OnCodeGetListener listener) {
        get(url, "UTF-8", null, listener);
    }

    /**
     * 8.83 CodeUtil.get(url, charset, headers, listener) 语义移植（smali 609-925）。
     * charset 为空或 "UTF-8" 时按 UTF-8 解码；否则按指定 charset。
     */
    public static void get(String url, String charset, Map<String, String> headers, OnCodeGetListener listener) {
        final long start = System.currentTimeMillis();
        url = clearTag(url);
        url = HkParserKit.decodeConflictStr(url);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                e.setValue(HkParserKit.decodeConflictStr(e.getValue()));
            }
        }
        if (url.startsWith("hiker://files/")) {
            String name = trimBlanks(url.replace("hiker://files/", ""));
            File f = new File(HkJsRuntime.getFilePath(name));
            if (f.exists()) {
                listener.onSuccess(readFile(f));
            } else {
                listener.onFailure(404, url + "文件不存在");
            }
            return;
        }
        if (url.startsWith("file://") || url.startsWith("/")) {
            dealFileByPath(url, listener);
            return;
        }
        if (url.startsWith("hiker://")) {
            dealFileByHiker(url, listener);
            return;
        }
        final boolean useUtf8 = charset == null || charset.isEmpty() || "UTF-8".equals(charset);
        final Charset cs = useUtf8 ? Charset.forName("UTF-8") : Charset.forName(charset);
        Request.Builder rb = new Request.Builder().url(url);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
        }
        HkHttp.clientFor(url).newCall(rb.build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "get", e);
                listener.onFailure(500, e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        listener.onFailure(r.code(), r.message());
                        return;
                    }
                    byte[] bytes = r.body() != null ? r.body().bytes() : new byte[0];
                    listener.onSuccess(new String(bytes, cs));
                } catch (Exception e) {
                    Log.e(TAG, "get", e);
                    listener.onFailure(500, e.getMessage());
                }
            }
        });
    }

    /** 8.83 CodeUtil.post(url, params, listener) → post(url, params, "UTF-8", null, listener)。 */
    public static void post(String url, Map<String, String> params, OnCodeGetListener listener) {
        post(url, params, "UTF-8", null, listener);
    }

    /**
     * 8.83 CodeUtil.post(url, params, charset, headers, listener) 语义移植（smali 1125+）。
     * 8.83 用 OkGo HttpParams；此处 params 为表单字段 Map。
     */
    public static void post(String url, Map<String, String> params, String charset,
                            Map<String, String> headers, OnCodeGetListener listener) {
        url = clearTag(url);
        url = HkParserKit.decodeConflictStr(url);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                e.setValue(HkParserKit.decodeConflictStr(e.getValue()));
            }
        }
        if (url.startsWith("hiker://files/") || url.startsWith("file://")
                || url.startsWith("/") || url.startsWith("hiker://")) {
            // 8.83 post 同样先走文件分支（与 get 同款判断）
            get(url, charset, headers, listener);
            return;
        }
        final boolean useUtf8 = charset == null || charset.isEmpty() || "UTF-8".equals(charset);
        final Charset cs = useUtf8 ? Charset.forName("UTF-8") : Charset.forName(charset);
        FormBody.Builder fb = new FormBody.Builder();
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                fb.add(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        Request.Builder rb = new Request.Builder().url(url).post(fb.build());
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
        }
        HkHttp.clientFor(url).newCall(rb.build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "post", e);
                listener.onFailure(500, e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful()) {
                        listener.onFailure(r.code(), r.message());
                        return;
                    }
                    byte[] bytes = r.body() != null ? r.body().bytes() : new byte[0];
                    listener.onSuccess(new String(bytes, cs));
                } catch (Exception e) {
                    Log.e(TAG, "post", e);
                    listener.onFailure(500, e.getMessage());
                }
            }
        });
    }

    /**
     * 8.83 CodeUtil.getHeaders(url, headers) 语义移植（smali 926-1041）：
     * 发起 GET 请求并返回响应头，异常时返回 null。
     */
    public static Headers getHeaders(String url, Map<String, String> headers) {
        try {
            Request.Builder rb = new Request.Builder().url(url);
            if (headers != null && !headers.isEmpty()) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    rb.header(e.getKey(), e.getValue());
                }
            }
            try (Response r = HkHttp.clientFor(url).newCall(rb.build()).execute()) {
                return r.headers();
            }
        } catch (Exception e) {
            Log.e(TAG, "getHeaders", e);
            return null;
        }
    }

    /**
     * 8.83 CodeUtil.download(url, savePath, headers, listener) 语义移植（smali 235-400）。
     * savePath 先过 JSEngine.getFilePath；hiker://files/、file://、/ 开头走本地文件拷贝。
     */
    public static void download(String url, String savePath, Map<String, String> headers,
                                OnCodeGetListener listener) {
        final String target = HkJsRuntime.getFilePath(savePath);
        if (url.startsWith("hiker://files/") || url.startsWith("file://") || url.startsWith("/")) {
            String src = HkJsRuntime.getFilePath(url);
            if (!src.equals(target)) {
                copyFile(new File(src), new File(target));
            }
            listener.onSuccess(target);
            return;
        }
        Request.Builder rb = new Request.Builder().url(url);
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
        }
        HkHttp.clientFor(url).newCall(rb.build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "download", e);
                listener.onFailure(500, e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        listener.onFailure(r.code(), r.message());
                        return;
                    }
                    writeFile(target, r.body().byteStream());
                    listener.onSuccess(target);
                } catch (Exception e) {
                    Log.e(TAG, "download", e);
                    listener.onFailure(500, e.getMessage());
                }
            }
        });
    }

    /**
     * 8.83 CodeUtil.downloadSync(url, savePath, headers) 语义移植（smali 401-595）。
     *
     * @throws Exception 下载失败时抛出（8.83 同款 throws Exception）
     */
    public static void downloadSync(String url, String savePath, Map<String, String> headers) throws Exception {
        if (url.startsWith("hiker://files/") || url.startsWith("file://") || url.startsWith("/")) {
            String src = HkJsRuntime.getFilePath(url);
            if (!src.equals(savePath)) {
                copyFile(new File(src), new File(savePath));
            }
            return;
        }
        Request.Builder rb = new Request.Builder().url(url);
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
        }
        try (Response r = HkHttp.clientFor(url).newCall(rb.build()).execute()) {
            if (r.body() == null) throw new Exception("empty body");
            writeFile(savePath, r.body().byteStream());
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    // ---- 8.83 私有方法移植 ----

    /** 8.83 dealFileByHiker：hiker://assets/ 走资源文件，其余 hiker:// 走规则查询。 */
    private static void dealFileByHiker(String url, OnCodeGetListener listener) {
        if (url.startsWith("hiker://assets/")) {
            listener.onSuccess(HkHikerRuleUtil.getAssetsFileByHiker(url));
            return;
        }
        HkHikerRuleUtil.getRulesByHiker(url, listener);
    }

    /** 8.83 dealFileByPath：file:// 去前缀读本地文件。 */
    private static void dealFileByPath(String url, OnCodeGetListener listener) {
        String path = url.replace("file://", "");
        File f = new File(path);
        if (f.exists()) {
            listener.onSuccess(readFile(f));
        } else {
            listener.onFailure(404, path + "文件不存在");
        }
    }

    // ---- 小工具 ----

    private static String clearTag(String url) {
        // 8.83 UrlDetector.clearTag 原样（HkParserKit 逐行移植版）
        return url == null ? null : HkParserKit.clearTag(url);
    }

    private static String trimBlanks(String s) {
        return s == null ? "" : s.trim();
    }

    private static String readFile(File f) {
        try {
            byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
            return new String(b, Charset.forName("UTF-8"));
        } catch (Exception e) {
            Log.e(TAG, "readFile", e);
            return "";
        }
    }

    private static void copyFile(File src, File dst) {
        try {
            if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
            try (InputStream in = new java.io.FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "copyFile", e);
        }
    }

    private static void writeFile(String path, InputStream in) throws IOException {
        File dst = new File(path);
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
    }
}

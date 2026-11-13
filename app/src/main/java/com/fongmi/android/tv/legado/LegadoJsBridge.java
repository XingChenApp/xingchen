package com.fongmi.android.tv.legado;
import com.fongmi.android.tv.utils.ToastUtil;

import android.util.Log;
import android.widget.Toast;

import com.fongmi.android.tv.App;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Legado {@code java.*} 桥（阶段一高频子集）。
 * <p>
 * 已实现：ajax/get/post、getString/getStringList/getElement、put/get、
 * base64Encode/base64Decode、md5Encode/md5Encode16、createSymmetricCrypto、
 * log/toast。长尾（webView/startBrowser/getVerificationCode/downloadFile/
 * unzipFile/readTxtFile/importScript 等）调用时报源级错，不崩壳。
 * <p>
 * 沙箱：ClassShutter 拉黑危险类；JS 求值走独立线程＋20 秒超时熔断，
 * 不污染 HkJsRuntime 的全局 Rhino 状态。
 */
public class LegadoJsBridge {

    private static final String TAG = "legado";
    private static final long JS_TIMEOUT_MS = 20000;

    private static final ExecutorService JS_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "legado-js");
        t.setDaemon(true);
        return t;
    });

    /**
     * 死循环熔断（低-18）：Rhino 解释执行不响应线程中断，此前 Future.cancel(true)
     * 杀不掉跑飞的 JS 线程，cached pool 被一个个占满后新求值全排队假死。
     * 改指令计数观察：每 10000 条指令查一次本线程截止时间，超期直接抛 Error 打断求值。
     */
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

    private static class DeadlineContextFactory extends org.mozilla.javascript.ContextFactory {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setOptimizationLevel(-1);
            cx.setInstructionObserverThreshold(10000);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Long deadline = DEADLINE.get();
            if (deadline != null && System.currentTimeMillis() > deadline) {
                throw new Error("legado js deadline exceeded");
            }
        }
    }

    private static final DeadlineContextFactory FACTORY = new DeadlineContextFactory();

    private final LegadoRuleEvaluator.Ctx ctx;

    public LegadoJsBridge(LegadoRuleEvaluator.Ctx ctx) {
        this.ctx = ctx;
    }

    // ================= 求值入口 =================

    public Object eval(String code) {
        Future<Object> f = JS_POOL.submit(() -> evalInternal(code));
        try {
            return f.get(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            f.cancel(true);
            throw new LegadoException("JS 执行超时（20s 熔断）");
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable t) {
            throw toRuleException(t, code);
        }
    }

    private Object evalInternal(String code) {
        DEADLINE.set(System.currentTimeMillis() + JS_TIMEOUT_MS);
        try {
            return FACTORY.call(cx -> {
                cx.setLanguageVersion(200);
                cx.setClassShutter(new SandboxShutter());
                ScriptableObject scope = cx.initStandardObjects();
                ScriptableObject.putProperty(scope, "java", Context.javaToJS(this, scope));
                ScriptableObject.putProperty(scope, "baseUrl", ctx.baseUrl);
                ScriptableObject.putProperty(scope, "result", Context.javaToJS(ctx.rawText, scope));
                ScriptableObject.putProperty(scope, "book", Context.javaToJS(new HashMap<>(ctx.book), scope));
                ScriptableObject.putProperty(scope, "chapter", Context.javaToJS(new HashMap<>(ctx.chapter), scope));
                ScriptableObject.putProperty(scope, "source", Context.javaToJS(new HashMap<>(ctx.source), scope));
                ScriptableObject.putProperty(scope, "cookie", Context.javaToJS(new HashMap<>(ctx.cookie), scope));
                ScriptableObject.putProperty(scope, "cache", Context.javaToJS(new HashMap<>(ctx.vars), scope));
                ScriptableObject.putProperty(scope, "title", ctx.title);
                Object r = cx.evaluateString(scope, code, "legado", 1, null);
                return convert(r);
            });
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable t) {
            throw toRuleException(t, code);
        } finally {
            DEADLINE.remove();
        }
    }

    private Object convert(Object r) {
        if (r == null || r == Context.getUndefinedValue()) return "";
        // Java 方法返回值常被包成 NativeJavaObject，先解包
        if (r instanceof org.mozilla.javascript.NativeJavaObject) {
            r = ((org.mozilla.javascript.NativeJavaObject) r).unwrap();
            if (r == null) return "";
        }
        if (r instanceof org.mozilla.javascript.NativeJavaArray) {
            Object unwrapped = ((org.mozilla.javascript.NativeJavaArray) r).unwrap();
            if (unwrapped instanceof Object[]) {
                java.util.List<String> out = new java.util.ArrayList<>();
                for (Object e : (Object[]) unwrapped) out.add(String.valueOf(e));
                return out;
            }
        }
        List<String> list = LegadoRuleEvaluator.toStringList(r);
        if (list.size() == 1) {
            String s = String.valueOf(r);
            // NativeArray 单元素也按列表走，保持调用方一致
            if (r.getClass().getName().contains("NativeArray")) return list;
            return s;
        }
        if (list.size() > 1 || r.getClass().getName().contains("NativeArray")) return list;
        return String.valueOf(r);
    }

    private LegadoException toRuleException(Throwable t, String code) {
        String msg = t.getMessage();
        if (msg != null && msg.contains("Cannot find function")) {
            String fn = msg.replaceAll(".*function ([^ ]+).*", "$1");
            return new LegadoException("桥函数 java." + fn + " 暂未实现（阶段一），该源暂不支持");
        }
        String m = msg == null ? t.getClass().getSimpleName() : msg;
        if (m.length() > 200) m = m.substring(0, 200);
        return new LegadoException("JS 求值失败: " + m, t);
    }

    private static class SandboxShutter implements ClassShutter {
        private static final String[] BLOCKED = {
                "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.System",
                "java.lang.Class", "java.lang.ClassLoader", "java.lang.Thread",
                "java.io.File", "java.nio.file.Files", "java.net.Socket", "java.net.URL",
                "java.net.HttpURLConnection", "java.lang.reflect"
        };

        @Override
        public boolean visibleToScripts(String fullClassName) {
            for (String b : BLOCKED) {
                if (fullClassName.equals(b) || fullClassName.startsWith(b + ".")) return false;
            }
            return true;
        }
    }

    // ================= 网络 =================

    private String fetchText(String url, String method, byte[] body, Map<String, String> headers) {
        try {
            LegadoHttp.Result r = ctx.http.fetch(url, method, headers, body);
            String charset = detectCharset(r, ctx.charset);
            String text = new String(r.body, charset);
            ctx.cookie.putAll(r.cookies);
            return text;
        } catch (LegadoException e) {
            throw e;
        } catch (Throwable t) {
            throw new LegadoException("网络请求失败: " + abbrev(url) + "（" + t.getMessage() + "）");
        }
    }

    public String ajax(String url) {
        return fetchText(absUrl(url), "GET", null, null);
    }

    public String ajax(String url, String data) {
        byte[] body = data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8);
        return fetchText(absUrl(url), "POST", body, null);
    }

    /**
     * 阶段一有意合并：官方文档里 {@code get} 既出现在网络组又出现在变量组，
     * 签名相同无法共存。按参数形态分流：http 开头走网络 GET，否则走变量读取。
     */
    public String get(String keyOrUrl) {
        if (keyOrUrl != null && (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://"))) {
            return ajax(keyOrUrl);
        }
        String v = ctx.vars.get(keyOrUrl);
        return v == null ? "" : v;
    }

    public String get(String url, String params) {
        String u = absUrl(url);
        if (params != null && !params.isEmpty()) {
            u += (u.contains("?") ? "&" : "?") + params.replaceAll("^\\{|\\}$", "").replaceAll("\"", "").replace(":", "=").replace(",", "&");
        }
        return fetchText(u, "GET", null, null);
    }

    public String post(String url, String body) {
        return ajax(url, body);
    }

    private String absUrl(String url) {
        if (url == null || url.isEmpty()) throw new LegadoException("请求地址为空");
        return LegadoRuleEvaluator.absUrl(ctx.baseUrl, url);
    }

    private static String detectCharset(LegadoHttp.Result r, String fallback) {
        String ct = r.contentType == null ? "" : r.contentType.toLowerCase();
        int i = ct.indexOf("charset=");
        if (i >= 0) {
            String cs = ct.substring(i + 8).split("[; ]")[0].trim().replace("\"", "");
            if (!cs.isEmpty()) return normalize(cs);
        }
        return normalize(fallback);
    }

    private static String normalize(String cs) {
        if (cs == null) return "UTF-8";
        String c = cs.trim().toLowerCase();
        if (c.contains("gbk") || c.contains("gb2312") || c.contains("gb18030")) return "GBK";
        if (c.contains("big5")) return "Big5";
        if (c.contains("utf")) return "UTF-8";
        return "UTF-8";
    }

    // ================= 规则递归 =================

    public String getString(String rule) {
        return LegadoRuleEvaluator.str(ctx.root, ctx, rule, false);
    }

    public List<String> getStringList(String rule) {
        return LegadoRuleEvaluator.strList(ctx.root, ctx, rule);
    }

    /** 阶段一语义简化：返回首个命中元素的外层 HTML（JS 内可再正则处理）。 */
    public String getElement(String rule) {
        List<LegadoRuleEvaluator.Sel> els = LegadoRuleEvaluator.elements(ctx, rule);
        if (els.isEmpty()) return "";
        LegadoRuleEvaluator.Sel s = els.get(0);
        if (s instanceof LegadoRuleEvaluator.HtmlSel) {
            return ((LegadoRuleEvaluator.HtmlSel) s).el.outerHtml();
        }
        return String.valueOf(((LegadoRuleEvaluator.JsonSel) s).node);
    }

    // ================= 变量 =================

    public void put(String key, String value) {
        ctx.vars.put(key, value == null ? "" : value);
    }

    // ================= 编解码 =================

    public String base64Encode(String s) {
        if (s == null) return "";
        return android.util.Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
    }

    public String base64Decode(String s) {
        if (s == null) return "";
        try {
            return new String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new LegadoException("base64 解码失败");
        }
    }

    public String md5Encode(String s) {
        return md5(s, false);
    }

    public String md5Encode16(String s) {
        return md5(s, true);
    }

    private String md5(String s, boolean half) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            String full = sb.toString();
            return half ? full.substring(8, 24) : full;
        } catch (Throwable t) {
            throw new LegadoException("md5 失败");
        }
    }

    // ================= 对称加密 =================

    public Crypto createSymmetricCrypto(String transformation, String key) {
        return new Crypto(transformation, key, null);
    }

    public Crypto createSymmetricCrypto(String transformation, String key, String iv) {
        return new Crypto(transformation, key, iv);
    }

    public static class Crypto {
        private final String transformation;
        private final byte[] key;
        private final byte[] iv;

        public Crypto(String transformation, String key, String iv) {
            this.transformation = transformation == null ? "AES/CBC/PKCS5Padding" : transformation;
            this.key = fit((key == null ? "" : key).getBytes(StandardCharsets.UTF_8), keyLen(this.transformation));
            this.iv = fit((iv == null ? "" : iv).getBytes(StandardCharsets.UTF_8), 16);
        }

        private static int keyLen(String t) {
            String u = t.toUpperCase();
            if (u.contains("DESEDE") || u.contains("TRIPLEDES")) return 24;
            if (u.startsWith("DES")) return 8;
            return 16;
        }

        private static byte[] fit(byte[] src, int len) {
            byte[] out = new byte[len];
            System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
            return out;
        }

        private String algorithm() {
            String u = transformation.toUpperCase();
            if (u.contains("DESEDE") || u.contains("TRIPLEDES")) return "DESede";
            if (u.startsWith("DES")) return "DES";
            return "AES";
        }

        private Cipher cipher(int mode) throws Throwable {
            Cipher c = Cipher.getInstance(transformation);
            SecretKeySpec ks = new SecretKeySpec(key, algorithm());
            if (transformation.toUpperCase().contains("ECB")) c.init(mode, ks);
            else c.init(mode, ks, new IvParameterSpec(iv));
            return c;
        }

        public String encryptToBase64(String plain) {
            try {
                byte[] e = cipher(Cipher.ENCRYPT_MODE).doFinal(plain.getBytes(StandardCharsets.UTF_8));
                return android.util.Base64.encodeToString(e, android.util.Base64.NO_WRAP);
            } catch (Throwable t) {
                throw new LegadoException("加密失败: " + t.getMessage());
            }
        }

        public String decryptBase64ToString(String base64) {
            try {
                byte[] e = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                return new String(cipher(Cipher.DECRYPT_MODE).doFinal(e), StandardCharsets.UTF_8);
            } catch (Throwable t) {
                throw new LegadoException("解密失败: " + t.getMessage());
            }
        }

        public String encryptToHex(String plain) {
            try {
                byte[] e = cipher(Cipher.ENCRYPT_MODE).doFinal(plain.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : e) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Throwable t) {
                throw new LegadoException("加密失败: " + t.getMessage());
            }
        }

        public String decryptHexToString(String hex) {
            try {
                int n = hex.length() / 2;
                byte[] e = new byte[n];
                for (int i = 0; i < n; i++) e[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                return new String(cipher(Cipher.DECRYPT_MODE).doFinal(e), StandardCharsets.UTF_8);
            } catch (Throwable t) {
                throw new LegadoException("解密失败: " + t.getMessage());
            }
        }
    }

    // ================= 日志 =================

    public void log(String msg) {
        Log.d(TAG, msg == null ? "" : msg);
    }

    public void toast(String msg) {
        try {
            App app = App.get();
            if (app != null) {
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> ToastUtil.show(app, msg == null ? "" : msg, Toast.LENGTH_SHORT));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String abbrev(String s) {
        if (s == null) return "";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}

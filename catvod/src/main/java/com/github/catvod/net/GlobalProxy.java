package com.github.catvod.net;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.Route;

/**
 * 全局 HTTP/SOCKS5 代理。
 * <p>
 * 开启后：全部 OkHttp 流量（点播/封面/播放器）与 Python requests 流量都走该代理。
 * 与“走壳代理”(OkProxySelector 按域名规则)互斥：全局代理开启时优先接管，
 * 本地回环地址（localhost/127.x）永远直连，避免本机推送等功能被代理打断。
 */
public class GlobalProxy {

    public static final int TYPE_NONE = 0;
    public static final int TYPE_HTTP = 1;
    public static final int TYPE_SOCKS5 = 2;

    public static class Config {
        public int type = TYPE_NONE;
        public String host = "";
        public int port = 0;
        public String user = "";
        public String pass = "";

        public boolean enabled() {
            return type != TYPE_NONE && !TextUtils.isEmpty(host) && port > 0 && port <= 65535;
        }

        public boolean hasAuth() {
            return !TextUtils.isEmpty(user);
        }

        public Proxy toProxy() {
            if (!enabled()) return null;
            Proxy.Type t = type == TYPE_SOCKS5 ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            return new Proxy(t, new InetSocketAddress(host, port));
        }

        /** http://user:pass@host:port  或  socks5://user:pass@host:port */
        public String toUrl() {
            if (!enabled()) return "";
            StringBuilder sb = new StringBuilder(type == TYPE_SOCKS5 ? "socks5://" : "http://");
            if (hasAuth()) sb.append(enc(user)).append(':').append(enc(pass)).append('@');
            return sb.append(host).append(':').append(port).toString();
        }

        private static String enc(String s) {
            try {
                return java.net.URLEncoder.encode(s, "UTF-8");
            } catch (Exception e) {
                return s;
            }
        }
    }

    private static volatile Config current = new Config();

    public static void set(Config config) {
        current = config != null ? config : new Config();
    }

    public static Config get() {
        return current;
    }

    public static boolean isEnabled() {
        return current.enabled();
    }

    /**
     * 包装走壳代理的 selector：全局代理开启时接管（回环地址除外），
     * 关闭时原样透传给走壳代理，默认行为零变化。
     */
    public static ProxySelector wrapSelector(ProxySelector shell) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                Config c = current;
                if (c.enabled()) {
                    if (isLoopback(uri)) return List.of(Proxy.NO_PROXY);
                    Proxy p = c.toProxy();
                    if (p != null) return List.of(p);
                }
                return shell.select(uri);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                shell.connectFailed(uri, sa, ioe);
            }
        };
    }

    /**
     * 包装走壳代理的 authenticator：全局代理收到 407 且配了账号时先用全局账号，
     * 其余情况透传给走壳代理。
     */
    public static okhttp3.Authenticator wrapAuthenticator(okhttp3.Authenticator shell) {
        return (route, response) -> {
            Config c = current;
            if (c.enabled() && c.hasAuth() && response.code() == 407 && isOurProxy(route, c)) {
                String credential = Credentials.basic(c.user, c.pass);
                if (credential.equals(response.request().header("Proxy-Authorization"))) return null;
                return response.request().newBuilder().header("Proxy-Authorization", credential).build();
            }
            return shell.authenticate(route, response);
        };
    }

    private static boolean isOurProxy(@Nullable Route route, Config c) {
        if (route == null || route.proxy() == null) return false;
        SocketAddress addr = route.proxy().address();
        return addr instanceof InetSocketAddress && ((InetSocketAddress) addr).getHostString().equalsIgnoreCase(c.host);
    }

    private static boolean isLoopback(URI uri) {
        try {
            String host = uri.getHost();
            if (host == null) return false;
            String h = host.toLowerCase();
            return h.equals("localhost") || h.equals("::1") || h.equals("[::1]") || h.startsWith("127.") || h.equals("0.0.0.0");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 把代理配置同步进 Python 解释器。requests 会自动读取
     * HTTP_PROXY / HTTPS_PROXY / ALL_PROXY / NO_PROXY 环境变量。
     * <ul>
     * <li>进程级：android.system.Os.setenv，保证 Python 尚未启动时后续启动能读到；</li>
     * <li>已启动：直接改 os.environ 映射（直接调 putenv 不会更新 os.environ，必须走映射赋值）。</li>
     * </ul>
     */
    public static void syncPython() {
        Config c = current;
        boolean on = c.enabled();
        String url = on ? c.toUrl() : "";
        String[] names = {"HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY"};
        String[] values = {url, url, url, on ? "localhost,127.0.0.1,::1" : ""};
        for (int i = 0; i < names.length; i++) setProcEnv(names[i], values[i]);
        syncPythonLive(names, values);
    }

    private static void setProcEnv(String name, String value) {
        try {
            android.system.Os.setenv(name, value, true);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void syncPythonLive(String[] names, String[] values) {
        try {
            Class<?> pythonClass = Class.forName("com.chaquo.python.Python");
            boolean started = (Boolean) pythonClass.getMethod("isStarted").invoke(null);
            if (!started) return;
            Object python = pythonClass.getMethod("getInstance").invoke(null);
            Class<?> pyObjectClass = Class.forName("com.chaquo.python.PyObject");
            java.lang.reflect.Method getModule = pythonClass.getMethod("getModule", String.class);
            java.lang.reflect.Method getAttr = pyObjectClass.getMethod("get", String.class);
            java.lang.reflect.Method callAttr = pyObjectClass.getMethod("callAttr", String.class, Object[].class);
            Object os = getModule.invoke(python, "os");
            Object environ = getAttr.invoke(os, "environ");
            for (int i = 0; i < names.length; i++) {
                if (values[i] == null || values[i].isEmpty()) {
                    callAttr.invoke(environ, new Object[]{"pop", new Object[]{names[i], null}});
                } else {
                    callAttr.invoke(environ, new Object[]{"__setitem__", new Object[]{names[i], values[i]}});
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

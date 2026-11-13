package com.fongmi.android.tv.clash;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 单节点分享链接 → mihomo proxies 条目（Map，可直接喂给 MiniYaml.dump）。
 * <p>
 * 覆盖：vless / vmess / ss / ssr / trojan / hysteria2(hy2) / tuic /
 * wireguard(尽力) / socks5(socks) / http(s)。返回 null 表示不支持。
 * <p>
 * 字段名按 mihomo 文档口径；拿不准的冷门参数直接丢弃不进 map，
 * 宁可少一个参数也不编一个错字段。
 */
public final class LinkParser {

    private LinkParser() {
    }

    /** 解析失败返回 null。 */
    public static Map<String, Object> parse(String link) {
        if (link == null) return null;
        link = link.trim();
        int p = link.indexOf("://");
        if (p <= 0) return null;
        String scheme = link.substring(0, p).toLowerCase(Locale.ROOT);
        try {
            switch (scheme) {
                case "vless":
                    return parseVless(link);
                case "vmess":
                    return parseVmess(link);
                case "ss":
                    return parseSs(link);
                case "ssr":
                    return parseSsr(link);
                case "trojan":
                    return parseTrojan(link);
                case "hysteria2":
                case "hy2":
                    return parseHysteria2(link);
                case "tuic":
                    return parseTuic(link);
                case "wireguard":
                case "wg":
                    return parseWireguard(link);
                case "socks5":
                case "socks":
                    return parseSocks(link);
                case "http":
                case "https":
                    return parseHttp(link, scheme);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 通用小件 ----------------

    static class Parts {
        String scheme;
        String userinfo = "";
        String host = "";
        int port = -1;
        Map<String, String> query = new LinkedHashMap<>();
        String fragment = "";
    }

    /** 容忍未编码中文的手工拆分（java.net.URI 遇到裸中文会炸）。 */
    static Parts split(String link) {
        Parts r = new Parts();
        int p = link.indexOf("://");
        r.scheme = link.substring(0, p).toLowerCase(Locale.ROOT);
        String rest = link.substring(p + 3);
        int hi = rest.lastIndexOf('#');
        if (hi >= 0) {
            r.fragment = decode(rest.substring(hi + 1));
            rest = rest.substring(0, hi);
        }
        int qi = rest.indexOf('?');
        String main = rest;
        if (qi >= 0) {
            main = rest.substring(0, qi);
            r.query = parseQuery(rest.substring(qi + 1));
        }
        int ai = main.lastIndexOf('@');
        String hostport = main;
        if (ai >= 0) {
            r.userinfo = main.substring(0, ai);
            hostport = main.substring(ai + 1);
        }
        int ci = hostport.lastIndexOf(':');
        if (ci >= 0) {
            r.host = hostport.substring(0, ci);
            try {
                r.port = Integer.parseInt(hostport.substring(ci + 1));
            } catch (NumberFormatException ignored) {
                r.port = -1;
            }
        } else {
            r.host = hostport;
        }
        if (r.host.startsWith("[") && r.host.endsWith("]")) r.host = r.host.substring(1, r.host.length() - 1);
        return r;
    }

    static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String kv : q.split("&")) {
            if (kv.isEmpty()) continue;
            int e = kv.indexOf('=');
            if (e < 0) m.put(decode(kv), "");
            else m.put(decode(kv.substring(0, e)), decode(kv.substring(e + 1)));
        }
        return m;
    }

    static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    static String b64(String s) {
        if (s == null) return "";
        String t = s.trim().replace('-', '+').replace('_', '/');
        while (t.length() % 4 != 0) t += "=";
        try {
            return new String(Base64.getDecoder().decode(t), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    static boolean isB64(String s) {
        return s != null && s.matches("[A-Za-z0-9+/_=-]+") && s.length() % 4 != 1;
    }

    static int intOf(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    static boolean boolOf(Map<String, String> q, String... keys) {
        for (String k : keys) {
            String v = q.get(k);
            if (v != null) return "1".equals(v) || "true".equalsIgnoreCase(v);
        }
        return false;
    }

    static String q(Map<String, String> m, String... keys) {
        for (String k : keys) {
            String v = m.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    static String nameOf(Parts r, String fallback) {
        if (!r.fragment.isEmpty()) return r.fragment;
        return fallback == null || fallback.isEmpty() ? r.host : fallback;
    }

    static void putIf(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isEmpty()) m.put(k, v);
    }

    static void putIf(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }

    static List<String> listOf(String csv) {
        List<String> l = new ArrayList<>();
        for (String s : csv.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    // ---------------- vless ----------------
    // vless://uuid@host:port?security=reality&encryption=none&pbk=..&fp=..&sni=..&sid=..&spx=..&type=ws&path=..&host=..&flow=..#name

    private static Map<String, Object> parseVless(String link) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "vless"));
        m.put("type", "vless");
        m.put("server", r.host);
        m.put("port", r.port > 0 ? r.port : 443);
        m.put("uuid", r.userinfo);
        String network = q(r.query, "type", "net");
        if (!network.isEmpty() && !"tcp".equals(network)) m.put("network", network);
        String security = q(r.query, "security");
        if ("tls".equals(security) || "reality".equals(security)) m.put("tls", true);
        putIf(m, "servername", q(r.query, "sni", "serverName"));
        String fp = q(r.query, "fp", "fingerprint");
        if (!fp.isEmpty()) m.put("client-fingerprint", fp);
        putIf(m, "flow", q(r.query, "flow"));
        if ("reality".equals(security)) {
            Map<String, Object> ro = new LinkedHashMap<>();
            putIf(ro, "public-key", q(r.query, "pbk", "publicKey"));
            putIf(ro, "short-id", q(r.query, "sid", "shortId"));
            putIf(ro, "spider-x", q(r.query, "spx", "spiderX"));
            if (!ro.isEmpty()) m.put("reality-opts", ro);
        }
        if ("ws".equals(network)) {
            Map<String, Object> ws = new LinkedHashMap<>();
            putIf(ws, "path", q(r.query, "path"));
            String wsHost = q(r.query, "host", "obfs-host", "ws-host");
            if (!wsHost.isEmpty()) {
                Map<String, Object> headers = new LinkedHashMap<>();
                headers.put("Host", wsHost);
                ws.put("headers", headers);
            }
            if (!ws.isEmpty()) m.put("ws-opts", ws);
        } else if ("grpc".equals(network)) {
            Map<String, Object> grpc = new LinkedHashMap<>();
            putIf(grpc, "grpc-service-name", q(r.query, "serviceName", "service_name"));
            if (!grpc.isEmpty()) m.put("grpc-opts", grpc);
        }
        String alpn = q(r.query, "alpn");
        if (!alpn.isEmpty()) m.put("alpn", listOf(alpn));
        m.put("udp", true);
        return m;
    }

    // ---------------- vmess ----------------
    // vmess://base64({"ps":"name","add":"host","port":"443","id":"uuid","aid":"0",
    //   "net":"ws","type":"none","host":"","path":"","tls":"tls","sni":"","alpn":"","fp":""})

    private static Map<String, Object> parseVmess(String link) {
        String body = link.substring(link.indexOf("://") + 3);
        int hi = body.indexOf('#');
        if (hi >= 0) body = body.substring(0, hi);
        String json = b64(body.trim());
        if (json.isEmpty() || !json.startsWith("{")) return null;
        Map<String, String> j = flatJson(json);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", firstNonEmpty(j.get("ps"), j.get("add"), "vmess"));
        m.put("type", "vmess");
        m.put("server", firstNonEmpty(j.get("add"), ""));
        m.put("port", intOf(j.get("port"), 443));
        m.put("uuid", firstNonEmpty(j.get("id"), ""));
        m.put("alterId", intOf(j.get("aid"), 0));
        putIf(m, "cipher", j.get("scy"));
        String tls = j.get("tls");
        if ("tls".equals(tls)) m.put("tls", true);
        putIf(m, "servername", j.get("sni"));
        String net = firstNonEmpty(j.get("net"), "");
        if (!net.isEmpty() && !"tcp".equals(net)) m.put("network", net);
        putIf(m, "client-fingerprint", j.get("fp"));
        String alpn = j.get("alpn");
        if (alpn != null && !alpn.isEmpty()) m.put("alpn", listOf(alpn));
        if ("ws".equals(net)) {
            Map<String, Object> ws = new LinkedHashMap<>();
            putIf(ws, "path", j.get("path"));
            String host = j.get("host");
            if (host != null && !host.isEmpty()) {
                Map<String, Object> headers = new LinkedHashMap<>();
                headers.put("Host", host);
                ws.put("headers", headers);
            }
            if (!ws.isEmpty()) m.put("ws-opts", ws);
        } else if ("grpc".equals(net)) {
            Map<String, Object> grpc = new LinkedHashMap<>();
            putIf(grpc, "grpc-service-name", j.get("path"));
            if (!grpc.isEmpty()) m.put("grpc-opts", grpc);
        } else if ("h2".equals(net) || "http".equals(net)) {
            Map<String, Object> http = new LinkedHashMap<>();
            putIf(http, "path", j.get("path"));
            String host = j.get("host");
            if (host != null && !host.isEmpty()) {
                Map<String, Object> headers = new LinkedHashMap<>();
                headers.put("Host", host);
                http.put("headers", headers);
            }
            if (!http.isEmpty()) m.put("http-opts", http);
        }
        m.put("udp", true);
        return m;
    }

    /** 只够 vmess JSON 的扁平解析：顶层 string/number/bool 值。 */
    static Map<String, String> flatJson(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        String t = json.trim();
        if (t.startsWith("{")) t = t.substring(1);
        if (t.endsWith("}")) t = t.substring(0, t.length() - 1);
        int i = 0;
        while (i < t.length()) {
            while (i < t.length() && (t.charAt(i) == ' ' || t.charAt(i) == ',' || t.charAt(i) == '\n' || t.charAt(i) == '\r' || t.charAt(i) == '\t')) i++;
            if (i >= t.length() || t.charAt(i) != '"') break;
            int ke = t.indexOf('"', i + 1);
            if (ke < 0) break;
            String key = t.substring(i + 1, ke);
            int c = t.indexOf(':', ke + 1);
            if (c < 0) break;
            i = c + 1;
            while (i < t.length() && t.charAt(i) == ' ') i++;
            String val;
            if (i < t.length() && t.charAt(i) == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < t.length()) {
                    char ch = t.charAt(i);
                    if (ch == '\\' && i + 1 < t.length()) {
                        char n = t.charAt(i + 1);
                        sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
                        i += 2;
                    } else if (ch == '"') {
                        i++;
                        break;
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }
                val = sb.toString();
            } else {
                int e = i;
                while (e < t.length() && t.charAt(e) != ',' && t.charAt(e) != '}') e++;
                val = t.substring(i, e).trim();
                i = e;
            }
            m.put(key, val);
        }
        return m;
    }

    private static String firstNonEmpty(String... ss) {
        for (String s : ss) if (s != null && !s.isEmpty()) return s;
        return "";
    }

    // ---------------- ss ----------------
    // 写法1: ss://method:password@host:port#name
    // 写法2: ss://base64(method:password)@host:port#name
    // 写法3: ss://base64(method:password@host:port)#name  (可带 /?plugin=...)

    private static Map<String, Object> parseSs(String link) {
        String body = link.substring(link.indexOf("://") + 3);
        String frag = "";
        int hi = body.lastIndexOf('#');
        if (hi >= 0) {
            frag = decode(body.substring(hi + 1));
            body = body.substring(0, hi);
        }
        String plugin = "";
        int qi = body.indexOf('?');
        String pluginQuery = "";
        if (qi >= 0) {
            pluginQuery = body.substring(qi + 1);
            body = body.substring(0, qi);
        }
        if (body.endsWith("/")) body = body.substring(0, body.length() - 1);
        String method, password, host;
        int port;
        int ai = body.lastIndexOf('@');
        if (ai >= 0) {
            String userinfo = body.substring(0, ai);
            String hostport = body.substring(ai + 1);
            if (!userinfo.contains(":")) {
                String dec = b64(userinfo);
                if (dec.isEmpty()) return null;
                userinfo = dec;
            }
            int ci = userinfo.indexOf(':');
            if (ci < 0) return null;
            method = userinfo.substring(0, ci);
            password = userinfo.substring(ci + 1);
            int cp = hostport.lastIndexOf(':');
            if (cp < 0) return null;
            host = hostport.substring(0, cp);
            port = intOf(hostport.substring(cp + 1), -1);
        } else {
            String dec = b64(body);
            if (dec.isEmpty()) return null;
            int at = dec.lastIndexOf('@');
            if (at < 0) return null;
            String userinfo = dec.substring(0, at);
            String hostport = dec.substring(at + 1);
            int ci = userinfo.indexOf(':');
            if (ci < 0) return null;
            method = userinfo.substring(0, ci);
            password = userinfo.substring(ci + 1);
            int cp = hostport.lastIndexOf(':');
            if (cp < 0) return null;
            host = hostport.substring(0, cp);
            port = intOf(hostport.substring(cp + 1), -1);
        }
        if (port <= 0 || host.isEmpty()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", frag.isEmpty() ? host : frag);
        m.put("type", "ss");
        m.put("server", host);
        m.put("port", port);
        m.put("cipher", method);
        m.put("password", password);
        if (!pluginQuery.isEmpty()) {
            Map<String, String> pq = parseQuery(pluginQuery);
            String pluginStr = pq.get("plugin");
            if (pluginStr != null && !pluginStr.isEmpty()) {
                int sc = pluginStr.indexOf(';');
                if (sc >= 0) {
                    m.put("plugin", pluginStr.substring(0, sc));
                    Map<String, Object> po = new LinkedHashMap<>();
                    for (String kv : pluginStr.substring(sc + 1).split(";")) {
                        int e = kv.indexOf('=');
                        if (e > 0) po.put(kv.substring(0, e), kv.substring(e + 1));
                    }
                    if (!po.isEmpty()) m.put("plugin-opts", po);
                } else {
                    m.put("plugin", pluginStr);
                }
                plugin = pluginStr;
            }
        }
        if (!plugin.isEmpty()) {
            // 已在上面处理
        }
        m.put("udp", true);
        return m;
    }

    // ---------------- ssr ----------------
    // ssr://base64(host:port:protocol:method:obfs:base64pass/?obfsparam=&protoparam=&remarks=&group=)

    private static Map<String, Object> parseSsr(String link) {
        String body = link.substring(link.indexOf("://") + 3);
        String dec = b64(body);
        if (dec.isEmpty()) return null;
        String main = dec;
        Map<String, String> q = new LinkedHashMap<>();
        int qi = dec.indexOf('?');
        if (qi < 0) qi = dec.indexOf("/?");
        if (qi >= 0) {
            main = dec.substring(0, qi);
            String qs = dec.substring(qi + 1);
            if (qs.startsWith("/")) qs = qs.substring(1);
            q = parseQuery(qs);
        }
        String[] seg = main.split(":");
        if (seg.length < 6) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        String remarks = b64(q.get("remarks"));
        m.put("name", remarks.isEmpty() ? seg[0] : remarks);
        m.put("type", "ssr");
        m.put("server", seg[0]);
        m.put("port", intOf(seg[1], -1));
        m.put("protocol", seg[2]);
        m.put("cipher", seg[3]);
        m.put("obfs", seg[4]);
        m.put("password", b64(seg[5]));
        putIf(m, "obfs-param", b64(q.get("obfsparam")));
        putIf(m, "protocol-param", b64(q.get("protoparam")));
        m.put("udp", true);
        return m;
    }

    // ---------------- trojan ----------------
    // trojan://password@host:port?sni=..&alpn=..&allowInsecure=1&type=ws&path=..#name

    private static Map<String, Object> parseTrojan(String link) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "trojan"));
        m.put("type", "trojan");
        m.put("server", r.host);
        m.put("port", r.port > 0 ? r.port : 443);
        m.put("password", r.userinfo);
        putIf(m, "sni", q(r.query, "sni", "peer"));
        String alpn = q(r.query, "alpn");
        if (!alpn.isEmpty()) m.put("alpn", listOf(alpn));
        if (boolOf(r.query, "allowInsecure", "insecure", "skip-cert-verify")) m.put("skip-cert-verify", true);
        String network = q(r.query, "type", "net");
        if (!network.isEmpty() && !"tcp".equals(network)) m.put("network", network);
        if ("ws".equals(network)) {
            Map<String, Object> ws = new LinkedHashMap<>();
            putIf(ws, "path", q(r.query, "path"));
            String wsHost = q(r.query, "host");
            if (!wsHost.isEmpty()) {
                Map<String, Object> headers = new LinkedHashMap<>();
                headers.put("Host", wsHost);
                ws.put("headers", headers);
            }
            if (!ws.isEmpty()) m.put("ws-opts", ws);
        } else if ("grpc".equals(network)) {
            Map<String, Object> grpc = new LinkedHashMap<>();
            putIf(grpc, "grpc-service-name", q(r.query, "serviceName", "service_name"));
            if (!grpc.isEmpty()) m.put("grpc-opts", grpc);
        }
        m.put("udp", true);
        return m;
    }

    // ---------------- hysteria2 ----------------
    // hysteria2://password@host:port?obfs=salamander&obfs-password=..&sni=..&insecure=1#name

    private static Map<String, Object> parseHysteria2(String link) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "hysteria2"));
        m.put("type", "hysteria2");
        m.put("server", r.host);
        m.put("port", r.port > 0 ? r.port : 443);
        m.put("password", r.userinfo);
        putIf(m, "obfs", q(r.query, "obfs"));
        putIf(m, "obfs-password", q(r.query, "obfs-password", "obfs_password"));
        putIf(m, "sni", q(r.query, "sni", "peer"));
        if (boolOf(r.query, "insecure", "allowInsecure", "skip-cert-verify")) m.put("skip-cert-verify", true);
        putIf(m, "up", q(r.query, "up"));
        putIf(m, "down", q(r.query, "down"));
        return m;
    }

    // ---------------- tuic ----------------
    // tuic://uuid:password@host:port?congestion_controller=bbr&alpn=h3&sni=..&udp_relay_mode=native#name

    private static Map<String, Object> parseTuic(String link) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "tuic"));
        m.put("type", "tuic");
        m.put("server", r.host);
        m.put("port", r.port > 0 ? r.port : 443);
        String uuid = r.userinfo;
        String token = "";
        int ci = r.userinfo.indexOf(':');
        if (ci >= 0) {
            uuid = r.userinfo.substring(0, ci);
            token = r.userinfo.substring(ci + 1);
        }
        m.put("uuid", uuid);
        putIf(m, "password", token);
        putIf(m, "sni", q(r.query, "sni"));
        String alpn = q(r.query, "alpn");
        if (!alpn.isEmpty()) m.put("alpn", listOf(alpn));
        putIf(m, "congestion-controller", q(r.query, "congestion_controller", "congestion-controller"));
        putIf(m, "udp-relay-mode", q(r.query, "udp_relay_mode", "udp-relay-mode"));
        if (boolOf(r.query, "allow_insecure", "insecure", "skip-cert-verify")) m.put("skip-cert-verify", true);
        return m;
    }

    // ---------------- wireguard（尽力） ----------------
    // wireguard://privateKey@host:port?publickey=..&presharedkey=..&address=..&allowedips=..#name

    private static Map<String, Object> parseWireguard(String link) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "wireguard"));
        m.put("type", "wireguard");
        putIf(m, "private-key", decode(r.userinfo));
        putIf(m, "ip", q(r.query, "address", "ip"));
        putIf(m, "dns", q(r.query, "dns"));
        Map<String, Object> peer = new LinkedHashMap<>();
        peer.put("server", r.host);
        peer.put("port", r.port > 0 ? r.port : 51820);
        putIf(peer, "public-key", q(r.query, "publickey", "public-key"));
        putIf(peer, "pre-shared-key", q(r.query, "presharedkey", "pre-shared-key"));
        String allowed = q(r.query, "allowedips", "allowed-ips");
        peer.put("allowed-ips", allowed.isEmpty() ? listOf("0.0.0.0/0") : listOf(allowed));
        List<Object> peers = new ArrayList<>();
        peers.add(peer);
        m.put("peers", peers);
        m.put("udp", true);
        return m;
    }

    // ---------------- socks5 ----------------

    private static Map<String, Object> parseSocks(String link) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "socks5"));
        m.put("type", "socks5");
        m.put("server", r.host);
        m.put("port", r.port > 0 ? r.port : 1080);
        String user = r.userinfo;
        int ci = user.indexOf(':');
        if (ci >= 0) {
            m.put("username", user.substring(0, ci));
            m.put("password", user.substring(ci + 1));
        } else if (!user.isEmpty()) {
            m.put("username", user);
        }
        m.put("udp", true);
        return m;
    }

    // ---------------- http(s) ----------------

    private static Map<String, Object> parseHttp(String link, String scheme) {
        Parts r = split(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nameOf(r, "http"));
        m.put("type", "http");
        m.put("server", r.host);
        m.put("port", r.port > 0 ? r.port : ("https".equals(scheme) ? 443 : 8080));
        if ("https".equals(scheme)) m.put("tls", true);
        String user = r.userinfo;
        int ci = user.indexOf(':');
        if (ci >= 0) {
            m.put("username", user.substring(0, ci));
            m.put("password", user.substring(ci + 1));
        } else if (!user.isEmpty()) {
            m.put("username", user);
        }
        return m;
    }
}

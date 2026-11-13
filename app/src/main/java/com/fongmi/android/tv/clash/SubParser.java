package com.fongmi.android.tv.clash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅内容 → 节点草稿列表（只做解析与展示，不写盘；写盘走 NodeStore）。
 * <p>
 * 识别三种形态：
 * 1) base64 整包：解开后是按行分隔的分享链接；
 * 2) 明文多行：每行一条分享链接；
 * 3) Clash YAML：含 "proxies:" 段的配置，取其 proxies 列表。
 */
public final class SubParser {

    private SubParser() {
    }

    /** 最近一次解析的统计（供界面/日志定位丢节点丢在哪一步，不含任何节点内容）。 */
    public static int lastTotal = 0;
    public static int lastFailed = 0;
    public static String lastFailedSchemes = "";

    /** 单条解析结果。link 为空表示来自 YAML（无原始链接）。 */
    public static class NodeDraft {
        public String link = "";
        public String name = "";
        public String proto = "";
        public Map<String, Object> config = new LinkedHashMap<>();

        @Override
        public String toString() {
            return proto + " " + name;
        }
    }

    public static List<NodeDraft> parse(String content) {
        List<NodeDraft> out = new ArrayList<>();
        lastTotal = 0;
        lastFailed = 0;
        lastFailedSchemes = "";
        if (content == null) return out;
        String text = content.trim();
        if (text.isEmpty()) return out;
        if (looksLikeYaml(text)) {
            parseYaml(text, out);
            if (!out.isEmpty()) return out;
        }
        String links = text;
        if (looksLikeBase64Blob(text)) {
            String dec = LinkParser.b64(text);
            if (!dec.isEmpty()) links = dec;
        }
        java.util.Set<String> failed = new java.util.TreeSet<>();
        for (String line : links.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            lastTotal++;
            Map<String, Object> cfg = LinkParser.parse(line);
            if (cfg == null) {
                lastFailed++;
                int c = line.indexOf(':');
                failed.add(c > 0 ? line.substring(0, c) : "?");
                continue;
            }
            NodeDraft d = new NodeDraft();
            d.link = line;
            d.config = cfg;
            d.name = String.valueOf(cfg.get("name"));
            d.proto = String.valueOf(cfg.get("type"));
            out.add(d);
        }
        lastFailedSchemes = String.join(",", failed);
        return out;
    }

    private static boolean looksLikeYaml(String text) {
        String head = text.length() > 4000 ? text.substring(0, 4000) : text;
        return head.contains("proxies:") || head.contains("proxy-groups:");
    }

    private static boolean looksLikeBase64Blob(String text) {
        String one = text.replaceAll("\\s+", "");
        if (one.length() < 32) return false;
        if (!one.matches("[A-Za-z0-9+/_=-]+")) return false;
        return !one.contains("://");
    }

    @SuppressWarnings("unchecked")
    private static void parseYaml(String text, List<NodeDraft> out) {
        try {
            Map<String, Object> root = MiniYaml.loadMap(text);
            Object proxies = root.get("proxies");
            if (!(proxies instanceof List)) return;
            for (Object o : (List<Object>) proxies) {
                lastTotal++;
                if (!(o instanceof Map)) {
                    lastFailed++;
                    continue;
                }
                Map<String, Object> cfg = new LinkedHashMap<>((Map<String, Object>) o);
                Object name = cfg.get("name");
                Object type = cfg.get("type");
                if (name == null || type == null) {
                    lastFailed++;
                    continue;
                }
                NodeDraft d = new NodeDraft();
                d.link = "";
                d.config = cfg;
                d.name = String.valueOf(name);
                d.proto = String.valueOf(type);
                out.add(d);
            }
        } catch (Exception ignored) {
        }
    }
}

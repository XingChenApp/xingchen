package com.fongmi.android.tv.clash;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 YAML 子集读写：只够 mihomo config 生成与订阅里的 proxies 段解析。
 * <p>
 * 读支持：缩进式 map、"- " 开头的 list（含 list of map）、"key: value"、
 * "key:" 后跟嵌套块、# 注释、空行、单/双引号字符串；标量尝试转整数/布尔，
 * 其余按字符串保留。不支持：锚点、合并键、多行字面量（|/>)、行内流式写法。
 * <p>
 * 写支持：Map/List/字符串/数字/布尔的 2 空格缩进输出；需要时自动加引号。
 */
public final class MiniYaml {

    private MiniYaml() {
    }

    // ---------------- 读 ----------------

    public static Map<String, Object> loadMap(String yaml) {
        Object root = load(yaml);
        if (root instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) root;
            return m;
        }
        return new LinkedHashMap<>();
    }

    public static Object load(String yaml) {
        List<String> lines = new ArrayList<>();
        for (String raw : yaml.split("\n")) {
            String noComment = stripComment(raw);
            if (noComment.trim().isEmpty()) continue;
            lines.add(noComment);
        }
        if (lines.isEmpty()) return new LinkedHashMap<String, Object>();
        Parser p = new Parser(lines);
        return p.parseBlock(0).value;
    }

    private static String stripComment(String line) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '#' && !inSingle && !inDouble) {
                if (i == 0 || line.charAt(i - 1) == ' ') return line.substring(0, i);
            }
        }
        return line;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
    }

    private static Object parseScalar(String s) {
        s = s.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        if ("null".equalsIgnoreCase(s) || "~".equals(s)) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
        }
        return s;
    }

    private static class Result {
        Object value;
        int next;
    }

    private static class Parser {
        final List<String> lines;

        Parser(List<String> lines) {
            this.lines = lines;
        }

        Result parseBlock(int from) {
            Result r = new Result();
            String first = lines.get(from).trim();
            if (first.startsWith("- ") || first.equals("-")) {
                List<Object> list = new ArrayList<>();
                int i = from;
                int base = indentOf(lines.get(from));
                while (i < lines.size()) {
                    String line = lines.get(i);
                    int ind = indentOf(line);
                    if (ind != base) break;
                    String t = line.trim();
                    if (!t.startsWith("-")) break;
                    String rest = t.substring(1).trim();
                    if (rest.isEmpty()) {
                        Result sub = parseBlock(i + 1);
                        list.add(sub.value);
                        i = sub.next;
                    } else if (rest.contains(":")) {
                        int colon = rest.indexOf(':');
                        String k = rest.substring(0, colon).trim();
                        String v = rest.substring(colon + 1).trim();
                        Map<String, Object> m = new LinkedHashMap<>();
                        if (v.isEmpty()) {
                            if (i + 1 < lines.size() && indentOf(lines.get(i + 1)) > base) {
                                Result sub = parseBlock(i + 1);
                                m.put(k, sub.value);
                                i = sub.next;
                            } else {
                                m.put(k, null);
                                i++;
                            }
                        } else {
                            m.put(k, parseScalar(v));
                            if (i + 1 < lines.size() && indentOf(lines.get(i + 1)) > base) {
                                Result sub = parseBlock(i + 1);
                                Object subVal = sub.value;
                                if (subVal instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> sm = (Map<String, Object>) subVal;
                                    m.putAll(sm);
                                }
                                i = sub.next;
                            } else {
                                i++;
                            }
                        }
                        list.add(m);
                    } else {
                        list.add(parseScalar(rest));
                        i++;
                    }
                }
                r.value = list;
                r.next = i;
                return r;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            int i = from;
            int base = indentOf(lines.get(from));
            while (i < lines.size()) {
                String line = lines.get(i);
                int ind = indentOf(line);
                if (ind < base) break;
                if (ind > base) break;
                String t = line.trim();
                if (t.startsWith("-")) break;
                int colon = t.indexOf(':');
                if (colon < 0) {
                    i++;
                    continue;
                }
                String k = t.substring(0, colon).trim();
                String v = t.substring(colon + 1).trim();
                if (v.isEmpty()) {
                    if (i + 1 < lines.size() && indentOf(lines.get(i + 1)) > base) {
                        Result sub = parseBlock(i + 1);
                        map.put(k, sub.value);
                        i = sub.next;
                    } else {
                        map.put(k, null);
                        i++;
                    }
                } else {
                    map.put(k, parseScalar(v));
                    i++;
                }
            }
            r.value = map;
            r.next = i;
            return r;
        }
    }

    // ---------------- 写 ----------------

    public static String dump(Object obj) {
        StringBuilder sb = new StringBuilder();
        dumpInto(sb, obj, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void dumpInto(StringBuilder sb, Object obj, int indent) {
        String pad = pad(indent);
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = quote(e.getKey());
                Object v = e.getValue();
                if (v instanceof Map || v instanceof List) {
                    sb.append(pad).append(key).append(":\n");
                    dumpInto(sb, v, indent + 1);
                } else {
                    sb.append(pad).append(key).append(": ").append(scalar(v)).append('\n');
                }
            }
        } else if (obj instanceof List) {
            for (Object item : (List<Object>) obj) {
                if (item instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) item;
                    boolean first = true;
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        String key = quote(e.getKey());
                        Object v = e.getValue();
                        if (first) {
                            if (v instanceof Map || v instanceof List) {
                                sb.append(pad).append("- ").append(key).append(":\n");
                                dumpInto(sb, v, indent + 2);
                            } else {
                                sb.append(pad).append("- ").append(key).append(": ").append(scalar(v)).append('\n');
                            }
                            first = false;
                        } else {
                            String pad2 = pad(indent + 1);
                            if (v instanceof Map || v instanceof List) {
                                sb.append(pad2).append(key).append(":\n");
                                dumpInto(sb, v, indent + 2);
                            } else {
                                sb.append(pad2).append(key).append(": ").append(scalar(v)).append('\n');
                            }
                        }
                    }
                    if (m.isEmpty()) sb.append(pad).append("- {}\n");
                } else {
                    sb.append(pad).append("- ").append(scalar(item)).append('\n');
                }
            }
        } else {
            sb.append(pad).append(scalar(obj)).append('\n');
        }
    }

    private static String pad(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) sb.append("  ");
        return sb.toString();
    }

    private static String scalar(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Number) return v.toString();
        return quote(v.toString());
    }

    private static String quote(String s) {
        if (s == null) return "\"\"";
        boolean need = s.isEmpty();
        for (int i = 0; i < s.length() && !need; i++) {
            char c = s.charAt(i);
            if (c == ':' || c == '#' || c == '"' || c == '\'' || c == '\n' || c == '\r'
                    || c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == '&'
                    || c == '*' || c == '?' || c == '|' || c == '-' && i == 0 || c == '<'
                    || c == '>' || c == '=' || c == '!' || c == '%' || c == '@' || c == '`') {
                if (c == ':' && i + 1 < s.length() && s.charAt(i + 1) != ' ') continue;
                need = true;
            }
            if ((i == 0 || i == s.length() - 1) && (c == ' ' || c == '\t')) need = true;
        }
        if (!need) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}

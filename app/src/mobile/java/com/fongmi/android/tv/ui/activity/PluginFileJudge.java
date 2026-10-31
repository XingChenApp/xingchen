package com.fongmi.android.tv.ui.activity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 外部文件导入的定型判定（纯逻辑、零 Android 依赖，harness 可单机验）：
 * 先认文件名扩展名，认不出再嗅探内容头，只分 py / js 两大类——与插件管理
 * （PluginActivity）的两个页签一一对应。细分形态不在这里分：drpy/wv 的
 * 通道分发在 JsLoader 内部按内容判，音乐插件形态由 MusicSourceManager
 * .detectFormat 判（打开落点用），都不改落盘目录。
 * 扩展名是 .py/.js/.wv 时扩展名说了算，不许内容嗅探翻案（一个 .js 里
 * 出现 "class Spider" 字样不等于它是 py）。
 */
public final class PluginFileJudge {

    public enum Kind {PY, JS, UNKNOWN}

    public static final class Verdict {
        public final Kind kind;
        /** 落盘文件名（已清洗、扩展名已归一小写）；原名缺失/不可用时为 null，调用方按内容定名生成 */
        public final String targetName;

        Verdict(Kind kind, String targetName) {
            this.kind = kind;
            this.targetName = targetName;
        }
    }

    private PluginFileJudge() {
    }

    public static Verdict judge(String rawName, byte[] content) {
        String name = sanitize(rawName);
        String ext = extensionOf(name);
        if ("py".equals(ext)) return namedVerdict(Kind.PY, name, ext);
        if ("js".equals(ext) || "wv".equals(ext)) return namedVerdict(Kind.JS, name, ext);
        Kind sniffed = sniff(content);
        if (sniffed == Kind.UNKNOWN) return new Verdict(Kind.UNKNOWN, null);
        String suffix = sniffed == Kind.PY ? ".py" : ".js";
        if (name == null) return new Verdict(sniffed, null);
        // 有名但扩展名对不上（.txt 之类）：留基名换后缀，让插件页签能列出来
        String base = ext == null ? name : name.substring(0, name.length() - ext.length() - 1);
        base = canonicalBase(base);
        if (base == null || base.isEmpty()) return new Verdict(sniffed, null);
        return new Verdict(sniffed, base + suffix);
    }

    /** 直名路：基名先归一（剥避让缀号）再归一扩展名大小写；基名剥空按无名处理 */
    private static Verdict namedVerdict(Kind kind, String name, String ext) {
        String base = canonicalBase(name.substring(0, name.length() - ext.length() - 1));
        if (base == null || base.isEmpty()) return new Verdict(kind, null);
        return new Verdict(kind, normalizeExtCase(base + name.substring(name.length() - ext.length() - 1)));
    }

    /** 只留文件名本体：剥掉任何路径段与首尾空白；剥完为空（含纯 ".py" 这种没基名的）返回 null */
    static String sanitize(String rawName) {
        if (rawName == null) return null;
        String s = rawName.trim();
        int cut = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (cut >= 0) s = s.substring(cut + 1).trim();
        if (s.isEmpty() || ".".equals(s)) return null;
        if (s.lastIndexOf('.') == 0) return null; // 只有后缀没基名（".py"/".js" 这种）
        return s;
    }

    /** 避让缀号：基名尾部的「(数字)」「（数字）」（可带前置空白）——下载/分享链给同名文件自动缀的号，不是脚本名的一部分 */
    private static final Pattern AVOID_SUFFIX = Pattern.compile("\\s*[\\(（][0-9]+[\\)）]$");

    /**
     * 基名归一：剥掉尾部的避让缀号（可叠多层，逐层剥）。外部给到的文件名可能
     * 带着「鲍鱼盒子(1).py」这种缀号名进来——缀号是别处的同名避让产物，脚本的
     * 正名就是剥掉缀号的那个；咱家若原样收作落盘名/键/显示名，等于替别人把
     * 改名坐实（他 2026-10-31 连纠两次：无同名原样收、同名才覆盖、缀号本身
     * 就是错）。只认纯数字缀号，「(典藏版)」这类真名组成部分不动。
     */
    static String canonicalBase(String base) {
        if (base == null) return null;
        String b = base;
        while (true) {
            java.util.regex.Matcher m = AVOID_SUFFIX.matcher(b);
            if (!m.find()) return b;
            b = b.substring(0, m.start());
            if (b.isEmpty()) return "";
        }
    }

    /** 全名归一（基名剥避让缀号，扩展名原样）；无扩展名/剥空时原样返回，交给调用方原流程 */
    public static String canonicalName(String name) {
        if (name == null) return null;
        String ext = extensionOf(name);
        if (ext == null) return name;
        String base = canonicalBase(name.substring(0, name.length() - ext.length() - 1));
        if (base == null || base.isEmpty()) return name;
        return base + name.substring(name.length() - ext.length() - 1);
    }

    /**
     * 清掉目录里同一脚本的缀号变体文件（此前按缀号名落过盘留下的双份）：与
     * canonicalName 同口径判定同身份，只删名字带缀号的那份、正名那份不动。
     * 返回删掉的文件的绝对路径（供调用方按路径失效蜘蛛）。
     */
    public static List<String> purgeAvoidVariants(File dir, String canonicalName) {
        List<String> removed = new ArrayList<>();
        if (dir == null || canonicalName == null) return removed;
        File[] files = dir.listFiles();
        if (files == null) return removed;
        for (File f : files) {
            String n = f.getName();
            if (n.equals(canonicalName)) continue;
            if (canonicalName(n).equals(canonicalName) && f.delete()) removed.add(f.getAbsolutePath());
        }
        return removed;
    }

    /**
     * 落盘名扩展名归一小写（基名原样）：「鲍鱼盒子.PY」与「鲍鱼盒子.py」是同一个
     * 脚本，不归一会在大小写敏感的文件系统上落成两个文件、列表双份——他
     * 2026-10-31 亲令同名直接覆盖，落盘名先归一才谈得上同名。
     */
    static String normalizeExtCase(String name) {
        String ext = extensionOf(name);
        if (ext == null) return name;
        return name.substring(0, name.length() - ext.length()) + ext;
    }

    /** 小写扩展名（不含点）；无扩展名返回 null */
    static String extensionOf(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 内容嗅探：只看头部 64KB；py 标志更专一先判，js 标志后判，都不中 UNKNOWN */
    static Kind sniff(byte[] content) {
        if (content == null || content.length == 0) return Kind.UNKNOWN;
        int len = Math.min(content.length, 64 * 1024);
        String head = new String(content, 0, len, StandardCharsets.UTF_8);
        String trimmed = head.trim();
        if (trimmed.startsWith("#!")) {
            if (trimmed.substring(0, Math.min(trimmed.length(), 128)).contains("python")) return Kind.PY;
            if (trimmed.substring(0, Math.min(trimmed.length(), 128)).contains("node")) return Kind.JS;
        }
        if (head.contains("class Spider")
                || head.contains("def homeContent(")
                || head.contains("def homeVideoContent(")
                || head.contains("from spider import")
                || head.contains("import spider")) {
            return Kind.PY;
        }
        if (head.contains("__JS_SPIDER__")
                || head.contains("module.exports")
                || head.contains("var rule")
                || head.contains("let rule")
                || head.contains("const rule")
                || head.contains("globalThis.")
                || head.contains("function homeContent")) {
            return Kind.JS;
        }
        return Kind.UNKNOWN;
    }
}

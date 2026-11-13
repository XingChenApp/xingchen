package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地歌元数据清洗：只处理 platform=local/device 的曲目（标题多半是文件名派生），在线歌原样不动。
 * 解决：文件名标题含后缀（.flac）与发行组后缀（- h3R3）＋歌手占位（<unknown>）把搜词查询污染，
 * 本地歌搜不到词、播放页还把“.flac”显示成歌名。
 * 纯逻辑，不依赖 Android，可单测。
 */
public class LocalTrackMeta {

    public static final String PLATFORM_LOCAL = "local";
    public static final String PLATFORM_DEVICE = "device";

    /** 一次搜词查询：keyword 发给源搜，matchTitle 给 matchSong 对歌用 */
    public static class Query {
        public final String keyword;
        public final String matchTitle;

        public Query(String keyword, String matchTitle) {
            this.keyword = keyword;
            this.matchTitle = matchTitle;
        }
    }

    private static final Set<String> AUDIO_EXT = new HashSet<>();

    static {
        String[] exts = {"mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "ape", "wv", "aiff", "aif", "dff", "dsf"};
        for (String e : exts) AUDIO_EXT.add(e);
    }

    public static boolean isLocalPlatform(String platform) {
        return PLATFORM_LOCAL.equals(platform) || PLATFORM_DEVICE.equals(platform);
    }

    /** 歌手占位：MediaStore 的 <unknown>、空、unknown 类都不算真歌手 */
    public static boolean isPlaceholderArtist(String artist) {
        if (artist == null) return true;
        String a = artist.trim();
        if (a.isEmpty()) return true;
        if (a.startsWith("<") && a.endsWith(">") && a.length() > 2) a = a.substring(1, a.length() - 1).trim();
        String low = a.toLowerCase();
        return low.equals("unknown") || low.equals("unknown artist") || low.equals("未知")
                || low.equals("未知艺术家") || low.equals("未知歌手");
    }

    /** 专辑占位：空、unknown、未知专辑都算没专辑（跟歌手占位同口径，2026-10-22 专辑认不出修） */
    public static boolean isPlaceholderAlbum(String album) {
        if (album == null) return true;
        String a = album.trim();
        if (a.isEmpty()) return true;
        if (a.startsWith("<") && a.endsWith(">") && a.length() > 2) a = a.substring(1, a.length() - 1).trim();
        String low = a.toLowerCase();
        return low.equals("unknown") || low.equals("unknown album") || low.equals("未知") || low.equals("未知专辑");
    }

    /** 搜词／对歌用真歌手：占位返回空串 */
    public static String realArtist(String artist) {
        if (isPlaceholderArtist(artist)) return "";
        return artist.trim();
    }

    /** 专辑占位清洗（与 realArtist 同口径）：占位专辑按没有处理，不许把 <unknown> 之类露给界面 */
    public static String realAlbum(String album) {
        if (isPlaceholderAlbum(album)) return "";
        return album.trim();
    }

    /** 显示／主清洗：去文件后缀、去尾巴发行组后缀；括号标签不动（显示保留原意） */
    public static String cleanTitle(String title) {
        if (title == null) return "";
        String t = title.trim();
        t = stripAudioExt(t);
        for (int i = 0; i < 3; i++) {
            String next = stripReleaseSuffix(t);
            if (next.equals(t)) break;
            t = next;
        }
        return t.trim();
    }

    /** 显示用标题：本地歌清洗，在线歌原样 */
    public static String cleanDisplayTitle(String title, String platform) {
        if (title == null) return "";
        if (!isLocalPlatform(platform)) return title;
        return cleanTitle(title);
    }

    /** 去尾巴音频后缀（.flac/.mp3 等，大小写不敏感） */
    static String stripAudioExt(String title) {
        int dot = title.lastIndexOf('.');
        if (dot <= 0) return title;
        String ext = title.substring(dot + 1).toLowerCase();
        if (AUDIO_EXT.contains(ext)) return title.substring(0, dot).trim();
        return title;
    }

    /**
     * 去尾巴“ - 短标签”发行组后缀：短 ASCII 标签且含数字（如 h3R3、2024）或全大写短词（HQ、SQ、CD）。
     * 中文后缀（如 - 王力宏）、混合大小写词（如 - Live）一律保留，不动真歌名。
     */
    static String stripReleaseSuffix(String title) {
        int sep = -1;
        int sepLen = 0;
        String[] seps = {" - ", " \u2013 ", " \u2014 "};
        for (String s : seps) {
            int p = title.lastIndexOf(s);
            if (p > sep) {
                sep = p;
                sepLen = s.length();
            }
        }
        if (sep <= 0) return title;
        String token = title.substring(sep + sepLen).trim();
        if (token.isEmpty() || token.contains(" ")) return title;
        if (!token.matches("[A-Za-z0-9._-]{1,12}")) return title;
        boolean hasDigit = false;
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                hasDigit = true;
                break;
            }
        }
        boolean allCapsShort = token.length() <= 4 && token.equals(token.toUpperCase()) && token.matches("[A-Z]+");
        if (!hasDigit && !allCapsShort) return title;
        return title.substring(0, sep).trim();
    }

    /** 去全部括号／方括号／书名号标签（只供搜词降级用，不动显示） */
    static String stripBracketTags(String title) {
        return title.replaceAll("\\([^)]*\\)", " ").replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("\u3010[^\u3011]*\u3011", " ").replaceAll("\\s+", " ").trim();
    }

    /** 取第一个“ - ”前的核心段（搜词最后降级用） */
    static String coreBeforeDash(String title) {
        String[] seps = {" - ", " \u2013 ", " \u2014 "};
        int cut = title.length();
        for (String s : seps) {
            int p = title.indexOf(s);
            if (p > 0 && p < cut) cut = p;
        }
        return title.substring(0, cut).trim();
    }

    /** 标题形态（去重保序）：清洗 → 去括号 → 核心段 */
    public static List<String> titleForms(String title) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String clean = cleanTitle(title);
        addForm(out, seen, clean);
        addForm(out, seen, stripBracketTags(clean));
        addForm(out, seen, coreBeforeDash(stripBracketTags(clean)));
        return out;
    }

    private static void addForm(List<String> out, Set<String> seen, String form) {
        if (form == null) return;
        form = form.trim();
        if (form.isEmpty() || !seen.add(form)) return;
        out.add(form);
    }

    /**
     * 搜词查询序列（去重保序）：本地歌按形态逐级降级（带真歌手 → 净歌名），
     * 在线歌原样一个查询，行为不动。
     */
    public static List<Query> queries(String title, String artist, String platform) {
        List<Query> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (!isLocalPlatform(platform)) {
            String keyword = (title == null ? "" : title.trim()) + " " + (artist == null ? "" : artist.trim());
            keyword = keyword.trim();
            if (!keyword.isEmpty()) out.add(new Query(keyword, title == null ? "" : title));
            return out;
        }
        String real = realArtist(artist);
        for (String form : titleForms(title)) {
            if (!real.isEmpty()) addQuery(out, seen, form + " " + real, form);
            addQuery(out, seen, form, form);
        }
        return out;
    }

    private static void addQuery(List<Query> out, Set<String> seen, String keyword, String matchTitle) {
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.isEmpty() || !seen.add(keyword)) return;
        out.add(new Query(keyword, matchTitle));
    }
}

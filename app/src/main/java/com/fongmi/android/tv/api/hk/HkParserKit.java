package com.fongmi.android.tv.api.hk;

import java.util.Collection;
import java.util.List;

/**
 * 8.83 解析器兼容工具包。
 * <p>
 * 直接移植自海阔 8.83（do not rewrite）：
 * <ul>
 * <li>{@code com.example.hikerview.utils.StringUtil} —— 移植解析器与取数链用到的方法（getHome 链为环 15/17 补入）</li>
 * <li>{@code org.apache.commons.lang3.StringUtils#replaceOnce} / {@code replaceOnceIgnoreCase}</li>
 * <li>{@code com.example.hikerview.ui.browser.util.CollectionUtil#isNotEmpty}</li>
 * <li>{@code com.example.hikerview.ui.browser.model.UrlDetector#clearTag}</li>
 * </ul>
 */
public final class HkParserKit {

    private HkParserKit() {
    }

    // ---- StringUtil.isEmpty / isNotEmpty (8.83 原样) ----

    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence s) {
        return !isEmpty(s);
    }

    public static boolean isNotEmpty(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    // ---- StringUtil.arrayToString (8.83 原样) ----

    public static String arrayToString(String[] arr, int start, String sep) {
        return arrayToString(arr, start, arr == null ? 0 : arr.length, sep);
    }

    public static String arrayToString(String[] arr, int start, int end, String sep) {
        StringBuilder sb = new StringBuilder();
        if (arr == null || arr.length <= start) {
            return "";
        }
        if (arr.length <= 1) {
            return arr[0];
        }
        sb.append(arr[start]);
        for (int i = start + 1; i < arr.length && i < end; i++) {
            sb.append(sep).append(arr[i]);
        }
        return sb.toString();
    }

    // ---- StringUtil.listToString (8.83 原样) ----

    public static String listToString(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.size() <= 0) {
            return "";
        }
        if (list.size() <= 1) {
            return list.get(0);
        }
        sb.append(list.get(0));
        for (int i = 1; i < list.size(); i++) {
            sb.append(sep).append(list.get(i));
        }
        return sb.toString();
    }

    public static String listToString(List<String> list, int start, String sep) {
        StringBuilder sb = new StringBuilder();
        if (list == null || list.size() <= start) {
            return "";
        }
        if (list.size() <= 1) {
            return list.get(0);
        }
        sb.append(list.get(start));
        for (int i = start + 1; i < list.size(); i++) {
            sb.append(sep).append(list.get(i));
        }
        return sb.toString();
    }

    // ---- StringUtil.decodeConflictStr (8.83 原样) ----

    public static String decodeConflictStr(String str) {
        return isEmpty(str) ? str : str.replace("？？", "?").replace("＆＆", "&").replace("；；", ";").replace("，，", ",");
    }

    // ---- StringUtil.splitUrlByQuestionMark (8.83 原样) ----

    public static String[] splitUrlByQuestionMark(String str) {
        if (isEmpty(str)) {
            return new String[]{str};
        }
        String[] split = str.split("\\?");
        return split.length <= 1 ? split : new String[]{split[0], arrayToString(split, 1, "?")};
    }

    // ---- StringUtil.containsChinese (8.83 原样) ----

    public static boolean containsChinese(String str) {
        if (str == null) {
            return false;
        }
        for (char c : str.toCharArray()) {
            if (isChinese(c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    // ---- StringUtil.getBaseUrl (8.83 原样) ----

    public static String getBaseUrl(String str) {
        if (isEmpty(str)) {
            return str;
        }
        String host = str.replace("http://", "").replace("https://", "").split("/")[0];
        if (str.startsWith("https")) {
            return "https://" + host;
        }
        return "http://" + host;
    }

    // ---- StringUtil.getHome / getHomeUrl (8.83 原样) ----
    // 官 StringUtil.getHome（smali:474-479）：空回原串；余 scheme(split "://"[0])
    // + "://" + getDom0(getRealUrl(url))。getHomeUrl = getHome(getFirstPageUrl(url))
    // （smali:289，getFirstPageUrl 是 HttpParser 的，主源集同转写在 HkHttpParser）。
    // 与 ui/hk 的 HkStringUtil 同名实现同一份转写——主源集够不到 mobile 源集，
    // 按本类既有定位（主源集 StringUtil 转写收口，getBaseUrl 段同例）落在此处，
    // 环 15 fromRule 拼支与环 17 ruleBaseUrl 共用这一份，不另立副本。

    public static String getHome(String url) {
        if (isEmpty(url)) {
            return url;
        }
        String real = getRealUrl(url);
        String dom = getDom0(real);
        return real.split("://")[0] + "://" + dom;
    }

    public static String getHomeUrl(String url) {
        return getHome(HkHttpParser.getFirstPageUrl(url));
    }

    /** StringUtil.getDom0（官 private，同 ui 转写）：特殊协议前缀原样回整串、异常回 null。 */
    private static String getDom0(String url) {
        if (isEmpty(url)) {
            return url;
        }
        if (url.startsWith("thunder://") || url.startsWith("magnet:") || url.startsWith("ftp://")
                || url.startsWith("ed2k:") || url.startsWith("file://")) {
            return url;
        }
        try {
            String[] seg = url.split("://");
            if (seg.length > 1) return seg[1].split("/")[0];
            String[] seg2 = url.split("//");
            if (seg2.length > 1) return seg2[1].split("/")[0];
            String s = url.replaceFirst("http://", "").replaceFirst("https://", "").replaceFirst("hiker://", "");
            String[] parts = s.split("/");
            if (parts.length > 0) return parts[0];
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** StringUtil.getMaxItem（官 private，同 ui 转写）：按正则切分取以 http 开头且最长一段，无则 null。 */
    private static String getMaxItem(String s, String regex) {
        String[] parts = s.split(regex);
        String best = null;
        int maxLen = 0;
        for (String part : parts) {
            if (part.startsWith("http") && part.length() > maxLen) {
                maxLen = part.length();
                best = part;
            }
        }
        return best;
    }

    /** StringUtil.getRealUrl（同 ui 转写）：剥 @lazyRule=/@rule= 与 hiker://empty 各形态，末尾取 $$$ / $$ / ## 分隔的最长 http 段；异常回当前已处理到的串。 */
    public static String getRealUrl(String url) {
        if (isEmpty(url)) {
            return url;
        }
        String p0 = url;
        try {
            p0 = p0.split("@lazyRule=")[0].split("@rule=")[0];
            if (p0.startsWith("hiker://empty#http")) {
                p0 = replaceOnce(p0, "hiker://empty#", "");
            } else if (p0.startsWith("webview://")) {
                p0 = replaceOnce(p0, "webview://", "");
            } else if (p0.startsWith("hiker://empty##https%3A%2F%2F")
                    || p0.startsWith("hiker://empty##http%3A%2F%2F")) {
                p0 = HkHttpParser.decodeUrl(replaceOnce(p0, "hiker://empty##", ""), "UTF-8");
            } else if (p0.startsWith("hiker://empty##http")) {
                p0 = replaceOnce(p0, "hiker://empty##", "");
            } else if (p0.startsWith("hiker://empty$http")) {
                p0 = replaceOnce(p0, "hiker://empty$", "");
            } else if (p0.startsWith("hiker://empty$$http")) {
                p0 = replaceOnce(p0, "hiker://empty$$", "");
            } else if (p0.startsWith("hiker://empty$$$http")) {
                p0 = replaceOnce(p0, "hiker://empty$$$", "");
            } else if (p0.startsWith("hiker://empty?url=")) {
                p0 = replaceOnce(p0, "hiker://empty?url=", "");
            }
            String m = getMaxItem(p0, "\\$\\$\\$");
            if (isNotEmpty(m)) return m;
            m = getMaxItem(p0, "\\$\\$");
            if (isNotEmpty(m)) return m;
            m = getMaxItem(p0, "##");
            if (isNotEmpty(m)) return m;
            return p0;
        } catch (Exception e) {
            return p0;
        }
    }

    // ---- StringUtil.trimBlanks (8.83 原样，CommonParser.getUrlWithoutOr 用) ----

    public static String trimBlanks(String str) {
        if (str == null) {
            return "";
        }
        return str.trim().replaceAll("\\s+", " ");
    }

    // ---- commons-lang3 StringUtils.replaceOnce / replaceOnceIgnoreCase ----

    public static String replaceOnce(String text, String searchString, String replacement) {
        if (isEmpty(text) || isEmpty(searchString)) {
            return text;
        }
        int idx = text.indexOf(searchString);
        if (idx < 0) {
            return text;
        }
        return text.substring(0, idx) + replacement + text.substring(idx + searchString.length());
    }

    public static String replaceOnceIgnoreCase(String text, String searchString, String replacement) {
        if (isEmpty(text) || isEmpty(searchString)) {
            return text;
        }
        int idx = text.toLowerCase().indexOf(searchString.toLowerCase());
        if (idx < 0) {
            return text;
        }
        return text.substring(0, idx) + replacement + text.substring(idx + searchString.length());
    }

    // ---- UrlDetector.clearTag (8.83 原样) ----
    // 去掉 x5Play:// 前缀及 #xxx# 标记。
    // 官包 UrlDetector.smali clearTag 全形：x5Play:// 仅在开头时 replaceOnce
    // 去除；24 个标签逐个 replaceOnce；尾以 replaceAll 清 "#threads=\d{1,2}#"。
    // 旧移植只落了前 7 个标签且 x5Play 无条件去除（G17 施工时对官查出），
    // 此处补齐全集——本法是 HkPageParser 与 HkJsRuntime 取页共用的同一口。

    public static String clearTag(String str) {
        if (isEmpty(str)) {
            return str;
        }
        if (str.startsWith("x5Play://")) {
            str = replaceOnce(str, "x5Play://", "");
        }
        str = replaceOnce(str, "#ignoreVideo=true#", "");
        str = replaceOnce(str, "#isVideo=true#", "");
        str = replaceOnce(str, "#ignoreImg=true#", "");
        str = replaceOnce(str, "#immersiveTheme#", "");
        str = replaceOnce(str, "#noRecordHistory#", "");
        str = replaceOnce(str, "#noHistory#", "");
        str = replaceOnce(str, "#noLoading#", "");
        str = replaceOnce(str, "#isMusic=true#", "");
        str = replaceOnce(str, "#ignoreMusic=true#", "");
        str = replaceOnce(str, "#autoPage#", "");
        str = replaceOnce(str, "#autoReader#", "");
        str = replaceOnce(str, "#pre#", "");
        str = replaceOnce(str, "#noPre#", "");
        str = replaceOnce(str, "#fullTheme#", "");
        str = replaceOnce(str, "#readTheme#", "");
        str = replaceOnce(str, "#gameTheme#", "");
        str = replaceOnce(str, "#noRefresh#", "");
        str = replaceOnce(str, "#background#", "");
        str = replaceOnce(str, "#autoCache#", "");
        str = replaceOnce(str, "#cacheOnly#", "");
        str = replaceOnce(str, "#originalSize#", "");
        str = replaceOnce(str, "#memoryPage#", "");
        str = replaceOnce(str, "#fastPlayMode#", "");
        str = replaceOnce(str, "#checkMetadata=false#", "");
        str = str.replaceAll("#threads=\\d{1,2}#", "");
        return str;
    }
}

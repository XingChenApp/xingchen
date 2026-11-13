package com.fongmi.android.tv.ui.hk;

import android.text.TextUtils;

import java.net.URLDecoder;

import android.util.Log;

/**
 * 照抄 8.83 StringUtil / HttpParser 的 URL 工具批：
 * autoFixUrl、getBaseUrl、getHomeUrl(getHome+getDom0+getRealUrl+getMaxItem)、getFirstPageUrl、decodeUrl。
 * 怪癖照抄（getDom0 对 thunder/magnet/ftp/ed2k/file 前缀原样返回整串、异常回 null 等）。
 */
public final class HkStringUtil {
    private static final String TAG = "HkStringUtil";

    private HkStringUtil() {
    }

    /** 照抄 StringUtil.isEmpty：仅判 null 与长度 0。 */
    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    public static boolean isNotEmpty(CharSequence s) {
        return !isEmpty(s);
    }

    /** 照抄 commons-lang3 StringUtils.replaceOnce：字面量替换首个出现处。 */
    public static String replaceOnce(String text, String search, String replacement) {
        if (text == null || search == null || search.isEmpty()) return text;
        int idx = text.indexOf(search);
        if (idx < 0) return text;
        return text.substring(0, idx) + replacement + text.substring(idx + search.length());
    }

    /** 照抄 commons-lang3 StringUtils.replaceOnceIgnoreCase：忽略大小写定位首个出现处再字面替换。 */
    public static String replaceOnceIgnoreCase(String text, String search, String replacement) {
        if (text == null || search == null || search.isEmpty()) return text;
        int idx = text.toLowerCase(java.util.Locale.ROOT).indexOf(search.toLowerCase(java.util.Locale.ROOT));
        if (idx < 0) return text;
        return text.substring(0, idx) + replacement + text.substring(idx + search.length());
    }

    /** 照抄 StringUtil.replaceLineBlank：名不副实，只 replaceAll("\n","")，异常回原串。 */
    public static String replaceLineBlank(String s) {
        try {
            return s.replaceAll("\n", "");
        } catch (Exception e) {
            return s;
        }
    }

    /** 照抄 StringUtil.<clinit> 的 SCHEME_CANT_HANDLE（StringUtil.smali:423 一带）。 */
    private static final String[] SCHEME_CANT_HANDLE = {"ftp:", "ed2k:", "magnet:", "thunder:", "xg:"};

    /** 照抄 StringUtil.<clinit> 的 innerScheme（StringUtil.smali:428 起）。 */
    private static final String[] INNER_SCHEME = {
            "http", "hiker", "file", "/", "content", "{", "[", "x5:", "fileSelect://", "toast://",
            "web://", "input://", "confirm://", "select://", "copy://", "x5WebView:", "editFile://",
            "rtmp:", "rtsp:", "rule:", "code:", "海阔视界", "x5Play:", "func:", "download://",
            "share://", "openFile://", "webview://"
    };

    /** 照抄 StringUtil.isCannotHandleScheme（smali:3591）：命中 SCHEME_CANT_HANDLE 任一前缀。 */
    public static boolean isCannotHandleScheme(String url) {
        if (url == null) return false;
        for (String scheme : SCHEME_CANT_HANDLE) {
            if (url.startsWith(scheme)) return true;
        }
        return false;
    }

    /** 照抄 StringUtil.isOnlyEn（smali 附近）：^[a-zA-Z]+$。 */
    public static boolean isOnlyEn(String s) {
        return s != null && s.matches("^[a-zA-Z]+$");
    }

    /** 照抄 StringUtil.isScheme（smali:4001）：video:// 恒 false；innerScheme 前缀恒 false；余者 scheme 段须 ≤20 且纯英文。 */
    public static boolean isScheme(String url) {
        if (url == null) return false;
        if (url.startsWith("video://")) return false;
        for (String scheme : INNER_SCHEME) {
            if (url.startsWith(scheme)) return false;
        }
        String[] parts = url.split("://");
        if (parts.length < 2) return false;
        String head = parts[0];
        if (head.length() > 20) return false;
        return isOnlyEn(head);
    }

    /** 照抄 HttpParser.decodeUrl：先把孤立 % 转义、+ 转 %2B，再 URLDecoder。 */
    public static String decodeUrl(String s, String charset) {
        if (s == null) return null;
        try {
            s = s.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
            s = s.replaceAll("\\+", "%2B");
            return URLDecoder.decode(s, charset);
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return s;
        }
    }

    /** 照抄 StringUtil.getBaseUrl：scheme + "://" + 首段 host。 */
    public static String getBaseUrl(String url) {
        if (isEmpty(url)) return url;
        String host = url.replace("http://", "").replace("https://", "").split("/")[0];
        return url.startsWith("https") ? "https://" + host : "http://" + host;
    }

    /** 照抄 StringUtil.autoFixUrl(base, url)：相对地址补全。 */
    public static String autoFixUrl(String base, String url) {
        if (isEmpty(base) || isEmpty(url)) return url;
        String base0 = base.split(";")[0];
        String baseUrl = getBaseUrl(base0);
        String lower = url.toLowerCase();
        if (lower.startsWith("http") || lower.startsWith("hiker")
                || lower.startsWith("pics") || lower.startsWith("code")) {
            return url;
        }
        if (url.startsWith("//")) return "http:" + url;
        if (url.startsWith("magnet") || url.startsWith("thunder")
                || url.startsWith("ftp") || url.startsWith("ed2k")) {
            return url;
        }
        if (url.startsWith("/")) {
            if (baseUrl.endsWith("/")) {
                return baseUrl.substring(0, baseUrl.length() - 1) + url;
            }
            return baseUrl + url;
        }
        if (url.startsWith("./")) {
            String[] seg = base0.split("://");
            if (seg.length < 1) return url;
            String[] hostParts = seg[1].split("/");
            if (hostParts.length <= 1) {
                if (baseUrl.endsWith("/")) {
                    return baseUrl.substring(0, baseUrl.length() - 1) + url.replace("./", "");
                }
                return baseUrl + url.replace("./", "");
            }
            String trimmed = seg[1].replace(hostParts[hostParts.length - 1], "");
            return seg[0] + "://" + trimmed + url.replace("./", "");
        }
        if (url.startsWith("?")) return base0 + url;
        return url;
    }

    /** 照抄 StringUtil.getHomeUrl = getHome(getFirstPageUrl(url))。 */
    public static String getHomeUrl(String url) {
        return getHome(getFirstPageUrl(url));
    }

    /** 照抄 StringUtil.getHome：scheme + "://" + getDom0(getRealUrl(url))。 */
    public static String getHome(String url) {
        if (TextUtils.isEmpty(url)) return url;
        String real = getRealUrl(url);
        String dom = getDom0(real);
        return real.split("://")[0] + "://" + dom;
    }

    /** 照抄 StringUtil.getDom0（private 提为包内可见）：取域名首段；特殊协议前缀原样回整串、异常回 null。 */
    static String getDom0(String url) {
        if (TextUtils.isEmpty(url)) return url;
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

    /** 照抄 StringUtil.getMaxItem：按正则切分后取以 http 开头且最长的一段，无则 null。 */
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

    /** 照抄 StringUtil.getRealUrl：剥 @lazyRule=/@rule= 与 hiker://empty 各形态，末尾取 $$$ / $$ / ## 分隔的最长 http 段。 */
    public static String getRealUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        String p0 = url;
        try {
            p0 = p0.split("@lazyRule=")[0].split("@rule=")[0];
            if (p0.startsWith("hiker://empty#http")) {
                p0 = replaceOnce(p0, "hiker://empty#", "");
            } else if (p0.startsWith("webview://")) {
                p0 = replaceOnce(p0, "webview://", "");
            } else if (p0.startsWith("hiker://empty##https%3A%2F%2F")
                    || p0.startsWith("hiker://empty##http%3A%2F%2F")) {
                p0 = decodeUrl(replaceOnce(p0, "hiker://empty##", ""), "UTF-8");
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
            Log.e(TAG, "hk error", e);
            return p0;
        }
    }

    /** 照抄 StringUtil.getRealUrl(pageUrl, url)：url 的真实地址非空且以 http 开头时回该地址，否则回 pageUrl。 */
    public static String getRealUrl(String pageUrl, String url) {
        String real = getRealUrl(url);
        if (!isEmpty(real) && real.startsWith("http")) return real;
        return pageUrl;
    }

    /** 照抄 StringUtil.getDom：空回原串，余 getDom0(getRealUrl(url))。 */
    public static String getDom(String url) {
        if (TextUtils.isEmpty(url)) return url;
        return getDom0(getRealUrl(url));
    }

    /** 照抄 StringUtil.getSecondDom：取域名的最后两段（段数 ≥3 时），否则回域名本身。 */
    public static String getSecondDom(String url) {
        if (isEmpty(url)) return url;
        String dom = getDom(url);
        if (dom == null) return null;
        String[] parts = dom.split("\\.");
        if (parts.length >= 3) {
            return com.fongmi.android.tv.api.hk.HkParserKit.arrayToString(parts, parts.length - 2, ".");
        }
        return dom;
    }

    /** 照抄 StringUtil.trimBlanks：剥两端 \n \r \t \f（不含空格，原版怪癖照抄）。 */
    public static String trimBlanks(String s) {
        if (s == null || s.length() == 0) return s;
        int start = 0;
        int end = s.length();
        while (start < end && isBlankChar(s.charAt(start))) start++;
        while (end > start && isBlankChar(s.charAt(end - 1))) end--;
        return s.substring(start, end);
    }

    private static boolean isBlankChar(char c) {
        return c == '\n' || c == '\r' || c == '\t' || c == '\f';
    }

    /** 照抄 DetailUIHelper.getTitleText(title) = getTitleText(title, true)。 */
    public static String getTitleText(String title) {
        return getTitleText(title, true);
    }

    /**
     * 照抄 DetailUIHelper.getTitleText(title, truncate)：
     * 含 ““”” / ‘‘’’ 标记组 → 剥标记后 Jsoup 取文本；以 < 开头或含 </ → Jsoup 取文本；
     * 余直接用原标题（未 trim，原版怪癖）；truncate 时超 200 字截断加 "..."；末尾恒把 "-" 换 "_"。
     */
    public static String getTitleText(String title, boolean truncate) {
        String p0 = title;
        try {
            if (isEmpty(title)) return "";
            String t = trimBlanks(title);
            if (t.contains("““””") || t.contains("‘‘’’")) {
                String s = t.replace("““””", "").replace("‘‘’’", "");
                p0 = org.jsoup.Jsoup.parse(s).text();
            } else if (t.startsWith("<") || t.contains("</")) {
                p0 = org.jsoup.Jsoup.parse(t).text();
            }
            if (truncate && p0.length() > 200) {
                p0 = p0.substring(0, 200) + "...";
            }
            p0 = p0.replace("-", "_");
            return p0;
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return p0;
        }
    }

    /** 照抄 PageParser.getUrl(url, extra)：extra 是 JSON 对象且含非空 "url" 键时取该 url，否则原样返回。 */
    public static String pageParserGetUrl(String url, String extra) {
        com.google.gson.JsonObject obj = null;
        if (isNotEmpty(extra) && extra.startsWith("{") && extra.endsWith("}")) {
            try {
                obj = com.google.gson.JsonParser.parseString(extra).getAsJsonObject();
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
            }
        }
        if (obj != null && obj.has("url")) {
            try {
                String u = obj.get("url").getAsString();
                if (u != null) return u;
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
            }
        }
        return url;
    }

    /** 照抄 HttpParser.getFirstPageUrl：剥协议壳后处理 [firstPage=...] / fypage@ 算式 / fypage→1。 */
    public static String getFirstPageUrl(String url) {
        if (isEmpty(url)) return url;
        String p0 = url;
        try {
            if (p0.startsWith("x5://")) return p0.replace("x5://", "");
            if (p0.startsWith("webview://")) return p0.replace("webview://", "");
            if (p0.startsWith("x5Rule://")) return p0.split("@")[0].replace("x5Rule://", "");
            if (p0.startsWith("webRule://")) return p0.split("@")[0].replace("webRule://", "");
            if (p0.startsWith("hiker://empty##https%3A%2F%2F")
                    || p0.startsWith("hiker://empty##http%3A%2F%2F")) {
                p0 = decodeUrl(replaceOnce(p0, "hiker://empty##", ""), "UTF-8");
            } else if (p0.startsWith("hiker://empty#http")) {
                p0 = replaceOnce(p0, "hiker://empty#", "");
            } else if (p0.startsWith("hiker://empty##http")) {
                p0 = replaceOnce(p0, "hiker://empty##", "");
            } else if (p0.startsWith("hiker://empty$http")) {
                p0 = replaceOnce(p0, "hiker://empty$", "");
            } else if (p0.startsWith("hiker://empty$$http")) {
                p0 = replaceOnce(p0, "hiker://empty$$", "");
            } else if (p0.startsWith("hiker://empty$$$http")) {
                p0 = replaceOnce(p0, "hiker://empty$$$", "");
            } else if (p0.startsWith("download://")) {
                return p0.replace("download://", "");
            } else if (p0.startsWith("video://")) {
                return p0.replace("video://", "");
            }
            String seg = p0.split(";")[0];
            String[] fp = seg.split("\\[firstPage=");
            if (fp.length > 1) {
                return fp[1].split("]")[0];
            }
            if (seg.contains("fypage@")) {
                String[] parts = seg.split("fypage@");
                String[] expr = parts[1].split("@");
                int page = 1;
                for (int i = 0; i < expr.length - 1; i++) {
                    String t = expr[i];
                    if (t.startsWith("-")) {
                        page -= Integer.parseInt(t.replace("-", ""));
                    } else if (t.startsWith("+")) {
                        page += Integer.parseInt(t.replace("+", ""));
                    } else if (t.startsWith("*")) {
                        page *= Integer.parseInt(t.replace("*", ""));
                    } else if (t.startsWith("/")) {
                        page /= Integer.parseInt(t.replace("/", ""));
                    }
                }
                return parts[0] + page + expr[expr.length - 1];
            }
            return seg.replace("fypage", "1");
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return p0;
        }
    }

    /**
     * 照 StringUtil.replaceContains（搜索标题高亮用）：keyword 按空格拆 token，
     * 逐 token 在剩余文本中定位首现：命中段前/后文本以 (p4, p5) 包裹、token 本身以 (p2, p3) 包裹；
     * 尾段同样以 (p4, p5) 包裹；全部片段顺序拼接。text/keyword 空 → 原样返回。
     */
    public static String replaceContains(String text, String keyword, String p2, String p3,
                                         String p4, String p5) {
        if (isEmpty(text) || isEmpty(keyword)) return text;
        String[] tokens = keyword.split(" ");
        if (tokens.length < 1) return text;
        java.util.List<String> parts = new java.util.ArrayList<>();
        String remaining = text;
        for (String token : tokens) {
            int idx = remaining.indexOf(token);
            if (idx < 0) break;
            if (isNotEmpty(p4)) parts.add(p4);
            parts.add(remaining.substring(0, idx));
            if (isNotEmpty(p5)) parts.add(p5);
            parts.add(p2);
            parts.add(token);
            parts.add(p3);
            remaining = remaining.substring(idx + token.length());
        }
        if (isNotEmpty(remaining)) {
            if (isNotEmpty(p4)) parts.add(p4);
            parts.add(remaining);
            if (isNotEmpty(p5)) parts.add(p5);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) sb.append(part);
        return sb.toString();
    }

    /**
     * 照 StringUtil.searchContains(text, keyword, ignoreCase)：
     * text 空 → false；keyword 空 → true；ignoreCase 时双方转小写；
     * keyword 按空格拆 ≥2 token 时须按序逐个在剩余文本中命中，单 token 走 contains。
     */
    public static boolean searchContains(String text, String keyword, boolean ignoreCase) {
        if (isEmpty(text)) return false;
        if (isEmpty(keyword)) return true;
        if (ignoreCase) {
            text = text.toLowerCase();
            keyword = keyword.toLowerCase();
        }
        String[] tokens = keyword.split(" ");
        if (tokens.length >= 2) {
            String rest = text;
            for (String token : tokens) {
                int idx = rest.indexOf(token);
                if (idx < 0) return false;
                rest = rest.substring(idx + token.length());
            }
            return true;
        }
        return text.contains(keyword);
    }
}

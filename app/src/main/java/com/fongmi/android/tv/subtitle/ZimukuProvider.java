package com.fongmi.android.tv.subtitle;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.setting.SubtitleSetting;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕库（zimuku.org / zimuku.la）：网页抓取。
 * 搜索：GET /search?q={keyword}；详情页找下载链接（多为 zip 压缩包，下载后由 SubtitleManager 解压）。
 */
public class ZimukuProvider implements SubtitleProvider {

    private static final String BASE = "https://zimuku.org";
    private static final String BASE_FALLBACK = "http://www.zimuku.la";

    private static final Pattern ITEM = Pattern.compile(
            "<a[^>]+href=\"(/detail/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern LANG_HINT = Pattern.compile(
            "(简体|繁体|双语|中英|英文|英语|简|繁)", Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "zimuku";
    }

    @Override
    public String getName() {
        return "字幕库";
    }

    @Override
    public boolean isAvailable() {
        return SubtitleSetting.isSrcEnabled("zimuku");
    }

    @Override
    public List<SubtitleInfo> search(String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(query) || !isAvailable()) return result;
        result.addAll(searchOn(BASE, query));
        if (result.isEmpty()) result.addAll(searchOn(BASE_FALLBACK, query));
        return result;
    }

    private List<SubtitleInfo> searchOn(String base, String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        try {
            String url = base + "/search?q=" + Uri.encode(query);
            String html = OkHttp.string(url, headers(base));
            if (ShieldBypass.isShieldPage(html)) {
                ShieldBypass.setBlocked(getId());
                return result;
            }
            if (TextUtils.isEmpty(html)) return result;
            Matcher m = ITEM.matcher(html);
            int count = 0;
            while (m.find() && count < 30) {
                try {
                    String path = m.group(1);
                    String rawTitle = stripTags(m.group(2)).trim();
                    if (TextUtils.isEmpty(rawTitle) || rawTitle.length() > 200) continue;
                    // 跳过导航类链接
                    if (rawTitle.length() < 2) continue;
                    String window = html.substring(m.end(), Math.min(html.length(), m.end() + 600));
                    String lang = guessLang(window, rawTitle);
                    result.add(new SubtitleInfo(rawTitle, lang, "zimuku://detail" + path + "@" + base, getId()));
                    count++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 解析详情页拿真实下载地址（多为 zip/rar）。
     * downloadUrl 格式：zimuku://detail{path}@{base}
     */
    public static String resolveDownloadUrl(String stored) {
        try {
            if (!stored.startsWith("zimuku://detail")) return stored;
            String rest = stored.substring("zimuku://detail".length());
            int at = rest.lastIndexOf('@');
            if (at < 0) return "";
            String path = rest.substring(0, at);
            String base = rest.substring(at + 1);
            String html = OkHttp.string(base + path, headers(base));
            if (TextUtils.isEmpty(html)) return "";
            // 找下载链接：down_url / download 按钮
            Matcher m = Pattern.compile("href=\"([^\"]*(?:down|download)[^\"]*)\"").matcher(html);
            while (m.find()) {
                String dl = m.group(1);
                if (dl.contains(".php") || dl.contains("javascript")) continue;
                if (!dl.startsWith("http")) dl = base + (dl.startsWith("/") ? "" : "/") + dl;
                return dl;
            }
            // 兜底：直接找压缩包/字幕文件链接
            Matcher d = Pattern.compile("href=\"([^\"]*\\.(?:zip|rar|7z|srt|ass|ssa)[^\"]*)\"").matcher(html);
            if (d.find()) {
                String dl = d.group(1);
                return dl.startsWith("http") ? dl : base + dl;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String guessLang(String window, String title) {
        String s = window + " " + title;
        Matcher m = LANG_HINT.matcher(s);
        if (m.find()) {
            String hit = m.group(1);
            if (hit.contains("双语") || hit.contains("中英")) return "zh_en";
            if (hit.contains("繁")) return "zh_cht";
            if (hit.contains("英")) return "en";
            return "zh_chs";
        }
        return "zh_chs";
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        return TAG.matcher(s).replaceAll("").replace("&nbsp;", " ").trim();
    }

    private static Map<String, String> headers(String base) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        h.put("Accept", "text/html,application/xhtml+xml");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        ShieldBypass.injectCookie(h, ShieldBypass.hostOf(base));
        return h;
    }
}

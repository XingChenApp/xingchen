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
 * 射手网（现 assrt.net）：网页抓取。
 * 搜索：GET /search?q={keyword}；解析结果中的字幕条目。
 * 射手网传统 API 是按文件 Hash 匹配（点播流无本地文件），这里用标题搜索兜底。
 */
public class ShooterProvider implements SubtitleProvider {

    private static final String BASE = "https://assrt.net";
    private static final String BASE_FALLBACK = "https://www.shooter.cn";

    private static final Pattern ITEM = Pattern.compile(
            "<a[^>]+href=\"(/subtitle/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern ITEM2 = Pattern.compile(
            "<a[^>]+href=\"([^\"]*download[^\"]*)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern LANG_HINT = Pattern.compile(
            "(简体|繁体|双语|中英|英文|英语|简|繁)", Pattern.CASE_INSENSITIVE);

    @Override
    public String getId() {
        return "shooter";
    }

    @Override
    public String getName() {
        return "射手网";
    }

    @Override
    public boolean isAvailable() {
        return SubtitleSetting.isSrcEnabled("shooter");
    }

    @Override
    public List<SubtitleInfo> search(String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(query) || !isAvailable()) return result;
        // 先试 assrt.net，失败回退 shooter.cn
        result.addAll(searchOn(BASE, query));
        if (result.isEmpty()) result.addAll(searchOn(BASE_FALLBACK, query));
        return result;
    }

    private List<SubtitleInfo> searchOn(String base, String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        try {
            String url = base + "/search?q=" + Uri.encode(query);
            String html = OkHttp.string(url, headers());
            if (TextUtils.isEmpty(html)) {
                // 换一种搜索路径试试
                url = base + "/search/" + Uri.encode(query);
                html = OkHttp.string(url, headers());
            }
            if (TextUtils.isEmpty(html)) return result;
            Matcher m = ITEM.matcher(html);
            int count = 0;
            while (m.find() && count < 30) {
                try {
                    String path = m.group(1);
                    String rawTitle = stripTags(m.group(2)).trim();
                    if (TextUtils.isEmpty(rawTitle) || rawTitle.length() > 200) continue;
                    String window = html.substring(m.end(), Math.min(html.length(), m.end() + 600));
                    String lang = guessLang(window, rawTitle);
                    result.add(new SubtitleInfo(rawTitle, lang, "shooter://detail" + path + "@" + base, getId()));
                    count++;
                } catch (Throwable ignored) {
                }
            }
            // 兜底：直接找下载链接
            if (result.isEmpty()) {
                Matcher d = ITEM2.matcher(html);
                while (d.find() && count < 30) {
                    try {
                        String dl = d.group(1);
                        String rawTitle = stripTags(d.group(2)).trim();
                        if (TextUtils.isEmpty(rawTitle)) continue;
                        if (!dl.startsWith("http")) dl = base + dl;
                        result.add(new SubtitleInfo(rawTitle, guessLang("", rawTitle), dl, getId()));
                        count++;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 解析详情页拿真实下载地址。
     * downloadUrl 格式：shooter://detail{path}@{base}
     */
    public static String resolveDownloadUrl(String stored) {
        try {
            if (!stored.startsWith("shooter://detail")) return stored;
            String rest = stored.substring("shooter://detail".length());
            int at = rest.lastIndexOf('@');
            if (at < 0) return "";
            String path = rest.substring(0, at);
            String base = rest.substring(at + 1);
            String html = OkHttp.string(base + path, headers());
            if (TextUtils.isEmpty(html)) return "";
            Matcher m = Pattern.compile("href=\"([^\"]*\\.(?:srt|ass|ssa|zip)[^\"]*)\"").matcher(html);
            if (m.find()) {
                String dl = m.group(1);
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

    private static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        h.put("Accept", "text/html,application/xhtml+xml");
        h.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return h;
    }
}

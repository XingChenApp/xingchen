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
 * SubHD（https://subhd.tv）：网页抓取。
 * 搜索：GET /search/{keyword}；解析结果列表中的字幕条目。
 * 注意：SubHD 下载有时弹验证码，抓取为尽力而为，失败返回空列表。
 */
public class SubHDProvider implements SubtitleProvider {

    private static final String BASE = "https://subhd.tv";

    // 结果条目：找详情页链接 + 标题 + 语言/格式描述
    private static final Pattern ITEM = Pattern.compile(
            "<a[^>]+href=\"(/d/[^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern LANG_HINT = Pattern.compile(
            "(简体|繁体|双语|中英|简英|繁英|英文|英语)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMAT_HINT = Pattern.compile(
            "\\b(ASS|SRT|SSA|VTT|SUP)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    @Override
    public String getId() {
        return "subhd";
    }

    @Override
    public String getName() {
        return "SubHD";
    }

    @Override
    public boolean isAvailable() {
        return SubtitleSetting.isSrcEnabled("subhd");
    }

    @Override
    public List<SubtitleInfo> search(String query) {
        List<SubtitleInfo> result = new ArrayList<>();
        if (TextUtils.isEmpty(query) || !isAvailable()) return result;
        try {
            String url = BASE + "/search/" + Uri.encode(query);
            String html = OkHttp.string(url, headers());
            if (ShieldBypass.isShieldPage(html)) {
                ShieldBypass.setBlocked(getId());
                return result;
            }
            if (TextUtils.isEmpty(html)) return result;
            Matcher m = ITEM.matcher(html);
            int count = 0;
            while (m.find() && count < 30) {
                try {
                    String detailPath = m.group(1);
                    String rawTitle = stripTags(m.group(2)).trim();
                    if (TextUtils.isEmpty(rawTitle) || rawTitle.length() > 200) continue;
                    // 在条目附近找语言/格式提示（取链接后 500 字符窗口）
                    String window = html.substring(m.end(), Math.min(html.length(), m.end() + 800));
                    String lang = guessLang(window);
                    String detailUrl = BASE + detailPath;
                    // 下载链接需要进详情页拿 ajax 接口；这里存详情页，下载时再解析
                    result.add(new SubtitleInfo(rawTitle, lang, "subhd://detail" + detailPath, getId()));
                    count++;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    /**
     * 解析详情页，拿到真实下载地址。
     * 优先找 /ajax/down_ajax 需要的参数；失败返回空。
     */
    public static String resolveDownloadUrl(String detailPath) {
        try {
            String html = OkHttp.string(BASE + detailPath, headers());
            if (TextUtils.isEmpty(html)) return "";
            // 找下载按钮上的 data-id / sid 参数
            Matcher m = Pattern.compile("down_ajax[^0-9]*([0-9]+)").matcher(html);
            if (m.find()) {
                String sid = m.group(1);
                Map<String, String> h = headers();
                h.put("X-Requested-With", "XMLHttpRequest");
                h.put("Referer", BASE + detailPath);
                // POST 表单：sub_id
                okhttp3.FormBody body = new okhttp3.FormBody.Builder().add("sub_id", sid).build();
                try (okhttp3.Response res = OkHttp.newCall(BASE + "/ajax/down_ajax", h, body).execute()) {
                    if (res.body() == null) return "";
                    String resp = res.body().string();
                    Matcher um = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(resp);
                    if (um.find()) return um.group(1).replace("\\/", "/");
                }
            }
            // 兜底：页面内直接的下载链接
            Matcher dm = Pattern.compile("href=\"(https?://[^\"']+\\.(?:srt|ass|ssa|zip))\"").matcher(html);
            if (dm.find()) return dm.group(1);
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String guessLang(String window) {
        Matcher m = LANG_HINT.matcher(window);
        if (m.find()) {
            String hit = m.group(1);
            if (hit.contains("双语") || hit.contains("中英") || hit.contains("简英") || hit.contains("繁英")) return "zh_en";
            if (hit.contains("繁")) return "zh_cht";
            if (hit.contains("英")) return "en";
            return "zh_chs";
        }
        Matcher f = FORMAT_HINT.matcher(window);
        // 有格式无语言时默认简体
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
        ShieldBypass.injectCookie(h, "subhd.tv");
        return h;
    }
}

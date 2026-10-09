package com.fongmi.android.tv.subtitle;

/**
 * 字幕搜索结果：单个字幕文件的信息。
 * lang 规范化为：zh_chs（简体）、zh_cht（繁体）、en（英文）、zh_en（双语）。
 */
public class SubtitleInfo {

    private final String name;
    private final String lang;
    private final String downloadUrl;
    private final String providerId;
    private final String format;

    public SubtitleInfo(String name, String lang, String downloadUrl, String providerId) {
        this.name = name == null ? "" : name;
        this.lang = normalizeLang(lang);
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
        this.providerId = providerId == null ? "" : providerId;
        this.format = detectFormat(this.name, this.downloadUrl);
    }

    public String getName() {
        return name;
    }

    public String getLang() {
        return lang;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getFormat() {
        return format;
    }

    /**
     * 语言匹配分数：越小越符合用户偏好。
     * pref: cn_first / cn_s / cn_t / en（见 SubtitleSetting.getLang）
     */
    public int matchScore(String pref) {
        if ("cn_s".equals(pref)) {
            if ("zh_chs".equals(lang)) return 0;
            if ("zh_en".equals(lang)) return 1;
            if ("zh_cht".equals(lang)) return 2;
            return 3;
        }
        if ("cn_t".equals(pref)) {
            if ("zh_cht".equals(lang)) return 0;
            if ("zh_en".equals(lang)) return 1;
            if ("zh_chs".equals(lang)) return 2;
            return 3;
        }
        if ("en".equals(pref)) {
            if ("en".equals(lang)) return 0;
            if ("zh_en".equals(lang)) return 1;
            return 2;
        }
        // cn_first：双语优先，其次简体
        if ("zh_en".equals(lang)) return 0;
        if ("zh_chs".equals(lang)) return 1;
        if ("zh_cht".equals(lang)) return 2;
        return 3;
    }

    public static String normalizeLang(String lang) {
        if (lang == null) return "zh_chs";
        String l = lang.toLowerCase().trim();
        if (l.contains("zh_en") || l.contains("双语") || l.contains("中英")) return "zh_en";
        if (l.contains("zh_cht") || l.contains("cht") || l.contains("繁体") || l.contains("繁")) return "zh_cht";
        if (l.contains("zh_chs") || l.contains("chs") || l.contains("简体") || l.contains("简中")) return "zh_chs";
        if (l.equals("en") || l.contains("英文") || l.contains("英语")) return "en";
        if (l.contains("zh") || l.contains("中文") || l.contains("中字")) return "zh_chs";
        return "zh_chs";
    }

    private static String detectFormat(String name, String url) {
        String s = (name + " " + url).toLowerCase();
        if (s.contains(".ass")) return "ass";
        if (s.contains(".ssa")) return "ssa";
        if (s.contains(".vtt")) return "vtt";
        return "srt";
    }

    @Override
    public String toString() {
        return name + " [" + lang + "] (" + providerId + ")";
    }
}

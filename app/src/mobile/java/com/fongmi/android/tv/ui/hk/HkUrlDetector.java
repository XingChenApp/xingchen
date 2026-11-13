package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 照抄 8.83 UrlDetector + DetectUrlUtil.getNeedCheckUrl + StringUtil.removeDom：
 * 扩展名表、videoRules 正则规则（逆序匹配、"!!" 取反、"@domain=" 后段忽略）、clearTag 标签剥离。
 * videoRules 的持久化：原版存 LitePal BigTextDO(key=video_rules)，星辰等价存 SharedPreferences。
 */
public final class HkUrlDetector {
    private static final String TAG = "HkUrlDetector";

    private static final List<String> APPS = Arrays.asList(
            ".css", ".html", ".js", ".apk", ".apks", ".apk.1", ".exe", ".zip", ".rar", ".7z", ".hap", ".mtz");
    private static final List<String> HTMLS = Arrays.asList(".css", ".html", ".js", ".apk", ".exe");
    private static final List<String> VIDEOS = Arrays.asList(
            ".mp4", ".MP4", ".m3u8", ".M3U8", ".flv", ".avi", ".3gp", "mpeg", ".wmv", ".mov", ".MOV",
            "rmvb", ".dat", ".mkv", "qqBFdownload", "mime=video%2F", "=video_mp4");
    private static final List<String> MUSICS = Arrays.asList(
            ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".MP3", ".M4A", ".FLAC");
    private static final List<String> IMAGES = Arrays.asList(
            ".ico", ".png", ".PNG", ".jpg", ".JPG", ".jpeg", ".JPEG", ".gif", ".GIF", ".webp", ".svg", ".avif");
    private static final List<String> BLOCK_URLS = Arrays.asList(".php?url=http", "/?url=http");
    private static final List<String> MAKE_SURE_NOT_VIDEO_RULES = Arrays.asList(".mp4.jp", ".mp4.png", ".avif");

    private static final List<String> IMAGE_URLS = Arrays.asList(
            "hiker://images/\u4e66\u7b7e",
            "hiker://images/\u4e91\u5907\u4efd",
            "hiker://images/\u4fee\u6539",
            "hiker://images/\u5220\u9664",
            "hiker://images/\u5386\u53f2",
            "hiker://images/\u5f00\u5173",
            "hiker://images/\u6dfb\u52a0",
            "hiker://images/\u79fb\u52a8",
            "hiker://images/\u8bbe\u7f6e",
            "hiker://images/\u8c46\u74e3",
            "hiker://images/account",
            "hiker://images/backup",
            "hiker://images/bbs",
            "hiker://images/block",
            "hiker://images/bookmark",
            "hiker://images/card_bg",
            "hiker://images/code",
            "hiker://images/collection",
            "hiker://images/cover",
            "hiker://images/history",
            "hiker://images/home",
            "hiker://images/home_bg",
            "hiker://images/home_bookmark2",
            "hiker://images/home_download",
            "hiker://images/home_history2",
            "hiker://images/home_icon_add",
            "hiker://images/home_icon_bookmark",
            "hiker://images/home_icon_bookmark_group",
            "hiker://images/home_icon_code",
            "hiker://images/home_icon_edit",
            "hiker://images/home_icon_fold",
            "hiker://images/home_icon_item",
            "hiker://images/home_late",
            "hiker://images/home_pic1",
            "hiker://images/home_pic2",
            "hiker://images/home_pic3",
            "hiker://images/home_pic4",
            "hiker://images/home_setting",
            "hiker://images/home_tools",
            "hiker://images/ic_bookmark_folder",
            "hiker://images/ic_bookmark_url",
            "hiker://images/icon1",
            "hiker://images/icon2",
            "hiker://images/icon3",
            "hiker://images/icon4",
            "hiker://images/icon_360",
            "hiker://images/icon_360ai",
            "hiker://images/icon_account",
            "hiker://images/icon_add2",
            "hiker://images/icon_ai",
            "hiker://images/icon_ai_big",
            "hiker://images/icon_app3",
            "hiker://images/icon_baidu",
            "hiker://images/icon_baidu_ai",
            "hiker://images/icon_bing",
            "hiker://images/icon_bookmark_add",
            "hiker://images/icon_bottom",
            "hiker://images/icon_box",
            "hiker://images/icon_browser_history",
            "hiker://images/icon_class",
            "hiker://images/icon_clock",
            "hiker://images/icon_close1",
            "hiker://images/icon_close_2",
            "hiker://images/icon_close_2_fill",
            "hiker://images/icon_cloud6",
            "hiker://images/icon_code_view",
            "hiker://images/icon_del",
            "hiker://images/icon_del6",
            "hiker://images/icon_dev",
            "hiker://images/icon_domain_config",
            "hiker://images/icon_down5",
            "hiker://images/icon_download",
            "hiker://images/icon_download6",
            "hiker://images/icon_duck",
            "hiker://images/icon_edit6",
            "hiker://images/icon_felo",
            "hiker://images/icon_filter",
            "hiker://images/icon_find",
            "hiker://images/icon_fire",
            "hiker://images/icon_folder3",
            "hiker://images/icon_foot",
            "hiker://images/icon_fsou",
            "hiker://images/icon_good6",
            "hiker://images/icon_google",
            "hiker://images/icon_his",
            "hiker://images/icon_hot",
            "hiker://images/icon_iask",
            "hiker://images/icon_info",
            "hiker://images/icon_info_fill",
            "hiker://images/icon_isou",
            "hiker://images/icon_kfind",
            "hiker://images/icon_kuake",
            "hiker://images/icon_left5",
            "hiker://images/icon_left6",
            "hiker://images/icon_like",
            "hiker://images/icon_like_fill",
            "hiker://images/icon_menu2",
            "hiker://images/icon_menu6",
            "hiker://images/icon_meta",
            "hiker://images/icon_music3",
            "hiker://images/icon_my",
            "hiker://images/icon_my_fill",
            "hiker://images/icon_offline",
            "hiker://images/icon_phind",
            "hiker://images/icon_pic3",
            "hiker://images/icon_plugin",
            "hiker://images/icon_pure",
            "hiker://images/icon_rect",
            "hiker://images/icon_rect_fill",
            "hiker://images/icon_rect_selected",
            "hiker://images/icon_refresh",
            "hiker://images/icon_right5",
            "hiker://images/icon_right6",
            "hiker://images/icon_save",
            "hiker://images/icon_search5",
            "hiker://images/icon_search6",
            "hiker://images/icon_sel",
            "hiker://images/icon_sel5",
            "hiker://images/icon_sel_fill",
            "hiker://images/icon_select",
            "hiker://images/icon_select_fill",
            "hiker://images/icon_setting5",
            "hiker://images/icon_setting6",
            "hiker://images/icon_share5",
            "hiker://images/icon_share_green",
            "hiker://images/icon_shenma",
            "hiker://images/icon_sougou",
            "hiker://images/icon_star",
            "hiker://images/icon_star_fill",
            "hiker://images/icon_star_fill_green",
            "hiker://images/icon_star_fill_red",
            "hiker://images/icon_thinkany",
            "hiker://images/icon_time6",
            "hiker://images/icon_top",
            "hiker://images/icon_toutiao",
            "hiker://images/icon_trans",
            "hiker://images/icon_txt2",
            "hiker://images/icon_ua",
            "hiker://images/icon_unknown",
            "hiker://images/icon_video2",
            "hiker://images/icon_weibo",
            "hiker://images/icon_weixin",
            "hiker://images/icon_wuzhui",
            "hiker://images/icon_xiutan",
            "hiker://images/icon_yandex",
            "hiker://images/icon_yep",
            "hiker://images/icon_zhihu",
            "hiker://images/icon_zhihu_ai",
            "hiker://images/icon_zhiwang",
            "hiker://images/icon_zip2",
            "hiker://images/logo",
            "hiker://images/menu_back2",
            "hiker://images/menu_backup2",
            "hiker://images/menu_book2",
            "hiker://images/menu_clear2",
            "hiker://images/menu_new2",
            "hiker://images/menu_now2",
            "hiker://images/menu_rules2",
            "hiker://images/menu_search2",
            "hiker://images/placeholder",
            "hiker://images/rule_type_all",
            "hiker://images/rule_type_audio",
            "hiker://images/rule_type_comics",
            "hiker://images/rule_type_live",
            "hiker://images/rule_type_news",
            "hiker://images/rule_type_other",
            "hiker://images/rule_type_pic",
            "hiker://images/rule_type_read",
            "hiker://images/rule_type_tool",
            "hiker://images/rule_type_video",
            "hiker://images/search",
            "hiker://images/tree1",
            "hiker://images/tree2",
            "hiker://images/tree3",
            "hiker://images/tree4",
            "hiker://images/ua",
            "hiker://images/video");

    // 注：icon_mark_ad 常量在原版 clinit 里 url 串未带前缀（疑复用寄存器怪癖），按 hiker://images/icon_mark_ad 计入。
    private static final List<String> videoRules = new ArrayList<>(MAKE_SURE_NOT_VIDEO_RULES);
    private static Context appContext;

    private HkUrlDetector() {
    }

    /** 宿主绑定时调用（对应原版 initVideoRules 的存储读取时机）。 */
    public static void init(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
        loadVideoRules();
    }

    private static synchronized void loadVideoRules() {
        if (appContext == null) return;
        try {
            SharedPreferences sp = appContext.getSharedPreferences("hk_big_text", Context.MODE_PRIVATE);
            String saved = sp.getString("video_rules", null);
            if (!TextUtils.isEmpty(saved)) {
                com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(saved);
                if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
                    List<String> parsed = new ArrayList<>();
                    for (com.google.gson.JsonElement e : el.getAsJsonArray()) {
                        parsed.add(e.getAsString());
                    }
                    // 原版 initVideoRules 以 $0（非 !!）在前、$1（!!）在后的顺序归一
                    List<String> ordered = new ArrayList<>();
                    for (String r : parsed) if (!r.startsWith("!!")) ordered.add(r);
                    for (String r : parsed) if (r.startsWith("!!")) ordered.add(r);
                    videoRules.clear();
                    videoRules.addAll(ordered);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
        }
    }

    public static List<String> getVideoRules() {
        return videoRules;
    }

    /** 对应 ImageUrlMapEnum.getIdByUrl：命中内置图 url 回正数、否则 -1（星辰只需正负语义，真实 drawable 由 HkGlideUtil 按名解析）。 */
    public static int getImageIdByUrl(String url) {
        if (url == null) return -1;
        return IMAGE_URLS.contains(url) ? 1 : -1;
    }

    /** 内置图 url → 名称（hiker://images/<name> 的 <name>），非内置图回 null。 */
    public static String getImageNameByUrl(String url) {
        if (url == null || !url.startsWith("hiker://images/")) return null;
        return getImageIdByUrl(url) > 0 ? url.substring("hiker://images/".length()) : null;
    }

    /** 照抄 UrlDetector.clearTag：先剥 x5Play:// 前缀，再逐个 replaceOnce 24 个标记，末尾正则剥 #threads=N#。 */
    public static String clearTag(String url) {
        if (TextUtils.isEmpty(url)) return url;
        String out = url;
        if (out.startsWith("x5Play://")) out = out.replaceFirst(Pattern.quote("x5Play://"), "");
        String[] tags = {
                "#ignoreVideo=true#", "#isVideo=true#", "#ignoreImg=true#", "#immersiveTheme#",
                "#noRecordHistory#", "#noHistory#", "#noLoading#", "#isMusic=true#", "#ignoreMusic=true#",
                "#autoPage#", "#autoReader#", "#pre#", "#noPre#", "#fullTheme#", "#readTheme#",
                "#gameTheme#", "#noRefresh#", "#background#", "#autoCache#", "#cacheOnly#",
                "#originalSize#", "#memoryPage#", "#fastPlayMode#", "#checkMetadata=false#"};
        for (String tag : tags) {
            int idx = out.indexOf(tag);
            if (idx >= 0) out = out.substring(0, idx) + out.substring(idx + tag.length());
        }
        return out.replaceAll("#threads=\\d{1,2}#", "");
    }

    /** 照抄 UrlDetector.isImage。 */
    public static boolean isImage(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (url.startsWith("x5Play://")) return false;
        if (url.contains("@rule=")) return false;
        if (url.contains("@lazyRule=")) return false;
        if (url.contains("isVideo=true")) return false;
        if (getImageIdByUrl(url) > 0) return true;
        for (String app : APPS) {
            if (url.endsWith(app)) return false;
        }
        String dom = removeDom(url);
        if (dom.contains("ignoreImg=true")) return false;
        for (String img : IMAGES) {
            if (dom.contains(img)) return true;
        }
        return false;
    }

    /** 照抄 UrlDetector.isMusic。 */
    public static boolean isMusic(String url) {
        if (TextUtils.isEmpty(url)) return false;
        if (url.contains("isVideo=true")) return false;
        if (url.contains("@rule=")) return false;
        if (url.contains("@lazyRule=")) return false;
        if (url.contains("#ignoreMusic=true#")) return false;
        if (url.contains("isMusic=true")) return true;
        for (String app : APPS) {
            if (url.endsWith(app)) return false;
        }
        String dom = removeDom(url);
        for (String music : MUSICS) {
            if (dom.contains(music)) return true;
        }
        return false;
    }

    /** 照抄 UrlDetector.isVideoOrMusic(url) = isVideoOrMusic(url, false)。 */
    public static boolean isVideoOrMusic(String url) {
        return isVideoOrMusic(url, false);
    }

    /** 照抄 UrlDetector.isVideoOrMusic(url, checkMusic)。 */
    public static boolean isVideoOrMusic(String url, boolean checkMusic) {
        if (TextUtils.isEmpty(url)) return false;
        if (url.contains("isVideo=true")) return true;
        if (url.contains("isMusic=true")) return true;
        if (url.contains("ignoreVideo=true")) return false;
        if (url.contains("#ignoreMusic=true#")) return false;
        if (url.startsWith("x5Play://")) return true;
        if (url.contains("@rule=")) return false;
        if (url.contains("@lazyRule=")) return false;
        if (url.startsWith("rtmp://")) return true;
        if (url.startsWith("rtsp://")) return true;
        if (url.contains("video://")) return true;
        String check = getNeedCheckUrl(url);
        for (String rule : MAKE_SURE_NOT_VIDEO_RULES) {
            if (check.contains(rule)) return false;
        }
        for (int i = videoRules.size() - 1; i >= 0; i--) {
            String rule = videoRules.get(i);
            boolean negated = false;
            if (rule.startsWith("!!")) {
                rule = rule.substring(2);
                negated = true;
            }
            String pattern = rule.split("@domain=")[0];
            boolean matched;
            try {
                matched = Pattern.matches(pattern, check);
            } catch (Exception e) {
                Log.e(TAG, "hk error", e);
                continue;
            }
            if (matched) return !negated;
        }
        for (String block : BLOCK_URLS) {
            if (check.contains(block)) return false;
        }
        for (String app : APPS) {
            if (check.endsWith(app)) return false;
        }
        if (checkMusic) {
            for (String music : MUSICS) {
                if (check.contains(music)) return true;
            }
        }
        for (String video : VIDEOS) {
            if (check.contains(video)) return true;
        }
        return false;
    }

    /** 照抄 StringUtil.removeDom：剥 scheme 后去掉域名段（异常回原串）。 */
    public static String removeDom(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            String s = url.replaceFirst("http://", "").replaceFirst("https://", "");
            String[] parts = s.split("/");
            if (parts.length <= 1) return s;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sb.append('/');
                sb.append(parts[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "hk error", e);
            return url;
        }
    }

    /** 照抄 DetectUrlUtil.getNeedCheckUrl：剥全部 scheme 后去掉域名段。 */
    public static String getNeedCheckUrl(String url) {
        String s = url.replace("http://", "").replace("https://", "");
        if (!s.contains("/")) return s;
        String[] parts = s.split("/");
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) sb.append('/');
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return s;
    }
}

package com.example.hikerview.ui.browser.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.alibaba.fastjson.JSON;
import com.example.hikerview.constants.ImageUrlMapEnum;
import com.example.hikerview.constants.Media;
import com.example.hikerview.ui.browser.util.HttpRequestUtil;
import com.example.hikerview.ui.download.VideoFormat;
import com.example.hikerview.utils.FileUtil;
import com.example.hikerview.utils.StringUtil;
import com.example.hikerview.utils.ToastMgr;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.hk.HkJsRuntime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（jadx 官包源）——已落纯判定族：静态名单、
 * isVideoOrMusic/isMusic/isImage/clearTag/isTs/hasKey，逐字照官；
 * 规则持久化族 initVideoRules/addVideoRule(s)/removeVideoRule/
 * updateVideoRule/saveVideoRules 亦已照官落码：官走 LitePal
 * BigTextDO(key=video_rules)，咱家树内无 BigTextDO 类、无 LitePal
 * 运行时，等价存储面接 com.fongmi 侧 HkUrlDetector 既定的
 * SharedPreferences 文件 "hk_big_text" 同名键 "video_rules"
 * （值同为规则串的 JSON 数组，官 fastjson 序列化照旧），上下文取
 * App.get()（官 LitePal 为全局存储、无 Context 入参，此为等价接法）。
 * 记名差异：①videoRules 静态初值以官 init 后的默认状态一条
 * .*\.mp4.* 初始化；②clearTag 的 StringUtils.replaceOnce 以等义内联落。
 * ④团补记：getMediaType/isVideo 已随嗅探栈齐备照官落码（官
 * UrlDetector.smali:767-1633/:2238-2403）——返回类 constants.Media、
 * DetectUrlUtil.detectVideoComplex、HttpRequestUtil 均已同批转写；
 * 其中规则 @replace= 支官调 JSEngine.getInstance().evalJS(串, 址)，
 * 咱家等价单例面经核在位（HkJsRuntime.getInstance() 回最近领用实例、
 * 其 evalJS(String, String) 为官同名法转写），照官接该面、不改桥架构；
 * 记名一处形态差：官 JSEngine 为真单例恒非空，咱家 getInstance()
 * 在无实例被领用时可回 null，此时该支抛 NPE 经官本有的逐规则
 * catch(Exception)→Timber.e 吞掉续行，失败形态与官一致。
 * isVideo 经 FileUtil.getFormatedFileSize 显示体积，该法咱家既有
 * 转写与官本体有既存格式差（官 B/KB 档用 "#" 格式），属 FileUtil
 * 旧批转写差、本团未动该文件，记名待 FileUtil 对官批处理。
 */
public class UrlDetector {

    private static List<String> apps = asList(".css", ".html", ".js", ".apk", ".apks", ".apk.1", ".exe", ".zip", ".rar", ".7z", ".hap", ".mtz");
    private static List<String> htmls = asList(".css", ".html", ".js", ".apk", ".exe");
    public static List<String> videos = asList(".mp4", ".MP4", ".m3u8", ".M3U8", ".flv", ".avi", ".3gp", "mpeg", ".wmv", ".mov", ".MOV", "rmvb", ".dat", ".mkv", "qqBFdownload", "mime=video%2F", "=video_mp4");
    private static List<String> musics = asList(".mp3", ".wav", ".ogg", ".flac", ".m4a", ".MP3", ".M4A", ".FLAC");
    private static List<String> images = asList(".ico", ".png", ".PNG", ".jpg", ".JPG", ".jpeg", ".JPEG", ".gif", ".GIF", ".webp", ".svg", ".avif");
    private static List<String> blockUrls = asList(".php?url=http", "/?url=http");
    private static List<String> makeSureNotVideoRules = asList(".mp4.jp", ".mp4.png", ".avif");
    private static List<String> videoRules = Collections.synchronizedList(new ArrayList<>(Arrays.asList(".*\\.mp4.*")));

    private static <T> List<T> asList(T... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    public static List<String> getVideoRules() {
        return videoRules;
    }

    private static SharedPreferences videoRulesPrefs() {
        return App.get().getSharedPreferences("hk_big_text", Context.MODE_PRIVATE);
    }

    public static void initVideoRules() {
        String saved = videoRulesPrefs().getString("video_rules", null);
        videoRules.clear();
        if (saved == null || StringUtil.isEmpty(saved)) {
            videoRules.add(".*\\.mp4.*");
            saveVideoRules();
            return;
        }
        List<String> list = null;
        try {
            list = JSON.parseArray(saved, String.class);
        } catch (Exception e) {
            Timber.e(e);
        }
        if (list != null && !list.isEmpty()) {
            List<String> list2 = new ArrayList<>();
            for (String str : list) {
                if (!str.startsWith("!!")) {
                    list2.add(str);
                }
            }
            List<String> list3 = new ArrayList<>();
            for (String str : list) {
                if (str.startsWith("!!")) {
                    list3.add(str);
                }
            }
            if (!list2.isEmpty()) {
                videoRules.addAll(new HashSet<>(list2));
            }
            if (!list3.isEmpty()) {
                videoRules.addAll(new HashSet<>(list3));
            }
        }
    }

    public static void addVideoRule(Context context, String str) {
        if (StringUtil.isEmpty(str)) {
            return;
        }
        if (videoRules.contains(str)) {
            ToastMgr.shortBottomCenter(context, "规则和已有规则重复");
            return;
        }
        videoRules.add(str);
        saveVideoRules();
    }

    public static int addVideoRules(Context context, List<String> list, List<String> list2) {
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int i = 0;
        for (String str : list) {
            if (videoRules.contains(str)) {
                continue;
            }
            videoRules.add(str);
            list2.add(str);
            i++;
        }
        if (i > 0) {
            saveVideoRules();
        }
        return i;
    }

    public static void removeVideoRule(String str) {
        if (StringUtil.isEmpty(str)) {
            return;
        }
        videoRules.remove(str);
        saveVideoRules();
    }

    public static void updateVideoRule(String str, String str2) {
        if (StringUtil.isEmpty(str)) {
            return;
        }
        videoRules.remove(str);
        if (!videoRules.contains(str2)) {
            videoRules.add(str2);
        }
        saveVideoRules();
    }

    private static void saveVideoRules() {
        videoRulesPrefs().edit().putString("video_rules", JSON.toJSONString(videoRules)).commit();
    }

    public static boolean isImage(String str) {
        if (!StringUtil.isEmpty(str) && !str.startsWith("x5Play://") && !str.contains("@rule=") && !str.contains("@lazyRule=") && !str.contains("isVideo=true")) {
            if (ImageUrlMapEnum.getIdByUrl(str) > 0) {
                return true;
            }
            Iterator<String> it2 = apps.iterator();
            while (it2.hasNext()) {
                if (str.endsWith(it2.next())) {
                    return false;
                }
            }
            String removeDom = StringUtil.removeDom(str);
            if (removeDom.contains("ignoreImg=true")) {
                return false;
            }
            Iterator<String> it3 = images.iterator();
            while (it3.hasNext()) {
                if (removeDom.contains(it3.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isMusic(String str, String str2) {
        if (StringUtil.isEmpty(str)) {
            return isMusic(str2);
        }
        Iterator<String> it2 = videos.iterator();
        while (it2.hasNext()) {
            if (str.endsWith(it2.next())) {
                return false;
            }
        }
        return isMusic(str2);
    }

    public static boolean isMusic(String str) {
        if (!StringUtil.isEmpty(str) && !str.contains("isVideo=true") && !str.contains("@rule=") && !str.contains("@lazyRule=") && !str.contains("#ignoreMusic=true#")) {
            if (str.contains("isMusic=true")) {
                return true;
            }
            Iterator<String> it2 = apps.iterator();
            while (it2.hasNext()) {
                if (str.endsWith(it2.next())) {
                    return false;
                }
            }
            String removeDom = StringUtil.removeDom(str);
            Iterator<String> it3 = musics.iterator();
            while (it3.hasNext()) {
                if (removeDom.contains(it3.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String clearTag(String str) {
        if (StringUtil.isEmpty(str)) {
            return str;
        }
        if (str.startsWith("x5Play://")) {
            str = replaceOnce(str, "x5Play://", "");
        }
        String[] strArr = {"#ignoreVideo=true#", "#isVideo=true#", "#ignoreImg=true#", "#immersiveTheme#", "#noRecordHistory#", "#noHistory#", "#noLoading#", "#isMusic=true#", "#ignoreMusic=true#", "#autoPage#", "#autoReader#", "#pre#", "#noPre#", "#fullTheme#", "#readTheme#", "#gameTheme#", "#noRefresh#", "#background#", "#autoCache#", "#cacheOnly#", "#originalSize#", "#memoryPage#", "#fastPlayMode#", "#checkMetadata=false#"};
        for (int i = 0; i < 24; i++) {
            str = replaceOnce(str, strArr[i], "");
        }
        return str.replaceAll("#threads=\\d{1,2}#", "");
    }

    private static String replaceOnce(String str, String search, String replacement) {
        int idx = str.indexOf(search);
        if (idx < 0) {
            return str;
        }
        return str.substring(0, idx) + replacement + str.substring(idx + search.length());
    }

    public static boolean isTs(String... strArr) {
        return hasKey(".ts", strArr);
    }

    public static boolean hasKey(String str, String... strArr) {
        for (String str2 : strArr) {
            if (!StringUtil.isEmpty(str2) && str2.contains(str)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVideoOrMusic(String str) {
        return isVideoOrMusic(str, false);
    }

    public static boolean isVideoOrMusic(String str, boolean z) {
        if (StringUtil.isEmpty(str)) {
            return false;
        }
        if (str.contains("isVideo=true") || str.contains("isMusic=true")) {
            return true;
        }
        if (!str.contains("ignoreVideo=true") && !str.contains("#ignoreMusic=true#")) {
            if (str.startsWith("x5Play://")) {
                return true;
            }
            if (!str.contains("@rule=") && !str.contains("@lazyRule=")) {
                if (str.startsWith("rtmp://") || str.startsWith("rtsp://") || str.contains("video://")) {
                    return true;
                }
                String needCheckUrl = DetectUrlUtil.getNeedCheckUrl(str);
                Iterator<String> it2 = makeSureNotVideoRules.iterator();
                while (it2.hasNext()) {
                    if (needCheckUrl.contains(it2.next())) {
                        return false;
                    }
                }
                boolean ruleNegated = false;
                for (int size = videoRules.size() - 1; size >= 0; size--) {
                    String str2 = videoRules.get(size);
                    try {
                        if (str2.startsWith("!!")) {
                            str2 = str2.substring(2);
                            ruleNegated = true;
                        } else {
                            ruleNegated = false;
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                    if (Pattern.matches(str2.split("@domain=")[0], needCheckUrl)) {
                        return !ruleNegated;
                    }
                }
                Iterator<String> it3 = blockUrls.iterator();
                while (it3.hasNext()) {
                    if (needCheckUrl.contains(it3.next())) {
                        return false;
                    }
                }
                Iterator<String> it4 = apps.iterator();
                while (it4.hasNext()) {
                    if (needCheckUrl.endsWith(it4.next())) {
                        return false;
                    }
                }
                if (!z) {
                    Iterator<String> it5 = musics.iterator();
                    while (it5.hasNext()) {
                        if (needCheckUrl.contains(it5.next())) {
                            return true;
                        }
                    }
                }
                Iterator<String> it6 = videos.iterator();
                while (it6.hasNext()) {
                    if (needCheckUrl.contains(it6.next())) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    public static Media getMediaType(String str, String str2, Map<String, String> map, String str3) {
        String method = StringUtil.isEmpty(str3) ? "GET" : str3;
        if (str2.startsWith("rtmp://")) {
            Media media = new Media(Media.VIDEO);
            media.setType("rtmp");
            return media;
        }
        if (str2.startsWith("rtsp://")) {
            Media media = new Media(Media.VIDEO);
            media.setType("rtsp");
            return media;
        }
        if (str2.contains("isVideo=true")) {
            Media media = new Media(Media.VIDEO);
            media.setType("isVideo");
            return media;
        }
        if (str2.contains("isMusic=true")) {
            Media media = new Media(Media.MUSIC);
            media.setType("isMusic");
            return media;
        }
        String needCheckUrl = DetectUrlUtil.getNeedCheckUrl(str2);
        if (StringUtil.isEmpty(needCheckUrl) || needCheckUrl.length() < 3) {
            Media media = new Media(Media.HTML);
            media.setType("");
            return media;
        }
        for (String rule : makeSureNotVideoRules) {
            if (str2.contains(rule)) {
                Media media = new Media(Media.OTHER);
                media.setType(rule);
                return media;
            }
        }
        for (int size = videoRules.size() - 1; size >= 0; size--) {
            String videoRule = videoRules.get(size);
            boolean negated;
            String replace = null;
            try {
                if (videoRule.startsWith("!!")) {
                    videoRule = videoRule.substring(2);
                    negated = true;
                } else {
                    negated = false;
                }
                String checkUrl = videoRule.length() >= 15 ? str2 : needCheckUrl;
                String[] replaceSplit = videoRule.split("@replace=");
                if (replaceSplit.length > 1) {
                    videoRule = replaceSplit[0];
                    replace = replaceSplit[1];
                }
                String[] domainSplit = videoRule.split("@domain=");
                if (domainSplit.length >= 2 && StringUtil.isNotEmpty(str)) {
                    if (StringUtil.getHome(str).contains(domainSplit[1]) && Pattern.matches(domainSplit[0], checkUrl)) {
                        Media media = new Media(negated ? Media.OTHER : Media.VIDEO);
                        media.setType(negated ? "other" : "video");
                        if (StringUtil.isNotEmpty(replace)) {
                            media.setRealUrl(HkJsRuntime.getInstance().evalJS(replace, str2));
                        }
                        return media;
                    }
                } else if (Pattern.matches(videoRule, checkUrl)) {
                    Media media = new Media(negated ? Media.OTHER : Media.VIDEO);
                    media.setType(negated ? "other" : "video");
                    if (StringUtil.isNotEmpty(replace)) {
                        media.setRealUrl(HkJsRuntime.getInstance().evalJS(replace, str2));
                    }
                    return media;
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        for (String blockUrl : blockUrls) {
            if (needCheckUrl.contains(blockUrl)) {
                Media media = new Media(Media.OTHER);
                media.setType(blockUrl);
                return media;
            }
        }
        String noQueryUrl = needCheckUrl.split("\\?")[0];
        for (String html : htmls) {
            if (noQueryUrl.contains(html)) {
                Media media = new Media(Media.HTML);
                media.setType(html);
                return media;
            }
        }
        for (String image : images) {
            if (needCheckUrl.contains(image)) {
                Media media = new Media(Media.IMAGE);
                media.setType(image);
                return media;
            }
        }
        if (!needCheckUrl.contains("#ignoreVideo=true#")) {
            for (String video : videos) {
                if (needCheckUrl.contains(video)) {
                    Media media = new Media(Media.VIDEO);
                    media.setType(video);
                    return media;
                }
            }
        }
        if (!needCheckUrl.contains("#ignoreMusic=true#")) {
            for (String music : musics) {
                if (needCheckUrl.contains(music)) {
                    Media media = new Media(Media.MUSIC);
                    media.setType(music);
                    return media;
                }
            }
        }
        if ("POST".equalsIgnoreCase(method)) {
            Media media = new Media(Media.OTHER);
            media.setType("post");
            return media;
        }
        if (str2.contains("haikuoshijie.cn")) {
            Media media = new Media(Media.HTML);
            media.setType("html");
            return media;
        }
        if (str2.contains("captcha") || str2.contains("check_code") || str2.contains("check-code")) {
            Media media = new Media(Media.OTHER);
            media.setType("captcha");
            return media;
        }
        if (str2.contains("/api/") || str2.contains("/api?")) {
            Media media = new Media(Media.OTHER);
            media.setType("api");
            return media;
        }
        if (str2.contains("/login")) {
            Media media = new Media(Media.OTHER);
            media.setType("login");
            return media;
        }
        Media isVideo = isVideo(str2, map);
        if (isVideo != null) {
            return isVideo;
        }
        Media media = new Media(Media.OTHER);
        media.setType("");
        return media;
    }

    private static Media isVideo(String str, Map<String, String> map) {
        if (!StringUtil.isEmpty(str) && !str.contains("ignoreVideo=true")) {
            String fileExtensionFromUrl = HttpRequestUtil.getFileExtensionFromUrl(str);
            if ("m3u8".equals(fileExtensionFromUrl)) {
                return new Media(Media.VIDEO, fileExtensionFromUrl);
            }
            try {
                VideoInfo detectVideoComplex = DetectUrlUtil.detectVideoComplex(str, str, map);
                if (detectVideoComplex != null) {
                    VideoFormat videoFormat = detectVideoComplex.getVideoFormat();
                    if (videoFormat != null) {
                        String sizeStr = "";
                        if (detectVideoComplex.getSize() > 0) {
                            sizeStr = FileUtil.getFormatedFileSize(detectVideoComplex.getSize());
                        }
                        if ("player/m3u8".equals(videoFormat.getName())) {
                            return new Media(Media.VIDEO, "m3u8", sizeStr);
                        }
                        if (isVideoOrMusic("." + videoFormat.getName())) {
                            return new Media(Media.VIDEO, videoFormat.getName(), sizeStr);
                        }
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return null;
    }
}

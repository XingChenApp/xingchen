package com.example.hikerview.ui.browser.model;

import com.example.hikerview.constants.ImageUrlMapEnum;
import com.example.hikerview.utils.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（jadx 官包源）——本批落纯判定族：静态名单、
 * isVideoOrMusic/isMusic/isImage/clearTag/isTs/hasKey，逐字照官。
 * 记名差异：①videoRules 官由 initVideoRules 从 LitePal 存储灌入
 * （空存储时默认一条 .*\.mp4.*），星辰未接该存储，此处以官默认
 * 状态静态初始化，规则增删的持久化族未落；②getMediaType/isVideo
 * 依赖官嗅探栈（DetectUrlUtil.detectVideoComplex 等）未落；
 * ③clearTag 的 StringUtils.replaceOnce 以等义内联落。
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
}

package com.example.hikerview.ui.download.util;

import android.text.TextUtils;
import android.util.Log;

import com.example.hikerview.ui.download.DownloadManager;
import com.example.hikerview.ui.download.VideoFormat;
import com.example.hikerview.utils.FileUtil;
import com.example.hikerview.utils.HttpUtil;
import com.example.hikerview.utils.ShareUtil;
import com.example.hikerview.utils.StringUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * 照官逐字转写（官包 smali_classes2/com/example/hikerview/ui/download/util/
 * VideoFormatUtil.smali 全文，八法）：嗅探栈一员——detectVideoFormat 按
 * Content-Type/扩展名/流媒体体积判格式，isStreamContent 供
 * DetectUrlUtil.detectVideoComplex 调用。静态两表照官 &lt;clinit&gt; 逐串落
 * （官 jadx 影子里的库常量名已按 smali 常量串还原为字面值）。
 *
 * <p>Content-Disposition 支已补落（hk-14 五团）：私有 getVideoFormatByName
 * 首步「Content-Disposition 头 → DownloadManager.getDispositionFileName
 * 取文件名再判格式」照官接回——utils.contentdisposition 解析库已整套
 * 转写落码（25 类），DownloadManager.getDispositionFileName 已部分转写
 * 落 ui/download/DownloadManager（仅该法＋其类内私帮 decodeUrl）。
 *
 * <p>另记名：本类经 FileUtil.getExtension/getResourceName 调咱家既有转写，
 * 该两法与官 FileUtil 本体有既存差（官先剥 ?/## 再取末段、getResourceName
 * 官回解码后末段全名或 null），属 FileUtil 旧批转写差、本团未动该文件，
 * 嗅探链经此两法的分支行为随之有差，待 FileUtil 对官批统一处理。
 */
public class VideoFormatUtil {

    private static final String TAG = "VideoFormatUtil";

    private static final List<String> videoExtensionList = Arrays.asList("player/m3u8", "mp4", "ts", "mp3", "m4a", "flv", "mpeg");

    public static final List<VideoFormat> videoFormatList = Arrays.asList(
            new VideoFormat("player/m3u8", Arrays.asList("application/octet-stream", "application/vnd.apple.mpegurl", "application/mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl", "application/x-mpeg")),
            new VideoFormat("mp4", Arrays.asList("video/mp4", "application/mp4", "video/h264")),
            new VideoFormat("ts", Arrays.asList("video/vnd.dlna.mpeg-tts", "video/mpeg")),
            new VideoFormat("flv", Arrays.asList("video/x-flv")),
            new VideoFormat("f4v", Arrays.asList("video/x-f4v")),
            new VideoFormat("mpeg", Arrays.asList("video/vnd.mpegurl")));

    public static VideoFormat getVideoFormat(String str, String str2) {
        Set<String> extensions = ShareUtil.getExtensions();
        String str3;
        if (str == null || !str.contains(".")) {
            str3 = null;
        } else {
            if (str.contains(".m3u8")) {
                return new VideoFormat("m3u8", Collections.singletonList("m3u8"));
            }
            str3 = FileUtil.getExtension(str);
            if (StringUtil.isNotEmpty(str3) && str3.length() < 10 && extensions.contains(str3)) {
                return new VideoFormat(str3, Collections.singletonList(str3));
            }
            if (StringUtil.isNotEmpty(str3) && str3.length() > 1 && str.endsWith(".apk.1")) {
                return new VideoFormat("apk", Collections.singletonList("apk"));
            }
        }
        String resourceName = FileUtil.getResourceName(str2);
        if (StringUtil.isNotEmpty(resourceName) && resourceName.contains(".")) {
            if (str2.contains(".m3u8")) {
                return new VideoFormat("m3u8", Collections.singletonList("m3u8"));
            }
            String substring = resourceName.substring(resourceName.lastIndexOf(".") + 1);
            if (StringUtil.isNotEmpty(substring) && extensions.contains(substring)) {
                return new VideoFormat(substring, Collections.singletonList(substring));
            }
        }
        if (!StringUtil.isNotEmpty(str3) || isNumber(str3)) {
            return null;
        }
        return new VideoFormat(str3, Collections.singletonList(str3));
    }

    public static VideoFormat getVideoFormatAnyway(String str, String str2) {
        return getVideoFormatAnyway(str, str2, true);
    }

    public static VideoFormat getVideoFormatAnyway(String str, String str2, boolean z) {
        String str3;
        if (str == null || !str.contains(".")) {
            str3 = null;
        } else {
            if (str.contains(".m3u8") && z) {
                return new VideoFormat("m3u8", Collections.singletonList("m3u8"));
            }
            str3 = FileUtil.getExtension(str);
        }
        if (!isNumber(str3) && StringUtil.isNotEmpty(str3) && str3.length() < 10 && str3.length() > 1) {
            return new VideoFormat(str3, Collections.singletonList(str3));
        }
        String resourceName = FileUtil.getResourceName(str2);
        if (StringUtil.isNotEmpty(resourceName) && resourceName.contains(".")) {
            if (str2.contains(".m3u8") && z) {
                return new VideoFormat("m3u8", Collections.singletonList("m3u8"));
            }
            str3 = resourceName.substring(resourceName.lastIndexOf(".") + 1);
        }
        if (!isNumber(str3) && StringUtil.isNotEmpty(str3) && str3.length() < 10 && str3.length() > 1) {
            return new VideoFormat(str3, Collections.singletonList(str3));
        }
        if (str != null && str.contains(".apk.")) {
            return new VideoFormat("apk", Collections.singletonList("apk"));
        }
        if (str2 == null || !str2.contains(".apk.")) {
            return null;
        }
        return new VideoFormat("apk", Collections.singletonList("apk"));
    }

    private static boolean isNumber(String str) {
        if (str != null) {
            try {
                if (!str.isEmpty()) {
                    Integer.parseInt(str);
                    return true;
                }
            } catch (Exception e) {
                Timber.e(e);
            }
        }
        return false;
    }

    private static VideoFormat getVideoFormatByName(Map<String, List<String>> map, String str, String str2) {
        String dispositionFileName = null;
        try {
            if (map != null && map.containsKey("Content-Disposition")) {
                dispositionFileName = DownloadManager.getDispositionFileName(map.get("Content-Disposition").get(0));
            }
            VideoFormat videoFormat = getVideoFormat(dispositionFileName, null);
            if (videoFormat != null) {
                return videoFormat;
            }
            return getVideoFormat(str, str2);
        } catch (Exception e) {
            Timber.e(e);
            return null;
        }
    }

    public static VideoFormat detectVideoFormat(String str, String str2, Map<String, List<String>> map) {
        if (map == null) {
            map = new HashMap<>();
        }
        List<String> contentTypeList = map.get("Content-Type");
        if (contentTypeList == null || contentTypeList.isEmpty()) {
            return getVideoFormatByName(map, str, str2);
        }
        String contentType = contentTypeList.get(0);
        try {
            String path = new URL(str2).getPath();
            String extension = FileUtil.getExtension(path);
            Log.d(TAG, "detectVideoFormat: " + extension);
            if ("mp4".equals(extension)) {
                contentType = "video/mp4";
            } else if ("m3u8".equals(extension) || (HttpUtil.isFuckImage(str2, contentType) && str2.contains(".m3u8"))) {
                return videoFormatList.get(0);
            }
        } catch (MalformedURLException e) {
            Timber.e(e);
            return getVideoFormat(str, str2);
        }
        contentType = contentType.toLowerCase().replace("[", "").replace("]", "").split(";")[0];
        Log.d(TAG, "detectVideoFormat: " + contentType);
        if (isStream(contentType) && !str2.contains("m3u8")) {
            VideoFormat videoFormatByName = getVideoFormatByName(map, str, str2);
            if (videoFormatByName != null) {
                return videoFormatByName;
            }
        }
        if (!TextUtils.isEmpty(contentType)) {
            for (VideoFormat videoFormat : videoFormatList) {
                for (String mime : videoFormat.getMimeList()) {
                    if (contentType.contains(mime)) {
                        return videoFormat;
                    }
                }
            }
            String extension = ShareUtil.getExtension(str2, contentType);
            if (StringUtil.isNotEmpty(extension)) {
                return new VideoFormat(extension, Collections.singletonList(contentType));
            }
        }
        VideoFormat videoFormatByName2 = getVideoFormatByName(map, str, str2);
        if (videoFormatByName2 != null) {
            return videoFormatByName2;
        }
        if (isStream(contentType)) {
            long contentLength = 0;
            if (map.containsKey("Content-Length") && map.get("Content-Length").size() > 0) {
                try {
                    contentLength = Long.parseLong(map.get("Content-Length").get(0));
                } catch (NumberFormatException e) {
                    Timber.e(e);
                    Timber.d(e, "NumberFormatException");
                }
            }
            if (contentLength > 209715200L) {
                return videoFormatList.get(1);
            }
        }
        return getVideoFormatAnyway(str, str2);
    }

    public static boolean isStreamContent(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return isStream(str.toLowerCase().replace("[", "").replace("]", "").split(";")[0]);
    }

    public static boolean isStream(String str) {
        return "application/octet-stream".equals(str) || "application/oct-stream".equals(str) || "multipart/form-data".equals(str);
    }
}

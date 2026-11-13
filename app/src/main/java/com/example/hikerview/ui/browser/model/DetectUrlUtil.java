package com.example.hikerview.ui.browser.model;

import com.example.hikerview.ui.browser.util.CollectionUtil;
import com.example.hikerview.ui.browser.util.HttpRequestUtil;
import com.example.hikerview.ui.browser.util.UUIDUtil;
import com.example.hikerview.ui.download.VideoFormat;
import com.example.hikerview.ui.download.util.VideoFormatUtil;
import com.example.hikerview.utils.StringUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * 照官 8.83 本体移植（官包 smali_classes2/com/example/hikerview/ui/browser/
 * model/DetectUrlUtil.smali 全文逐字对）：getNeedCheckUrl（去协议头、去域名段，
 * 余下路径参与判定；官 ScanDeviceUtil.HTTP 即 "http://" 字面值）先行落码；
 * ④团嗅探栈齐后 detectVideoComplex 随落——链路照官：HEAD 请求
 * （HttpRequestUtil.performHeadRequest）→ 头表展平 → 无 Content-Type 回 null →
 * VideoFormatUtil.detectVideoFormat 判格式 → 流媒体型须真实址含已知扩展名
 * 否则回 null → 组 VideoInfo（player/m3u8 型 detectImageType="m3u8"，
 * 余型以 Content-Length 折 MB 串并记 size）。静态 filters/images 两表与
 * DetectListener 接口照官一并落（filters/images 官类内除建表外无消费点，
 * 照官原样保留）。
 */
public class DetectUrlUtil {

    public static volatile List<String> filters = CollectionUtil.asList(".css", ".html", ".js", ".ttf", ".ico", ".png", ".jpg", ".jpeg", ".cnzz");
    public static volatile List<String> images = CollectionUtil.asList("mp4", "m3u8", ".flv", ".avi", ".3gp", "mpeg", ".wmv", ".mov", "rmvb", ".dat", "qqBFdownload", ".mp3", ".wav", ".ogg", ".flac", ".m4a");

    public interface DetectListener {
        void onSuccess(VideoInfo videoInfo);
    }

    public static String getNeedCheckUrl(String str) {
        String replace = str.replace("http://", "").replace("https://", "");
        if (!replace.contains("/")) {
            return replace;
        }
        String[] split = replace.split("/");
        if (split.length > 1) {
            return StringUtil.listToString(Arrays.asList(split), 1, "/");
        }
        return (split.length >= 1 && new StringBuilder().append(split[0]).append("/").toString().equals(replace)) ? "" : replace;
    }

    public static VideoInfo detectVideoComplex(String str, String str2, Map<String, String> map) throws IOException {
        HttpRequestUtil.HeadRequestResponse performHeadRequest = HttpRequestUtil.performHeadRequest(str2, map);
        if (performHeadRequest == null) {
            return null;
        }
        String realUrl = performHeadRequest.getRealUrl();
        Map<String, List<String>> headerMap = performHeadRequest.getHeaderMap();
        Map<String, String> hashMap = new HashMap<>();
        for (String key : headerMap.keySet()) {
            hashMap.put(key, headerMap.get(key).toString());
        }
        if (!hashMap.containsKey("Content-Type")) {
            return null;
        }
        VideoFormat detectVideoFormat = VideoFormatUtil.detectVideoFormat(null, realUrl, headerMap);
        if (detectVideoFormat == null) {
            return null;
        }
        if (VideoFormatUtil.isStreamContent(hashMap.get("Content-Type"))) {
            String[] strArr = {".mp4", ".m3u8", ".mp3", ".mkv", ".flv", ".avi", ".rmvb"};
            boolean found = false;
            for (int i = 0; i < strArr.length; i++) {
                if (realUrl.contains(strArr[i])) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return null;
            }
        }
        VideoInfo videoInfo = new VideoInfo();
        if ("player/m3u8".equals(detectVideoFormat.getName())) {
            videoInfo.setDetectImageType("m3u8");
        } else {
            long size = 0;
            if (hashMap.containsKey("Content-Length")) {
                try {
                    String contentLength = hashMap.get("Content-Length");
                    if (contentLength.startsWith("[")) {
                        contentLength = contentLength.replace("[", "").replace("]", "");
                    }
                    size = Long.parseLong(contentLength);
                } catch (NumberFormatException e) {
                    Timber.e(e);
                }
            }
            videoInfo.setDetectImageType(((size / 1024) / 1024) + "MB");
            videoInfo.setSize(size);
        }
        videoInfo.setUrl(realUrl);
        videoInfo.setFileName(UUIDUtil.genUUID());
        videoInfo.setVideoFormat(detectVideoFormat);
        videoInfo.setSourcePageTitle(str);
        videoInfo.setSourcePageUrl(realUrl);
        return videoInfo;
    }
}

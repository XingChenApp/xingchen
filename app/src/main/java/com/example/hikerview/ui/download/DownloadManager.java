package com.example.hikerview.ui.download;

import com.example.hikerview.utils.StringUtil;
import com.example.hikerview.utils.contentdisposition.ContentDispositionHolder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import timber.log.Timber;

/**
 * 照官部分转写（官包 smali_classes2/com/example/hikerview/ui/download/
 * DownloadManager.smali 全文 8961 行）：只落 VideoFormatUtil
 * .getVideoFormatByName 的 Content-Disposition 支要用的
 * getDispositionFileName 一法（官 smali :1620 起），及其同类私帮
 * decodeUrl 一法（官 smali :660 起，该法是 getDispositionFileName
 * 的类内唯一自有依赖、闭包为 JDK URLDecoder＋Timber）。
 *
 * <p>getDispositionFileName 闭包裁定：ContentDispositionHolder
 * （utils.contentdisposition 解析库本批已整套落码）＋StringUtil
 * .isNotEmpty（树内已有）＋decodeUrl（同类）＋Timber（树内已有），
 * 不外扩出本批范围。
 *
 * <p>未落记名（官下载宿主面，非规则范围）：本类其余全部方法未落——
 * 含下载任务管理（getInstance/init/startTask/cancelTask/removeTask 等）、
 * 线程类与 M3U8 合并/解密面（decrypt/mergeM3u8File 等）、detectUrl/
 * findFileName/getExtByContentDispositionName 等同域法；需要时另批照官落。
 */
public class DownloadManager {

    public static String getDispositionFileName(String str) {
        try {
            ContentDispositionHolder contentDispositionHolder = new ContentDispositionHolder(str);
            String filename = contentDispositionHolder.getFilename();
            if (contentDispositionHolder.getParseException() == null && StringUtil.isNotEmpty(filename)) {
                String trim = decodeUrl(filename, "UTF-8").trim();
                if (!trim.contains("UTF-8''")) {
                    return trim;
                }
                if (trim.endsWith("UTF-8''")) {
                    return trim.split("UTF-8''")[0];
                }
                return trim.split("UTF-8''")[1];
            }
        } catch (Throwable th) {
            Timber.e(th);
        }
        if (str != null && !str.isEmpty()) {
            String replaceFirst = str.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
            String[] split = replaceFirst.split(";");
            if (split.length > 1) {
                replaceFirst = split[0];
            }
            if (replaceFirst.contains(".") && !replaceFirst.contains("filename=")) {
                return decodeUrl(replaceFirst, "UTF-8").trim();
            }
        }
        return "";
    }

    private static String decodeUrl(String str, String str2) {
        try {
            str = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
            str = str.replaceAll("\\+", "%2B");
            str = URLDecoder.decode(str, str2);
        } catch (UnsupportedEncodingException e) {
            Timber.e(e);
        }
        return str;
    }
}

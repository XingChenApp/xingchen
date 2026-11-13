package com.google.android.exoplayer2.util;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;

/**
 * 照官部分转写（官包 smali_classes2/com/google/android/exoplayer2/util/
 * FileTypes.smali 全文 1206 行）：只落 HttpUtil.inferFileTypeFromResponse
 * 尾步要用的两法——inferFileTypeFromResponseHeaders 与
 * inferFileTypeFromMimeType（MIME→类型常量表 24 项照官 sparse-switch
 * 逐项落，见法内）——及全部类型常量与扩展名字段（官字段全表照落）。
 *
 * <p>未落记名：inferFileTypeFromUri 一法未落（扩展名→类型表只在该法内，
 * 本团两目标法均不经它；其表已在分团账中逐项提取备查）。扩展名字段随
 * 类字段全表落码、其消费法未落，属记名状态。
 *
 * <p>inferFileTypeFromMimeType 首步调 MimeTypes.normalizeMimeType
 * （同包部分转写类，仅落该一法），照官调用形态不改。
 */
public final class FileTypes {

    public static final int AC3 = 0;
    public static final int AC4 = 1;
    public static final int ADTS = 2;
    public static final int AMR = 3;
    private static final String EXTENSION_AAC = ".aac";
    private static final String EXTENSION_AC3 = ".ac3";
    private static final String EXTENSION_AC4 = ".ac4";
    private static final String EXTENSION_ADTS = ".adts";
    private static final String EXTENSION_AMR = ".amr";
    private static final String EXTENSION_EC3 = ".ec3";
    private static final String EXTENSION_FLAC = ".flac";
    private static final String EXTENSION_FLV = ".flv";
    private static final String EXTENSION_JPEG = ".jpeg";
    private static final String EXTENSION_JPG = ".jpg";
    private static final String EXTENSION_M2P = ".m2p";
    private static final String EXTENSION_MP3 = ".mp3";
    private static final String EXTENSION_MP4 = ".mp4";
    private static final String EXTENSION_MPEG = ".mpeg";
    private static final String EXTENSION_MPG = ".mpg";
    private static final String EXTENSION_OPUS = ".opus";
    private static final String EXTENSION_PREFIX_CMF = ".cmf";
    private static final String EXTENSION_PREFIX_M4 = ".m4";
    private static final String EXTENSION_PREFIX_MK = ".mk";
    private static final String EXTENSION_PREFIX_MP4 = ".mp4";
    private static final String EXTENSION_PREFIX_OG = ".og";
    private static final String EXTENSION_PREFIX_TS = ".ts";
    private static final String EXTENSION_PS = ".ps";
    private static final String EXTENSION_TS = ".ts";
    private static final String EXTENSION_VTT = ".vtt";
    private static final String EXTENSION_WAV = ".wav";
    private static final String EXTENSION_WAVE = ".wave";
    private static final String EXTENSION_WEBM = ".webm";
    private static final String EXTENSION_WEBVTT = ".webvtt";
    public static final int FLAC = 4;
    public static final int FLV = 5;
    static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final int JPEG = 14;
    public static final int MATROSKA = 6;
    public static final int MP3 = 7;
    public static final int MP4 = 8;
    public static final int OGG = 9;
    public static final int PS = 10;
    public static final int TS = 11;
    public static final int UNKNOWN = -1;
    public static final int WAV = 12;
    public static final int WEBVTT = 13;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    private FileTypes() {
    }

    public static int inferFileTypeFromResponseHeaders(Map<String, List<String>> responseHeaders) {
        List<String> list = responseHeaders.get(HEADER_CONTENT_TYPE);
        String mimeType = (list == null || list.isEmpty()) ? null : list.get(0);
        return inferFileTypeFromMimeType(mimeType);
    }

    public static int inferFileTypeFromMimeType(String mimeType) {
        if (mimeType == null) {
            return UNKNOWN;
        }
        switch (MimeTypes.normalizeMimeType(mimeType)) {
            case "audio/eac3-joc":
                return AC3;
            case "video/mp2p":
                return PS;
            case "video/mp2t":
                return TS;
            case "video/webm":
                return MATROSKA;
            case "audio/amr-wb":
                return AMR;
            case "image/jpeg":
                return JPEG;
            case "application/mp4":
                return MP4;
            case "text/vtt":
                return WEBVTT;
            case "audio/x-matroska":
                return MATROSKA;
            case "application/webm":
                return MATROSKA;
            case "video/x-flv":
                return FLV;
            case "audio/ac3":
                return AC3;
            case "audio/ac4":
                return AC4;
            case "audio/amr":
                return AMR;
            case "audio/mp4":
                return MP4;
            case "audio/ogg":
                return OGG;
            case "audio/wav":
                return WAV;
            case "video/mp4":
                return MP4;
            case "audio/3gpp":
                return AMR;
            case "audio/eac3":
                return AC3;
            case "audio/flac":
                return FLAC;
            case "audio/mpeg":
                return MP3;
            case "audio/webm":
                return MATROSKA;
            case "video/x-matroska":
                return MATROSKA;
            default:
                return UNKNOWN;
        }
    }
}

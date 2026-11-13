package com.google.android.exoplayer2.util;

/**
 * 照官部分转写（官包 smali_classes2/com/google/android/exoplayer2/util/
 * MimeTypes.smali 全文 2629 行、23 法 87 字段）：只落 FileTypes
 * .inferFileTypeFromMimeType 首步被引的 normalizeMimeType 一法
 * （三别名照官 sparse-switch 逐条：audio/mp3→audio/mpeg、
 * audio/x-wav→audio/wav、audio/x-flac→audio/flac，余原样回）。
 *
 * <p>未落记名：其余 22 法与全部 MIME 常量字段未落（非本团两目标法
 * 闭包；FileTypes 落码用官 smali 内的字面串、不引本类常量）。
 */
public final class MimeTypes {

    private MimeTypes() {
    }

    public static String normalizeMimeType(String mimeType) {
        switch (mimeType) {
            case "audio/x-flac":
                return "audio/flac";
            case "audio/x-wav":
                return "audio/wav";
            case "audio/mp3":
                return "audio/mpeg";
            default:
                return mimeType;
        }
    }
}

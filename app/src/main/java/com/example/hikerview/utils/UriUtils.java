package com.example.hikerview.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.Objects;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/utils/UriUtils.smali）。
 * 本批只落 getRootDir 一法（①团：ArticleListRule.deleteFiles 与 JSEngine.getFilesDir
 * 公式的共同底座），逐字照官（含 requireNonNull 与重复取目录的官形态）；其余法未落、记名在案。
 */
public class UriUtils {

    public static String getRootDir(Context context) {
        if (!Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).exists()) {
            Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).mkdirs();
        }
        return Objects.requireNonNull(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)).getAbsolutePath();
    }
}

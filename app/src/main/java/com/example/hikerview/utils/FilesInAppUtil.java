package com.example.hikerview.utils;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import timber.log.Timber;

/**
 * 照官 8.83 本体按需转写（官源 smali_classes2/com/example/hikerview/utils/FilesInAppUtil.smali）。
 * 已落 getAssetsString（SettingConfig.getPrivacyFindRule 用），逐字照官：
 * 逐行读 assets、以 "\n" 连接（首行前不加）、异常 Timber.e 后回已拼部分；
 * getUri 随 PlayerChooser 起播支补落（见其法注）。官类其余法未落、记名在案。
 */
public class FilesInAppUtil {

    public static String getAssetsString(Context context, String fileName) {
        StringBuilder builder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName)));
            int lineNum = -1;
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineNum == -1) {
                    lineNum++;
                    builder.append(line);
                } else {
                    builder.append("\n").append(line);
                }
            }
        } catch (Throwable th) {
            Timber.e(th);
        }
        return builder.toString();
    }

    /**
     * 官 getUri（.line 198-207）：路径以 "file:"/"content"/"/" 任一开头的走
     * FileProvider 取 Uri（先 replaceFirst 剥 "file://"），余形 Uri.parse 直回；
     * FileProvider 支异常 Timber.e 后回 Uri.parse。
     * 官本体 authority 取 R.string.authority（官 res/values/public.xml
     * 0x7f12002a、官值 "com.example.hikerview.provider"）——咱家 R.string 无此
     * 字段（共享文件本批禁改，已在分团账开清单），接本树既有先例
     * AutoImportHelper／fongmi FileUtil 的 packageName + ".provider"（与咱家
     * 清单 authorities="${applicationId}.provider" 同值），记名。
     */
    public static Uri getUri(Context context, String path) {
        if (!path.startsWith("file:") && !path.startsWith("content")
                && !path.startsWith("/")) {
            return Uri.parse(path);
        }
        try {
            String authority = context.getPackageName() + ".provider";
            return FileProvider.getUriForFile(context, authority,
                    new File(path.replaceFirst("file://", "")));
        } catch (Exception e) {
            Timber.e(e);
            return Uri.parse(path);
        }
    }
}

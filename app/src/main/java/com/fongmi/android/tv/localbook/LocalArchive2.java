package com.fongmi.android.tv.localbook;

import java.io.File;
import java.util.List;

/**
 * CBR/CB7 分发器：按格式把"列图/分章/判加密/解条目"转给 LocalRar / LocalSevenZ。
 * importer 与 reader 共用这一份分支，不各写一遍。
 */
public class LocalArchive2 {

    public static boolean isCbrCb7(String format) {
        return "CBR".equals(format) || "CB7".equals(format);
    }

    public static boolean isEncrypted(File f, String format) throws Exception {
        if ("CBR".equals(format)) return LocalRar.isEncrypted(f);
        return LocalSevenZ.isEncrypted(f);
    }

    public static List<LocalBook.ChapterRef> chaptersOf(File f, String format) throws Exception {
        if ("CBR".equals(format)) return LocalRar.chaptersOf(f);
        return LocalSevenZ.chaptersOf(f);
    }

    public static List<String> imagesOf(File f, LocalBook.ChapterRef ref, String format)
            throws Exception {
        if ("CBR".equals(format)) return LocalRar.imagesOf(f, ref);
        return LocalSevenZ.imagesOf(f, ref);
    }

    public static void extractTo(File f, String entryName, File target, String format)
            throws Exception {
        if ("CBR".equals(format)) LocalRar.extractTo(f, entryName, target);
        else LocalSevenZ.extractTo(f, entryName, target);
    }
}

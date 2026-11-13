package com.fongmi.android.tv.api.hk;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 环43（批C3复核订正）：8.83 RetroLoadLibrary 转写（官 smali
 * RetroLoadLibrary.smali / $V25 / $V23 / $V14 本体逐支对位）。
 * installNativeLibraryPath（官 .line 30-66）：类级同步；目录为 null 或
 * 不存在只记日志返回；先走 V25（pathList.nativeLibraryDirectories 列表形＋
 * makePathElements(List)），失败记日志回落 V23（makePathElements(List,
 * File, List) 三参形），V23 再失败异常向外抛。V14（数组形）在官此版
 * 分发里不被调入，仅按官本体一并转写存置。日志官走 Timber，此处以
 * android.util.Log 同位代之（宿主面差异，不涉行为）。
 */
public final class RetroLoadLibrary {

    private static final String TAG = "Tinker.LoadLibrary";

    private RetroLoadLibrary() {
    }

    @SuppressWarnings("unused")
    private static int getPreviousSdkInt() {
        try {
            return Build.VERSION.PREVIEW_SDK_INT;
        } catch (Throwable t) {
            return 1;
        }
    }

    public static synchronized void installNativeLibraryPath(ClassLoader classLoader, File folder)
            throws Throwable {
        if (folder == null || !folder.exists()) {
            Log.i(TAG, "installNativeLibraryPath, folder " + folder + " is illegal");
            return;
        }
        try {
            V25.install(classLoader, folder);
        } catch (Throwable e) {
            Log.w(TAG, "installNativeLibraryPath, v25 fail, sdk: " + Build.VERSION.SDK_INT
                    + ", try to fallback to V23", e);
            V23.install(classLoader, folder);
        }
    }

    /** 官 RetroLoadLibrary$V25.install（smali .line 145-179）。 */
    private static final class V25 {

        private V25() {
        }

        @SuppressWarnings("unchecked")
        static void install(ClassLoader classLoader, File folder) throws Throwable {
            Field pathListField = RetroShareReflectUtil.findField(classLoader, "pathList");
            Object pathList = pathListField.get(classLoader);

            Field dirsField = RetroShareReflectUtil.findField(pathList, "nativeLibraryDirectories");
            List<File> dirs = (List<File>) dirsField.get(pathList);
            if (dirs == null) {
                dirs = new ArrayList<>(2);
            }
            Iterator<File> it = dirs.iterator();
            while (it.hasNext()) {
                File f = it.next();
                if (folder.equals(f)) it.remove();
            }
            dirs.add(0, folder);

            Field sysField = RetroShareReflectUtil.findField(pathList, "systemNativeLibraryDirectories");
            List<File> sysDirs = (List<File>) sysField.get(pathList);
            if (sysDirs == null) {
                sysDirs = new ArrayList<>(2);
            }
            List<File> all = new ArrayList<>(dirs.size() + sysDirs.size() + 1);
            all.addAll(dirs);
            all.addAll(sysDirs);

            Method makePathElements = RetroShareReflectUtil.findMethod(
                    pathList, "makePathElements", List.class);
            Object[] elements = (Object[]) makePathElements.invoke(pathList, all);

            Field elementsField = RetroShareReflectUtil.findField(pathList, "nativeLibraryPathElements");
            elementsField.set(pathList, elements);
        }
    }

    /** 官 RetroLoadLibrary$V23.install（smali .line 103-139）。 */
    private static final class V23 {

        private V23() {
        }

        @SuppressWarnings("unchecked")
        static void install(ClassLoader classLoader, File folder) throws Throwable {
            Field pathListField = RetroShareReflectUtil.findField(classLoader, "pathList");
            Object pathList = pathListField.get(classLoader);

            Field dirsField = RetroShareReflectUtil.findField(pathList, "nativeLibraryDirectories");
            List<File> dirs = (List<File>) dirsField.get(pathList);
            if (dirs == null) {
                dirs = new ArrayList<>(2);
            }
            Iterator<File> it = dirs.iterator();
            while (it.hasNext()) {
                File f = it.next();
                if (folder.equals(f)) it.remove();
            }
            dirs.add(0, folder);

            Field sysField = RetroShareReflectUtil.findField(pathList, "systemNativeLibraryDirectories");
            List<File> sysDirs = (List<File>) sysField.get(pathList);
            if (sysDirs == null) {
                sysDirs = new ArrayList<>(2);
            }
            List<File> all = new ArrayList<>(dirs.size() + sysDirs.size() + 1);
            all.addAll(dirs);
            all.addAll(sysDirs);

            Method makePathElements = RetroShareReflectUtil.findMethod(
                    pathList, "makePathElements", List.class, File.class, List.class);
            Object[] elements = (Object[]) makePathElements.invoke(
                    pathList, new Object[]{all, null, new ArrayList<>()});

            Field elementsField = RetroShareReflectUtil.findField(pathList, "nativeLibraryPathElements");
            elementsField.set(pathList, elements);
        }
    }

    /** 官 RetroLoadLibrary$V14.install（smali .line 84-97，数组形；官此版分发未调入）。 */
    @SuppressWarnings("unused")
    private static final class V14 {

        private V14() {
        }

        static void install(ClassLoader classLoader, File folder) throws Throwable {
            Field pathListField = RetroShareReflectUtil.findField(classLoader, "pathList");
            Object pathList = pathListField.get(classLoader);

            Field dirsField = RetroShareReflectUtil.findField(pathList, "nativeLibraryDirectories");
            File[] dirs = (File[]) dirsField.get(pathList);

            List<File> newDirs = new ArrayList<>(dirs.length + 1);
            newDirs.add(folder);
            for (File f : dirs) {
                if (!folder.equals(f)) newDirs.add(f);
            }
            dirsField.set(pathList, newDirs.toArray(new File[0]));
        }
    }
}

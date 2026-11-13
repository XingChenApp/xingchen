package com.xunlei.downloadlib.parameter;

import android.net.Uri;
import android.text.TextUtils;

import com.xunlei.downloadlib.Util;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TorrentFileInfo implements Comparable<TorrentFileInfo> {

    public boolean isSelected;
    public String mFileName;
    public String mSubPath;
    public long mFileSize;
    public int mFileIndex;
    public int mRealIndex;
    public File mFile;

    public String getFileName() {
        return TextUtils.isEmpty(mFileName) ? "" : mFileName;
    }

    public long getFileSize() {
        return mFileSize;
    }

    public int getFileIndex() {
        return mFileIndex;
    }

    public File getFile() {
        return mFile;
    }

    public TorrentFileInfo file(File file) {
        this.mFile = file;
        return this;
    }

    public String getSize() {
        return Util.size(mFileSize);
    }

    // 上游原文：Uri.Builder 编码 name、且 name 含子目录（mSubPath/文件名），
    // 本树旧写法裸拼文件名、丢子目录且文件名含空格/& 时选集地址解析会断。
    public String getPlayUrl() {
        String name = TextUtils.isEmpty(mSubPath) ? getFileName() : mSubPath + "/" + getFileName();
        return new Uri.Builder()
                .scheme("magnet")
                .authority("")
                .path(getFile().getAbsolutePath())
                .appendQueryParameter("name", name)
                .appendQueryParameter("index", String.valueOf(getFileIndex()))
                .build()
                .toString();
    }

    public String getExt() {
        return getFileName().contains(".") ? getFileName().substring(getFileName().lastIndexOf(".") + 1).toLowerCase(Locale.ROOT) : "";
    }

    @Override
    public int compareTo(TorrentFileInfo other) {
        return this.getFileName().compareToIgnoreCase(other.getFileName());
    }
}

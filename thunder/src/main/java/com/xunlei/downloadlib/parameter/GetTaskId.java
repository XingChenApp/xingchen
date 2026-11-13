package com.xunlei.downloadlib.parameter;

import com.github.catvod.runtime.ThunderRuntime;
import com.github.catvod.utils.Path;

import java.io.File;

// 上游原文口径：GetTaskId 实现 ThunderRuntime.Task（RuntimeEntry 以 Task
// 类型转发）。file:// 构造沿用本树 Path.copy 写法，不改上游 saveFile 用的
// Path.writeAtomically（本树 Path 无此方法，语义同为拷入目标目录）。
public class GetTaskId implements ThunderRuntime.Task {

    public long mTaskId;
    public File mSavePath;
    public String mFileName;
    public String mRealUrl;

    public GetTaskId(File savePath) {
        this.mSavePath = savePath;
    }

    public GetTaskId(String url, File savePath) {
        File file = new File(url.substring(7));
        File dest = new File(savePath, file.getName());
        Path.copy(file, dest);
        this.mFileName = file.getName();
        this.mSavePath = savePath;
        this.mRealUrl = url;
    }

    public GetTaskId(File savePath, String fileName, String realUrl) {
        this.mSavePath = savePath;
        this.mFileName = fileName;
        this.mRealUrl = realUrl;
    }

    public long getTaskId() {
        return this.mTaskId;
    }

    public File getSavePath() {
        return mSavePath;
    }

    @Override
    public String getFileName() {
        return mFileName;
    }

    @Override
    public String getRealUrl() {
        return mRealUrl;
    }

    @Override
    public File getSaveFile() {
        return new File(getSavePath(), getFileName());
    }
}

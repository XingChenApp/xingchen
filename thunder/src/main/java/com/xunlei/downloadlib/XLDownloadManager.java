package com.xunlei.downloadlib;

import android.content.Context;
import android.os.Build;

import com.github.catvod.Init;
import com.github.catvod.utils.Prefers;
import com.xunlei.downloadlib.android.XLUtil;
import com.xunlei.downloadlib.parameter.BtIndexSet;
import com.xunlei.downloadlib.parameter.BtSubTaskDetail;
import com.xunlei.downloadlib.parameter.BtTaskParam;
import com.xunlei.downloadlib.parameter.EmuleTaskParam;
import com.xunlei.downloadlib.parameter.ErrorCode;
import com.xunlei.downloadlib.parameter.GetDownloadLibVersion;
import com.xunlei.downloadlib.parameter.GetFileName;
import com.xunlei.downloadlib.parameter.GetTaskId;
import com.xunlei.downloadlib.parameter.InitParam;
import com.xunlei.downloadlib.parameter.MagnetTaskParam;
import com.xunlei.downloadlib.parameter.P2spTaskParam;
import com.xunlei.downloadlib.parameter.ThunderUrlInfo;
import com.xunlei.downloadlib.parameter.TorrentInfo;
import com.xunlei.downloadlib.parameter.XLConstant;
import com.xunlei.downloadlib.parameter.XLTaskInfo;
import com.xunlei.downloadlib.parameter.XLTaskLocalUrl;

public class XLDownloadManager {

    private XLLoader loader;
    private Context context;

    public XLDownloadManager() {
        this.context = Init.context();
        this.loader = new XLLoader();
        this.init();
    }

    // 上游原文（上游 XLDownloadManager 各调用点共用）：错码转可见错。
    static void checkErrorCode(int code) {
        if (code != XLConstant.XLErrorCode.NO_ERROR) throw new IllegalStateException(ErrorCode.get(code));
    }

    public void init() {
        // Init.context() 契约可回 null（未 set／WeakReference 已回收，见
        // Init 类头注），原先直接 context.getFilesDir() 是 NPE、把真因
        // 盖掉（体检类4）。按本类既有失败口径（checkErrorCode 抛可见
        // IllegalStateException）抛错：构造失败则 XLTaskHelper 的
        // getManager() 不缓存本实例，Init 补 set 后下一次调用自然重试。
        if (context == null) throw new IllegalStateException("Thunder context is not ready");
        InitParam param = new InitParam(context.getFilesDir().getPath());
        int code = loader.init(param.getSoKey(), "com.android.providers.downloads", param.mAppVersion, "", getPeerId(), getGuid(), param.mStatSavePath, param.mStatCfgSavePath, 0, param.mPermissionLevel, param.mQueryConfOnInit);
        checkErrorCode(code);
        getDownloadLibVersion(new GetDownloadLibVersion());
        setOSVersion(Build.VERSION.INCREMENTAL + "_alpha");
        setLocalProperty("PhoneModel", Build.MODEL);
        setStatReportSwitch(false);
        setSpeedLimit(-1, -1);
    }

    public void release() {
        if (loader != null) loader.unInit();
        context = null;
        loader = null;
    }

    private String getPeerId() {
        String uuid = Prefers.getString("phoneId5");
        if (uuid.isEmpty()) Prefers.put("phoneId5", uuid = XLUtil.getPeerId());
        return uuid;
    }

    private String getGuid() {
        return XLUtil.getGuid();
    }

    public void releaseTask(long taskId) {
        loader.releaseTask(taskId);
    }

    public void startTask(long taskId) {
        checkErrorCode(loader.startTask(taskId));
    }

    public void stopTask(long taskId) {
        loader.stopTask(taskId);
    }

    public void getTaskInfo(long taskId, int i, XLTaskInfo taskInfo) {
        checkErrorCode(loader.getTaskInfo(taskId, i, taskInfo));
    }

    public void getLocalUrl(String filePath, XLTaskLocalUrl localUrl) {
        checkErrorCode(loader.getLocalUrl(filePath, localUrl));
    }

    public void getDownloadLibVersion(GetDownloadLibVersion version) {
        loader.getDownloadLibVersion(version);
    }

    public void setStatReportSwitch(boolean value) {
        loader.setStatReportSwitch(value);
    }

    private void setLocalProperty(String key, String value) {
        loader.setLocalProperty(key, value);
    }

    public void setOSVersion(String str) {
        loader.setMiUiVersion(str);
    }

    public void setOriginUserAgent(long taskId, String userAgent) {
        loader.setOriginUserAgent(taskId, userAgent);
    }

    public void setDownloadTaskOrigin(long taskId, String str) {
        loader.setDownloadTaskOrigin(taskId, str);
    }

    public void setTaskGsState(long j, int i, int i2) {
        loader.setTaskGsState(j, i, i2);
    }

    public int createP2spTask(P2spTaskParam param, GetTaskId taskId) {
        return loader.createP2spTask(param.mUrl, param.mRefUrl, param.mCookie, param.mUser, param.mPass, param.mFilePath, param.mFileName, param.mCreateMode, param.mSeqId, taskId);
    }

    public int createBtMagnetTask(MagnetTaskParam param, GetTaskId taskId) {
        return loader.createBtMagnetTask(param.mUrl, param.mFilePath, param.mFileName, taskId);
    }

    public int createEmuleTask(EmuleTaskParam param, GetTaskId taskId) {
        return loader.createEmuleTask(param.mUrl, param.mFilePath, param.mFileName, param.mCreateMode, param.mSeqId, taskId);
    }

    public int createBtTask(BtTaskParam param, GetTaskId taskId) {
        return loader.createBtTask(param.mTorrentPath, param.mFilePath, param.mMaxConcurrent, param.mCreateMode, param.mSeqId, taskId);
    }

    public void getTorrentInfo(TorrentInfo info) {
        checkErrorCode(loader.getTorrentInfo(info.getFile().getAbsolutePath(), info));
    }

    public void getBtSubTaskInfo(long taskId, int index, BtSubTaskDetail detail) {
        checkErrorCode(loader.getBtSubTaskInfo(taskId, index, detail));
    }

    public void deselectBtSubTask(long taskId, BtIndexSet btIndexSet) {
        checkErrorCode(loader.deselectBtSubTask(taskId, btIndexSet));
    }

    public String parserThunderUrl(String url) {
        ThunderUrlInfo thunderUrlInfo = new ThunderUrlInfo();
        checkErrorCode(loader.parserThunderUrl(url, thunderUrlInfo));
        return thunderUrlInfo.mUrl;
    }

    public String getFileNameFromUrl(String url) {
        GetFileName getFileName = new GetFileName();
        checkErrorCode(loader.getFileNameFromUrl(url, getFileName));
        return getFileName.getFileName();
    }

    public void setSpeedLimit(long min, long max) {
        loader.setSpeedLimit(min, max);
    }
}
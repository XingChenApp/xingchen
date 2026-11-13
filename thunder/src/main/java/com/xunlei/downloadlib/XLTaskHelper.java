package com.xunlei.downloadlib;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.xunlei.downloadlib.parameter.BtIndexSet;
import com.xunlei.downloadlib.parameter.BtSubTaskDetail;
import com.xunlei.downloadlib.parameter.BtTaskParam;
import com.xunlei.downloadlib.parameter.EmuleTaskParam;
import com.xunlei.downloadlib.parameter.GetTaskId;
import com.xunlei.downloadlib.parameter.MagnetTaskParam;
import com.xunlei.downloadlib.parameter.P2spTaskParam;
import com.xunlei.downloadlib.parameter.TorrentFileInfo;
import com.xunlei.downloadlib.parameter.TorrentInfo;
import com.xunlei.downloadlib.parameter.XLConstant;
import com.xunlei.downloadlib.parameter.XLTaskInfo;
import com.xunlei.downloadlib.parameter.XLTaskLocalUrl;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class XLTaskHelper {

    private XLDownloadManager manager;
    private AtomicInteger seq;

    private static class Loader {
        static volatile XLTaskHelper INSTANCE = new XLTaskHelper();
    }

    public synchronized static XLTaskHelper get() {
        return Loader.INSTANCE;
    }

    private synchronized AtomicInteger getSeq() {
        return seq = seq == null ? new AtomicInteger(0) : seq;
    }

    private synchronized XLDownloadManager getManager() {
        return manager = manager == null ? new XLDownloadManager() : manager;
    }

    private synchronized GetTaskId startTask(GetTaskId taskId, int index) {
        getManager().setTaskGsState(taskId.getTaskId(), index, 2);
        getManager().startTask(taskId.getTaskId());
        return taskId;
    }

    private void deleteFile(File file) {
        if (file.isDirectory()) for (File f : Path.list(file)) deleteFile(f);
        if (file.isFile() && !file.getAbsolutePath().endsWith(".torrent")) Path.clear(file);
    }

    // 上游原文（RuntimeEntry.check 用）：thunder:// 解码自检，只解码不建任务。
    // 本树 parse() 不走 RuntimeModule.require()（内置形态，见 Thunder.java 头注），
    // 其余三条断言与上游逐字相同。
    public synchronized void check() {
        GetTaskId taskId = parse("thunder://QUFmdHA6Ly8xMjcuMC4wLjEvbW9kdWxlLWNoZWNrLm1wNFpa", Path.files());
        if (!"ftp://127.0.0.1/module-check.mp4".equals(taskId.getRealUrl())) throw new IllegalStateException("Thunder URL decoding returned an unexpected result");
        if (!"module-check.mp4".equals(taskId.getFileName())) throw new IllegalStateException("Thunder filename parsing returned an unexpected result");
        if (taskId.getTaskId() != 0) throw new IllegalStateException("Thunder check unexpectedly created a task");
    }

    // 只归一 scheme 段（trim ＋ scheme 小写），冒号后内容不动（哈希大小写敏感）。
    // 下面的 "magnet:?"/"thunder://" 门限是精确前缀，粘贴变体不归一会被静默跳过。
    static String normalizeScheme(String raw) {
        String u = raw == null ? "" : raw.trim();
        int colon = u.indexOf(':');
        if (colon > 0) u = u.substring(0, colon).toLowerCase(Locale.ROOT) + u.substring(colon);
        return u;
    }

    public synchronized GetTaskId parse(String url, File savePath) {
        url = normalizeScheme(url);
        if (url.startsWith("file://")) return new GetTaskId(url, savePath);
        if (url.startsWith("thunder://")) url = getManager().parserThunderUrl(url);
        String fileName = getManager().getFileNameFromUrl(url);
        GetTaskId taskId = new GetTaskId(savePath, fileName, url);
        if (!url.startsWith("magnet:?")) return taskId;
        MagnetTaskParam param = new MagnetTaskParam();
        param.setFilePath(savePath.getAbsolutePath());
        param.setFileName(fileName);
        param.setUrl(url);
        int code = getManager().createBtMagnetTask(param, taskId);
        if (code != XLConstant.XLErrorCode.NO_ERROR) throw new IllegalStateException("迅雷磁力建任务失败，错码=" + code);
        return startTask(taskId, 0);
    }

    public synchronized GetTaskId addThunderTask(String url, File savePath) {
        String fileName = getManager().getFileNameFromUrl(url);
        GetTaskId taskId = new GetTaskId(savePath, fileName, url);
        if (url.startsWith("ftp://")) {
            P2spTaskParam param = new P2spTaskParam();
            param.setFilePath(savePath.getAbsolutePath());
            param.setSeqId(getSeq().incrementAndGet());
            param.setFileName(fileName);
            param.setCreateMode(1);
            param.setUrl(url);
            param.setCookie("");
            param.setRefUrl("");
            param.setUser("");
            param.setPass("");
            int code = getManager().createP2spTask(param, taskId);
            if (code != XLConstant.XLErrorCode.NO_ERROR) throw new IllegalStateException("迅雷建任务失败，错码=" + code);
        } else if (url.startsWith("ed2k://")) {
            EmuleTaskParam param = new EmuleTaskParam();
            param.setFilePath(savePath.getAbsolutePath());
            param.setSeqId(getSeq().incrementAndGet());
            param.setFileName(fileName);
            param.setCreateMode(1);
            param.setUrl(url);
            int code = getManager().createEmuleTask(param, taskId);
            if (code != XLConstant.XLErrorCode.NO_ERROR) throw new IllegalStateException("迅雷建任务失败，错码=" + code);
        } else {
            throw new IllegalStateException("迅雷建任务失败：只支持 ftp:// 与 ed2k:// 链接");
        }
        getManager().setDownloadTaskOrigin(taskId.getTaskId(), "out_app/out_app_paste");
        getManager().setOriginUserAgent(taskId.getTaskId(), "AndroidDownloadManager/4.4.4 (Linux; U; Android 4.4.4; Build/KTU84Q)");
        return startTask(taskId, 0);
    }

    public synchronized GetTaskId addTorrentTask(File torrent, File savePath, int index) {
        TorrentInfo torrentInfo = getTorrentInfo(torrent);
        if (torrentInfo == null || torrentInfo.mSubFileInfo == null) throw new IllegalStateException("种子损坏：种子文件信息为空");
        TorrentFileInfo[] fileInfos = torrentInfo.mSubFileInfo;
        BtTaskParam taskParam = new BtTaskParam();
        taskParam.setCreateMode(1);
        taskParam.setMaxConcurrent(3);
        taskParam.setSeqId(getSeq().incrementAndGet());
        taskParam.setFilePath(savePath.getAbsolutePath());
        taskParam.setTorrentPath(torrent.getAbsolutePath());
        GetTaskId taskId = new GetTaskId(savePath);
        int code = getManager().createBtTask(taskParam, taskId);
        if (code != XLConstant.XLErrorCode.NO_ERROR) throw new IllegalStateException("迅雷 BT 建任务失败，错码=" + code);
        if (fileInfos.length > 1) {
            List<Integer> list = new CopyOnWriteArrayList<>();
            for (TorrentFileInfo fileInfo : fileInfos) {
                if (fileInfo.mFileIndex != index) {
                    list.add(fileInfo.mFileIndex);
                }
            }
            BtIndexSet btIndexSet = new BtIndexSet(list.size());
            for (int i = 0; i < list.size(); i++) btIndexSet.mIndexSet[i] = list.get(i);
            try {
                getManager().deselectBtSubTask(taskId.getTaskId(), btIndexSet);
            } catch (IllegalStateException e) {
                SpiderDebug.log("thunder", "deselect bt sub task failed err=%s", String.valueOf(e.getMessage()));
            }
        }
        return startTask(taskId, index);
    }

    public synchronized TorrentInfo getTorrentInfo(File file) {
        TorrentInfo torrentInfo = new TorrentInfo(file);
        getManager().getTorrentInfo(torrentInfo);
        return torrentInfo;
    }

    public synchronized String getLocalUrl(File file) {
        XLTaskLocalUrl localUrl = new XLTaskLocalUrl();
        getManager().getLocalUrl(file.getAbsolutePath(), localUrl);
        return localUrl.mStrUrl;
    }

    public synchronized void deleteTask(GetTaskId taskId) {
        stopTask(taskId);
        deleteFile(taskId.getSavePath());
    }

    public synchronized void stopTask(GetTaskId taskId) {
        getManager().stopTask(taskId.getTaskId());
        getManager().releaseTask(taskId.getTaskId());
    }

    // 始终取原生任务信息（真实已下载字节/总大小/状态），不再按「存档存在」合成完成状态
    public synchronized XLTaskInfo getTaskInfo(GetTaskId taskId) {
        XLTaskInfo taskInfo = new XLTaskInfo();
        getManager().getTaskInfo(taskId.getTaskId(), 1, taskInfo);
        return taskInfo;
    }

    // 与 getTaskInfo 同口径（原生任务信息），保留给既有调用方
    public synchronized XLTaskInfo getNativeTaskInfo(GetTaskId taskId) {
        return getTaskInfo(taskId);
    }

    public synchronized BtSubTaskDetail getBtSubTaskInfo(GetTaskId taskId, int index) {
        BtSubTaskDetail subTaskDetail = new BtSubTaskDetail();
        getManager().getBtSubTaskInfo(taskId.getTaskId(), index, subTaskDetail);
        return subTaskDetail;
    }

    public synchronized void release() {
        if (manager != null) manager.release();
        Path.clear(Path.files("setting.cfg"));
        Path.clear(Path.files("seq_id"));
        manager = null;
        seq = null;
    }
}
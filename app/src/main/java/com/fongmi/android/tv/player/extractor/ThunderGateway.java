package com.fongmi.android.tv.player.extractor;

import com.fongmi.android.tv.exception.ExtractException;
import com.github.catvod.runtime.ThunderRuntime;
import com.xunlei.downloadlib.RuntimeEntry;
import com.xunlei.downloadlib.XLTaskHelper;
import com.xunlei.downloadlib.parameter.BtSubTaskDetail;
import com.xunlei.downloadlib.parameter.GetTaskId;
import com.xunlei.downloadlib.parameter.XLTaskInfo;

// 迅雷运行时网关：Thunder.java 取运行时的唯一入口。
// 内置形态（2026-10-23 用户定案改回内置，外置包形态作废）：迅雷代码与两个
// 原生库随 :thunder 编进主包，运行时就是本进程里的 RuntimeEntry
// （com.xunlei.downloadlib.RuntimeEntry，转发 XLTaskHelper），网关直接
// 构造它——不查外置包、不走反射、没有「模块未安装」一态，点磁力直接解析起播。
// 状态取值走 XLTaskHelper 原生信息（getNativeTaskInfo／BT 子任务详情），
// 绕开 getTaskInfo 的“存档存在即报 2”合成，速度与字节都是真值。
public final class ThunderGateway {

    private static final ThunderGateway INSTANCE = new ThunderGateway();

    public static ThunderGateway get() {
        return INSTANCE;
    }

    public record StatusEx(int taskStatus, String errorMessage, long downloadSpeed, long downloadSize) {
    }

    private ThunderRuntime active;

    private ThunderGateway() {
    }

    public synchronized ThunderRuntime runtime() throws ExtractException {
        if (active == null) active = new RuntimeEntry();
        return active;
    }

    // 只放已接通的运行时，不为 release 专门去接通一次。
    public synchronized void releaseQuietly() {
        if (active == null) return;
        try {
            active.release();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    public synchronized StatusEx taskStatus(ThunderRuntime.Task task) throws ExtractException {
        if (!(task instanceof GetTaskId getTaskId)) return null;
        XLTaskInfo info = XLTaskHelper.get().getNativeTaskInfo(getTaskId);
        return info == null ? null : toStatusEx(info);
    }

    public synchronized StatusEx btSubTaskStatus(ThunderRuntime.Task task, int index) throws ExtractException {
        if (!(task instanceof GetTaskId getTaskId)) return null;
        BtSubTaskDetail detail = XLTaskHelper.get().getBtSubTaskInfo(getTaskId, index);
        return detail == null || detail.mTaskInfo == null ? null : toStatusEx(detail.mTaskInfo);
    }

    private StatusEx toStatusEx(XLTaskInfo info) {
        return new StatusEx(info.getTaskStatus(), info.getErrorMsg(), info.mDownloadSpeed, info.mDownloadSize);
    }
}

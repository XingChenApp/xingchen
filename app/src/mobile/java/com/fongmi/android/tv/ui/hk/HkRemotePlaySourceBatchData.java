package com.fongmi.android.tv.ui.hk;

import java.util.List;

/**
 * 照抄 8.83 RemotePlaySourceBatchData（smali_classes2/ui/video/model/RemotePlaySourceBatchData.smali）：
 * 世界直播批量投屏 POST /api/remote/batch 的请求体包装，字段名 playSources 与原版一致。
 */
public class HkRemotePlaySourceBatchData {

    private List<HkRemotePlaySource> playSources;

    public HkRemotePlaySourceBatchData() {
    }

    public HkRemotePlaySourceBatchData(List<HkRemotePlaySource> playSources) {
        this.playSources = playSources;
    }

    public List<HkRemotePlaySource> getPlaySources() {
        return playSources;
    }

    public void setPlaySources(List<HkRemotePlaySource> playSources) {
        this.playSources = playSources;
    }
}

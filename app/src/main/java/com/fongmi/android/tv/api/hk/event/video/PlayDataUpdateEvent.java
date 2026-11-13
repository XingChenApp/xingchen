package com.fongmi.android.tv.api.hk.event.video;

/**
 * 8.83 com.example.hikerview.event.video.PlayDataUpdateEvent 的等价实现（EventBus 事件类，纯数据载体）。
 * 规则 JS 调 refreshVideoUrl(url, restartPosition) 时发布，且仅当本事件有订阅者时才发
 * （原版 JSEngine.refreshVideoUrl 的 hasSubscriberForEvent 门，smali 22294）。
 * 消费侧：VideoActivity.onPlayDataUpdate → PlayerManager.refreshUrl 原地换当前播放地址
 * （restartPosition=true 从头播、false 保进度续播），对应原版 VideoPlayerActivity.refreshVideoUrl → reStartPlayer。
 */
public class PlayDataUpdateEvent {

    private String url;
    private boolean restartPosition;

    public PlayDataUpdateEvent(String url, boolean restartPosition) {
        this.url = url;
        this.restartPosition = restartPosition;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isRestartPosition() {
        return restartPosition;
    }

    public void setRestartPosition(boolean restartPosition) {
        this.restartPosition = restartPosition;
    }
}

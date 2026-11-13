package com.fongmi.android.tv.api.hk.event.video;

/**
 * 远程切集事件（8.83 com.example.hikerview.event.video.PlayChapterEvent 逐字段转写）：
 * 字段 position（集序，-1＝下一集）、title（集标题，可空），构造 (position, title)。
 * 发布方＝DlanWebController.playMe/playNext（HkDlanController，有订阅者才发）；
 * 消费方＝VideoPlayerActivity.playChapter，咱家落 VideoActivity.onPlayChapter。
 */
public class PlayChapterEvent {

    private final int position;
    private final String title;

    public PlayChapterEvent(int position, String title) {
        this.position = position;
        this.title = title;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }
}

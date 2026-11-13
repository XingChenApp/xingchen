package com.fongmi.android.tv.player.danmaku.live;

import java.util.Locale;

/**
 * 识别层产物：平台＋真实房间号成对出现，缺一作废（设计第四节死规矩）。
 */
public record RoomRef(Platform platform, String roomId) {

    public enum Platform {
        DOUYU,
        BILI,
        HUYA,
        DOUYIN,
        KUAISHOU
    }

    public String key() {
        return platform.name().toLowerCase(Locale.ROOT) + ":" + roomId;
    }
}

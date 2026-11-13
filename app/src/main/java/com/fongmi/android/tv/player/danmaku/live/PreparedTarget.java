package com.fongmi.android.tv.player.danmaku.live;

import java.util.List;
import java.util.Map;

/**
 * provider 预备步的产物：连哪、带什么头、连上先发什么、心跳间隔多少。
 * 预备步失败（校验不过/未开播）不产出本对象＝不接，静默降级。
 */
public record PreparedTarget(String wsUrl, Map<String, String> headers, List<byte[]> onOpenFrames, long heartbeatMs) {

    public PreparedTarget {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        onOpenFrames = onOpenFrames == null ? List.of() : List.copyOf(onOpenFrames);
    }
}

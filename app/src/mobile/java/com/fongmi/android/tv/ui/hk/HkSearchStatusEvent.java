package com.fongmi.android.tv.ui.hk;

import com.fongmi.android.tv.api.hk.HkSearchEngine;

/**
 * 对应 8.83 event/StatusEvent(SearchEngine, group)：搜索状态变更通知
 * （engine.status：1 搜索中、2 有结果、3 失败、4 无结果），宿主据此刷新顶部发动机状态条。
 */
public class HkSearchStatusEvent {

    public final HkSearchEngine engine;
    public final String group;

    public HkSearchStatusEvent(HkSearchEngine engine, String group) {
        this.engine = engine;
        this.group = group;
    }
}

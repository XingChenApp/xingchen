package com.fongmi.android.tv.api.hk.event.rule;

import com.fongmi.android.tv.api.hk.HkItem;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * 8.83 com.example.hikerview.event.rule.ClsItemsFindEvent 的咱家同位事件
 * （契约由 136 批 W3/W4 两路共定）：生产方 HkJsRuntime._findItemsByCls 以
 * cls 建件、经 EventBus.getDefault() post 后在 latch 上等应答（官 3 秒）；
 * 订阅方（HkArticleFragment）按 cls 在页面当前条目表里查、命中集逐个加进
 * results 后 countDown。官本体 result 名 articleLists、订阅方整表回填；
 * 咱家按共定契约用 CopyOnWriteArrayList 增量承接（应答等待期间生产方只读
 * latch、不读表，表在 countDown 后才被读，写读不相碰）。
 */
public class ClsItemsFindEvent {

    public final String cls;
    public final List<HkItem> results = new CopyOnWriteArrayList<>();
    public final CountDownLatch latch = new CountDownLatch(1);

    public ClsItemsFindEvent(String cls) {
        this.cls = cls;
    }
}

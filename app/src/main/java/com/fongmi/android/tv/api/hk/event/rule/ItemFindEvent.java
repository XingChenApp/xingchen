package com.fongmi.android.tv.api.hk.event.rule;

import com.fongmi.android.tv.api.hk.HkItem;

import java.util.concurrent.CountDownLatch;

/**
 * 8.83 com.example.hikerview.event.rule.ItemFindEvent 的咱家同位事件
 * （契约由 136 批 W3/W4 两路共定）：生产方 HkJsRuntime._findItem 以 id 建件、
 * 经 EventBus.getDefault() post 后在 latch 上等应答（官 3 秒）；订阅方
 * （HkArticleFragment）按 id 在页面当前条目表里查、命中把条目写入 result
 * 后 countDown，未命中也须 countDown（result 保持 null）。
 * 官本体字段是 private＋getter/setter、result 名 articleList；咱家按共定
 * 契约直用 public 字段、result 型为 HkItem。
 */
public class ItemFindEvent {

    public final String id;
    public volatile HkItem result;
    public final CountDownLatch latch = new CountDownLatch(1);

    public ItemFindEvent(String id) {
        this.id = id;
    }
}

package com.fongmi.android.tv.event;

import android.content.Context;
import android.content.Intent;

/**
 * 播放器已存在时原地换源事件（对齐海阔 8.83）。
 * 8.83 原文：com.example.hikerview.event.PlaySourceUpdateEvent，
 * PlayerChooser.hasPlayer 为 true 时走 EventBus，不 startActivity。
 */
public class PlaySourceUpdateEvent {

    private Context context;
    private Intent intent;

    public PlaySourceUpdateEvent(Context context, Intent intent) {
        this.intent = intent;
        this.context = context;
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}

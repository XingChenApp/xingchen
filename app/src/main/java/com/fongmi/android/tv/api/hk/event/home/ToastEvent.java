package com.fongmi.android.tv.api.hk.event.home;

/**
 * 对照 8.83 com.example.hikerview.event.ToastEvent：JS toast() 不直弹，
 * 投事件由宿主面承接（官 FilmListActivity.onToast：isOnPause 丢弃、
 * 否则 DebugUtil.showErrorMsg 错误式呈现）。咱家承接点照本树 A1-A5
 * 落点迁移先例放 HkArticleFragment（见其 onToastEvent）。
 */
public class ToastEvent {

    public final String message;

    public ToastEvent(String message) {
        this.message = message;
    }
}

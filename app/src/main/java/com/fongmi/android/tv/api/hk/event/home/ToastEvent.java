package com.fongmi.android.tv.api.hk.event.home;

/**
 * 对照 8.83 com.example.hikerview.event.home.ToastEvent：生产方是
 * JSEngine.setError 的无回调支（解析失败消息投事件），由宿主面承接
 * （官 FilmListActivity.onToast：isOnPause 丢弃、否则 DebugUtil
 * .showErrorMsg 错误式呈现，title=msg、code="500"、ex=new Exception(msg)）。
 * JS toast() 与本事件无关——官 JSEngine.toast 是 runOnUI 直弹
 * ToastMgr.shortBottomCenter 的轻提示（136b A7 曾认混两条通道、
 * 把 toast 改投本事件冒充报错，2026-11-15 已订正）。咱家承接点照
 * 本树 A1-A5 落点迁移先例放 HkArticleFragment（见其 onToastEvent）。
 */
public class ToastEvent {

    public final String message;

    public ToastEvent(String message) {
        this.message = message;
    }
}

package com.fongmi.android.tv.ui.hk;

import android.app.Activity;

import com.fongmi.android.tv.ai.AiActivityTracker;
import com.fongmi.android.tv.api.hk.event.AuthBridgeEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * AuthBridgeEvent 的宿主承接（独立 handler，官五宿主 Activity 的
 * onAuthRequest 同位面收成一份）：官 FilmListActivity/MainActivity/
 * RuleWindowActivity/SearchActivity/SearchInOneRuleActivity 五处同文
 * （FilmListActivity.smali:5932 为据）——@Subscribe(MAIN)，宿主
 * isOnPause/isFinishing 时不弹（生产方 latch 满 1 分钟后按未 AGREE
 * 收场）；余弹授权弹窗，三钮的裁决经 JSEngine.updateAuth 回桥后
 * lock.countDown()。
 * <p>
 * 咱家落法：弹窗走壳子统一弹窗面 HkDialogs（官为原生 AlertDialog＋
 * DialogUtil.showAsCard，文案/钮序/不可取消逐字照官，皮走咱家既定面）；
 * 文案「授权提示」＋「{rule}申请{title}，此方法非常规情况下不需要
 * 使用，确认要使用请点击授权执行」；正钮「授权执行」→AGREE、负钮
 * 「拒绝执行」→DISAGREE、中钮「暂不授权」→只 countDown 不回桥（官同：
 * 不写存储、下回再问）。回桥口＝{@link ResultSink}，即官 updateAuth
 * (rule, method, result) 的等价承接点：主线在 HkJsRuntime 落 updateAuth
 * 后注册一行把 sink 指向它（见 event/AuthBridgeEvent 类头注的契约账与
 * 分账主线缝）；sink 未注册时裁决仍写入 event.result 并 countDown，
 * 生产方回读 event 载体本身即可得本回裁决（官生产方回读的是存储表，
 * 此差记名于分账）。
 * <p>
 * 注册：宿主在海阔面就绪时调 {@link #register()} 一次（进程内单例、
 * 重复调无副作用），承接点如何落进宿主见分账主线缝——本类不自启、
 * 不挂 Application（后台自运行面不新增）。
 */
public final class HkAuthBridgeHandler {

    /** 官 JSEngine.updateAuth(String, String, AuthResult) 的回桥口。 */
    public interface ResultSink {
        void onAuthResult(String rule, String method, AuthBridgeEvent.AuthResult result);
    }

    /** 主线注册的 updateAuth 承接（未注册时为 null，见类头注）。 */
    public static volatile ResultSink resultSink;

    private static final HkAuthBridgeHandler INSTANCE = new HkAuthBridgeHandler();

    private HkAuthBridgeHandler() {
    }

    /** 在 EventBus 上注册单例（重复调用无副作用）。 */
    public static synchronized void register() {
        if (!EventBus.getDefault().isRegistered(INSTANCE)) {
            EventBus.getDefault().register(INSTANCE);
        }
    }

    /** 注销单例（宿主面整体拆除时用；常规不调）。 */
    public static synchronized void unregister() {
        if (EventBus.getDefault().isRegistered(INSTANCE)) {
            EventBus.getDefault().unregister(INSTANCE);
        }
    }

    /**
     * 官 onAuthRequest(@Subscribe MAIN)：宿主存活才弹。
     * 咱家 handler 无自有 pause 态，以当前 resumed 顶层 Activity 的存活
     * 为门（AiActivityTracker.top()＋isFinishing/isDestroyed）——顶层已
     * 结束时不弹、生产方按官同形等满 latch 超时收场。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAuthRequest(AuthBridgeEvent event) {
        if (event == null) {
            return;
        }
        Activity activity = AiActivityTracker.top();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || HkDialogs.isHostGone(activity)) {
            return;
        }
        String message = String.valueOf(event.getRule()) + "申请" + String.valueOf(event.getTitle())
                + "，此方法非常规情况下不需要使用，确认要使用请点击授权执行";
        new HkDialogs(activity)
                .setTitle("授权提示")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("授权执行", (dialog, which) -> {
                    dialog.dismiss();
                    deliver(event, AuthBridgeEvent.AuthResult.AGREE);
                })
                .setNegativeButton("拒绝执行", (dialog, which) -> {
                    dialog.dismiss();
                    deliver(event, AuthBridgeEvent.AuthResult.DISAGREE);
                })
                .setNeutralButton("暂不授权", (dialog, which) -> {
                    dialog.dismiss();
                    countDown(event);
                })
                .show();
    }

    /** 官三钮回调序：先 dismiss（由弹窗框架/回调内完成）→updateAuth 回桥→lock.countDown()。 */
    private void deliver(AuthBridgeEvent event, AuthBridgeEvent.AuthResult result) {
        event.setResult(result);
        ResultSink sink = resultSink;
        if (sink != null) {
            try {
                sink.onAuthResult(event.getRule(), event.getMethod(), result);
            } catch (Throwable ignored) {
            }
        }
        countDown(event);
    }

    private void countDown(AuthBridgeEvent event) {
        if (event.getLock() != null) {
            event.getLock().countDown();
        }
    }
}

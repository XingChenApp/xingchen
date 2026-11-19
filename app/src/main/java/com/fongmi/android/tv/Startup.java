package com.fongmi.android.tv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.startup.Initializer;

import com.fongmi.android.tv.utils.XingChenDoh;
import com.fongmi.android.tv.utils.XingChenProxy;
import com.fongmi.android.tv.player.mpv.PlaybackRecoveryMonitor;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.github.catvod.net.OkHttp;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.List;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class Startup implements Initializer<Void> {

    @NonNull
    @Override
    public Void create(@NonNull Context context) {
        // 进程守卫与 App 统一到主进程口径（S5-07）：此前只挡恢复进程，
        // 崩溃页进程（:error_activity）里整套（崩溃配置/日志/DoH/代理/节点网关）照跑。
        if (!PlaybackRecoveryMonitor.isMainProcess(context)) return null;
        CaocConfig.Builder.create().trackActivities(true).backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        Logger.addLogAdapter(new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("TV").build()));
        try {
            // 安装默认事件总线（S5-08）：此前 install 写在 catch 里、try 体为空，
            // 安装永不执行，日后往 builder 加配置会静默不生效。放回 try 体真装，
            // 失败（已安装等）保持 EventBus.getDefault() 惰性自建的原行为。
            EventBus.builder().installDefaultEventBus();
        } catch (Throwable ignored) {
        }
        XingChenDoh.apply(context);
        XingChenProxy.apply(context);
        // 节点按源定向：把 ClashSettings/策略表注册进 catvod 决策层
        com.fongmi.android.tv.clash.ClashGate.register(context);
        return null;
    }

    @NonNull
    @Override
    public List<Class<? extends Initializer<?>>> dependencies() {
        return Collections.emptyList();
    }
}

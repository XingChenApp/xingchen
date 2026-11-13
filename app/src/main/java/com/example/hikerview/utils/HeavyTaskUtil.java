package com.example.hikerview.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 照官 8.83 本体移植（jadx 官包源）——本批只落执行器族两法，
 * 逐字照官：postTaskToQueue 走自有单线程池（0 核心/1 最大/3 秒
 * 存活），executeNewTask 转 ThreadTool。官类其余成员是播放进度/
 * 历史落库（耦合官 LitePal 模型与浏览器/DLNA 栈），星辰侧该数据
 * 归引擎自有存储，随用到再议，记名在案。
 */
public class HeavyTaskUtil {

    private static final String TAG = "HeavyTaskUtil";
    private static final String[] playPosFixWhiteList = {"m3u8.htv009.com", "127.0.0.1", ":11111/"};
    private static final ExecutorService threadPool = new ThreadPoolExecutor(0, 1, 3, TimeUnit.SECONDS, new LinkedBlockingQueue());

    public static void postTaskToQueue(Runnable runnable) {
        threadPool.execute(runnable);
    }

    public static void executeNewTask(Runnable runnable) {
        ThreadTool.INSTANCE.executeNewTask(runnable);
    }
}

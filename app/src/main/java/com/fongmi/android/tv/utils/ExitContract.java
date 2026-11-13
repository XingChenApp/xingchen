package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.Intent;

import com.fongmi.android.tv.ui.activity.HomeActivity;

/**
 * 退出契约唯一实现（他 2026-11-13 返回规矩：哪个页都一样，里面该
 * 退的层退完之后再按返回一律回点播首页，只有停在点播首页本身按
 * 返回才退出）。
 *
 * <p>页面成了任务根（底下没有首页记录——后台久置进程被杀、回来
 * 系统只恢复顶层页，或单页入任务）时，裸 finish 会直接穿到手机
 * 桌面；先把点播首页拉回本任务再退，落点恒点播首页。BaseActivity
 * 的缺省返回路与不走 BaseActivity 的页面返回终点共用本实现，
 * 不许各页再自备一份（点播页/直播页的历史自带份与此同果，留原样）。</p>
 */
public final class ExitContract {

    private ExitContract() {
    }

    /** 本页是任务根、且不是点播首页本身时，先把点播首页拉回本任务。 */
    public static void rescueIfTaskRoot(Activity activity) {
        if (activity == null || activity instanceof HomeActivity) return;
        if (activity.isTaskRoot()) {
            activity.startActivity(new Intent(activity, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }
}

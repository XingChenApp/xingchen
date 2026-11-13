package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 启动页（独立成页，2026-11-13，对官 SplashActivity 独立条目）：
 * 桌面入口只落本页，清单主题 Theme.Splash 的金标黑底只在此页
 * 出现。本页不装任何界面，onCreate 即把桌面意图原样（ACTION_MAIN
 * ＋LAUNCHER 类别——HomeActivity 的「启动直达短剧」判读靠它）
 * 转交 HomeActivity 并自关（清单 noHistory，栈里不留）。
 *
 * 此前 HomeActivity 本人兼 LAUNCHER 且自带 Theme.Splash：它被
 * 系统收走（如播放页下内存压力）后按栈重建时，系统先顶它
 * 清单主题的启动图——退播放页闪黑底金标（126 真机）即此。拆页
 * 后 HomeActivity 清单主题回 Theme.App（windowDisablePreview），
 * 重建无起始窗可闪；冷启金标由本页照旧呈现，形态不变。
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent forward = new Intent(Intent.ACTION_MAIN);
        forward.setClass(this, HomeActivity.class);
        forward.addCategory(Intent.CATEGORY_LAUNCHER);
        forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(forward);
        finish();
    }
}

package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 启动页（独立成页，2026-11-13，对官 SplashActivity 独立条目）：
 * 桌面入口只落本页，清单主题 Theme.Splash 的金标黑底只在此页
 * 出现。本页不装任何界面，onCreate 即把桌面意图转交 HomeActivity
 * 并自关（清单 noHistory，栈里不留）。
 *
 * 转交必须是「原意图＋复用语义」（2026-11-18 断根，账
 * redo-batch1-20261118／bg-return-replay-20261118 甲节）：
 * 旧形每次新造一张只有 ACTION_MAIN＋NEW_TASK 的意图递根页——那
 * 是对已存在实例的一次全新显式启动，根页 singleTop 不在栈顶时
 * 系统按规再建一个新实例，新实例又必发直达、新建流页认领旧会话
 * 寄存，回程重播/页面打架/断点丢失整条链由此而起。改后：复制桌
 * 面原意图（动作/类别/桌面侧旗标全保留）只换落点到根页，并加
 * REORDER_TO_FRONT——根页已有实例时系统把那一个实例提到栈顶并
 * 经 onNewIntent 递交（其下播放页等原样保留，与根页桥
 * .startTab 的跨页路由同一复用语义、同一递交口），没有实例时才
 * 新建（冷启）。根页从此只有一个实例，直达判定在根页内收成同
 * 一个入场口（initView 冷启与 onNewIntent 回程共用一段判定）。
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
        Intent forward = new Intent(getIntent());
        forward.setClass(this, HomeActivity.class);
        forward.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(forward);
        finish();
    }
}

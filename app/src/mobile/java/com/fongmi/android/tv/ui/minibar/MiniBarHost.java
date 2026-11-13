package com.fongmi.android.tv.ui.minibar;

/**
 * 底部有自家导航栏的宿主（如首页底栏）：迷你条要贴在导航栏上方，
 * 宿主实现此接口告诉壳多留多少底边距（系统导航栏之外的部分）。
 */
public interface MiniBarHost {
    /** 系统导航栏之外、迷你条之下还要空出的像素数。 */
    int miniBarBottomExtraPx();
}

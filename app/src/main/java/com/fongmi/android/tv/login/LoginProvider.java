package com.fongmi.android.tv.login;

/**
 * 壳子统一登录：登录提供方接口。
 * 每个登录源（115、夸克等）实现一个 Provider，注册到 {@link LoginManager}。
 * 源（py / js / 海阔规则）不自己实现登录，只调 {@link LoginManager} 的静态方法。
 */
public interface LoginProvider {

    /** 提供方唯一 id，如 "115" */
    String id();

    /** 显示名，如 "115网盘" */
    String name();

    /** 是否已登录（凭据有效） */
    boolean isLoggedIn();

    /** 已保存的登录凭据（cookie / token），未登录返回空串 */
    String getCookie();

    /** 退出登录，清除已保存凭据 */
    void clear();
}

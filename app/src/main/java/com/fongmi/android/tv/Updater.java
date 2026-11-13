package com.fongmi.android.tv;

import androidx.fragment.app.FragmentActivity;

/**
 * 正式版更新位（潇洒哥 2026-11-11 亲定：正式版检测更新全摘）。
 *
 * 正式版零检测更新：GitHub 查仓链、自动检查、手动检查入口、本地文件
 * 更新路一件不留，新版由助手直接发包。本类只保留公开签名作空壳——
 * 入口三处（BaseActivity.resume、设置页关于弹窗回调位、App 的
 * notifyModules）一行不改，是因为朋友版构建时 strip-updater.sh 会把
 * 本文件整份换成只查版 Updater（只检测、弹提示去浏览器下载），那套
 * 靠的正是这组签名与入口；正式版落在这组空实现上即零请求、零弹窗。
 * 摘点全账：worknotes/formal-update-fullstrip-20261111.md。
 * FORMAL_UPDATE_REMOVED（strip-updater.sh 注入前以此断言正式版空壳形态）
 */
public class Updater {

    private static final Updater INSTANCE = new Updater();

    private Updater() {
    }

    public static Updater create() {
        return INSTANCE;
    }

    public Updater force() {
        return this;
    }

    public void start(FragmentActivity activity) {
    }

    public void resume(FragmentActivity activity) {
    }

    public void notifyModules() {
    }
}

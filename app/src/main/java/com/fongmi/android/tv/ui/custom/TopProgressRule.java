package com.fongmi.android.tv.ui.custom;

/**
 * 顶部细进度条显隐规则（纯函数，可单测）。
 * 规则（潇洒哥拍板）：控制栏藏着时顶部条在，控制栏出来它消失；只在全屏显示；直播这种没总时长的（hasDuration=false）不显示。
 */
public final class TopProgressRule {

    private TopProgressRule() {
    }

    /**
     * @param fullscreen      是否全屏（横屏全屏/竖屏全屏；竖屏小窗算非全屏）
     * @param controlsVisible 控制栏当前是否可见
     * @param hasDuration     是否有时长（点播 true，直播 false）
     * @return 顶部细进度条是否应该显示
     */
    public static boolean shouldShow(boolean fullscreen, boolean controlsVisible, boolean hasDuration) {
        return fullscreen && !controlsVisible && hasDuration;
    }
}

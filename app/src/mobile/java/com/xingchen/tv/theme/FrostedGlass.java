package com.xingchen.tv.theme;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * 磨砂玻璃：真 blur 实现（API 31+）。
 * 遵循 ToastUtil 的背景/文字分层：RenderEffect 只作用于独立背景层，内容层永远不被模糊。
 * RenderScript 已废弃（见 WallpaperBlur 注释），不引入第三方 BlurView；
 * 用平台 RenderEffect（API 31+，低版本靠半透明底兜底呈现磨砂）。
 */
public final class FrostedGlass {

    private FrostedGlass() {}

    /** 真模糊半径（与 ToastUtil 的 18f 同一量级）。 */
    public static final float RADIUS = 18f;

    /**
     * 对独立背景层叠加真模糊。bg 必须是半透明，模糊的是它背后的内容。
     * API 31 以下无操作（背景 drawable 本身的半透明即呈现磨砂）。
     */
    public static void blurBackground(View bg) {
        if (bg == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                bg.setRenderEffect(RenderEffect.createBlurEffect(RADIUS, RADIUS, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clearBlur(View bg) {
        if (bg == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                bg.setRenderEffect(null);
            } catch (Throwable ignored) {
            }
        }
    }
}

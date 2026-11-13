package com.fongmi.android.tv.ui.activity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 阅读模块换皮共用：深色毛玻璃＋金 #F2B33D 点缀（与精修稿 v2 同语言）。
 * 只出样式（颜色/背景/圆角/边线），不碰行为。
 */
public final class ReadingSkin {

    private ReadingSkin() {
    }

    /**
     * 点缀色（2026-10-18 他选 B 青绿，gen.py VARIANTS B）：
     * GOLD＝主色 #45C08A；GOLD_TEXT＝深底提亮字 #6FE0A8；
     * GOLD_BTN_TEXT＝渐变钮上的深字 #0A2115。
     * 常量名沿用 GOLD 系（调用方不动），小说阅读器三种纸色主题是内容色、不在此列。
     */
    public static final int GOLD = 0xFF45C08A;
    public static final int GOLD_TEXT = 0xFF6FE0A8;
    public static final int GOLD_BTN_TEXT = 0xFF0A2115;
    public static final int GOLD_DARK = 0xFF1F7A4D;
    public static final int GOLD_DEEP = 0xFF2FA36C;
    public static final int TEXT = 0xFFF4F2EC;
    public static final int SUB = 0x99F4F2EC;
    public static final int FAINT = 0x80F4F2EC;
    /**
     * 深蓝黑基底（2026-10-18 他看 home-blue.png 拍板；暖棕黑曾短暂定案、同日晚被他改口推翻「底色还是蓝色吧」）：
     * 取色依据——home-blue.html 中间稿：linear-gradient(180deg,#0E1622→#0A0E16)，
     * 顶部淡蓝柔光 radial rgba(120,170,255,.18)。
     */
    public static final int PAGE_BG_TOP = 0xFF0E1622;
    public static final int PAGE_BG_BOTTOM = 0xFF0A0E16;
    /** 兼容旧引用：页面主底色＝深蓝黑顶色。 */
    public static final int PAGE_BG = PAGE_BG_TOP;

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 页面底：深蓝黑纵向渐变＋顶部两道淡蓝柔光（home-blue 稿：rgba(120,170,255,.18) 顶部、rgba(90,140,220,.12) 右中）。 */
    public static Drawable pageBackground(Context c) {
        GradientDrawable glow1 = new GradientDrawable();
        glow1.setShape(GradientDrawable.RECTANGLE);
        glow1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow1.setGradientRadius(dp(c, 420));
        glow1.setGradientCenter(0.12f, 0f);
        glow1.setColors(new int[]{0x2E78AAFF, 0x00000000});
        GradientDrawable glow2 = new GradientDrawable();
        glow2.setShape(GradientDrawable.RECTANGLE);
        glow2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow2.setGradientRadius(dp(c, 480));
        glow2.setGradientCenter(0.92f, 0.2f);
        glow2.setColors(new int[]{0x1F5A8CDC, 0x00000000});
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{PAGE_BG_TOP, PAGE_BG_BOTTOM});
        return new LayerDrawable(new Drawable[]{base, glow1, glow2});
    }

    /**
     * 裸 {@code android.app.Activity} 用的 edge-to-edge（非 AppCompatActivity，
     * 调不了 {@code androidx.activity.EdgeToEdge.enable(ComponentActivity)}）。
     * 等效实现：内容延到状态栏下＋状态栏/导航栏透明；
     * darkPage=true 时系统栏图标切浅色（深色页），false 时切深色（浅色页）。
     */
    public static void enableEdgeToEdge(android.app.Activity act, boolean darkPage) {
        try {
            android.view.Window w = act.getWindow();
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
            w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                w.setStatusBarContrastEnforced(false);
                w.setNavigationBarContrastEnforced(false);
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController ic = w.getInsetsController();
                if (ic != null) {
                    int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    ic.setSystemBarsAppearance(darkPage ? 0 : mask, mask);
                }
            } else {
                android.view.View decor = w.getDecorView();
                int f = decor.getSystemUiVisibility();
                int bits = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                decor.setSystemUiVisibility(darkPage ? (f & ~bits) : (f | bits));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 只切系统栏图标明暗（深色页浅图标 / 浅色页深图标），不碰窗口底。
     * 供纸色主题切换时调用；fitWindowBackground 的图标逻辑抽出，窗口底部分已废弃。
     */
    public static void setSystemBarIconAppearance(android.app.Activity act,
                                                boolean darkPage) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController ic =
                        act.getWindow().getInsetsController();
                if (ic != null) {
                    int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    ic.setSystemBarsAppearance(darkPage ? 0 : mask, mask);
                }
            } else {
                android.view.View decor = act.getWindow().getDecorView();
                int f = decor.getSystemUiVisibility();
                int bits = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                decor.setSystemUiVisibility(darkPage ? (f & ~bits) : (f | bits));
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 窗口底对齐页面底：阅读页全挂 Theme.XingChen.HkPage，其 windowBackground
     * 是米白 #FFF4F6F8——状态栏接缝的根。调用方在 onCreate 里调一次，
     * 颜色只从这里取，各页不许各调各的。
     *
     * @param darkPage 深色页时状态栏/导航栏图标切浅色，浅色页切深色
     * @deprecated 名存实亡，勿再调用：窗口底恒被 ThemeManager.doApply（每次
     * onResume 的 posted 回调）重设为壁纸，本函数的窗口底设置活不过第一次
     * resume；页面底只靠内容根视图自己的背景。状态栏压实走
     * {@link #enableEdgeToEdge(android.app.Activity, boolean)}（裸 Activity）
     * 或 {@code androidx.activity.EdgeToEdge.enable}（AppCompatActivity）。
     */
    @Deprecated
    public static void fitWindowBackground(android.app.Activity act, int color,
                                          boolean darkPage) {
        try {
            act.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(color));
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController ic =
                        act.getWindow().getInsetsController();
                if (ic != null) {
                    int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    ic.setSystemBarsAppearance(darkPage ? 0 : mask, mask);
                }
            } else {
                android.view.View decor = act.getWindow().getDecorView();
                int f = decor.getSystemUiVisibility();
                int bits = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                decor.setSystemUiVisibility(darkPage ? (f & ~bits) : (f | bits));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 毛玻璃卡：半透明白渐变＋边缘高光＋圆角。阴影走 view.setElevation。 */
    public static GradientDrawable glassCard(Context c, int radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1AFFFFFF, 0x0BFFFFFF});
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 2), 0x29FFFFFF);
        return d;
    }

    /** 青绿胶囊钮底：B 方案 btn_top→btn_bot 渐变（字用 GOLD_BTN_TEXT）。 */
    public static GradientDrawable goldCapsule(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF63D99D, 0xFF379B63});
        d.setCornerRadius(dp(c, 999));
        return d;
    }

    /** 青绿虚线框（添加卡）。 */
    public static GradientDrawable goldDashed(Context c, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x0D45C08A);
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 2), 0x7345C08A, dp(c, 10), dp(c, 7));
        return d;
    }

    /** 深色圆钮底（边线高光）。 */
    public static GradientDrawable darkCircle(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x12FFFFFF);
        d.setStroke(dp(c, 2), 0x33FFFFFF);
        return d;
    }

    /** 青绿圆钮底。 */
    public static GradientDrawable goldCircle(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF63D99D, 0xFF379B63});
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    /** 分段胶囊容器（暗底灰描边）。 */
    public static GradientDrawable segContainer(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x0FFFFFFF);
        d.setCornerRadius(dp(c, 999));
        d.setStroke(dp(c, 2), 0x29FFFFFF);
        return d;
    }

    /** 分段选中：青绿底（漫画稿）或青绿 tint＋青绿字（小说稿），调用方选。 */
    public static GradientDrawable segSelectedSolid(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF63D99D, 0xFF379B63});
        d.setCornerRadius(dp(c, 999));
        return d;
    }

    public static GradientDrawable segSelectedTint(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x3845C08A);
        d.setCornerRadius(dp(c, 999));
        d.setStroke(dp(c, 2), 0x8C45C08A);
        return d;
    }

    /** 金进度条（ProgressBar 用）：轨道半透明白，填充金渐变。 */
    public static LayerDrawable goldProgressBar(Context c) {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x24FFFFFF);
        track.setCornerRadius(dp(c, 999));
        GradientDrawable fill = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GOLD_DEEP, 0xFF63D99D});
        fill.setCornerRadius(dp(c, 999));
        ClipDrawable clip = new ClipDrawable(fill, Gravity.START,
                ClipDrawable.HORIZONTAL);
        LayerDrawable d = new LayerDrawable(new Drawable[]{track, clip});
        d.setId(0, android.R.id.background);
        d.setId(1, android.R.id.progress);
        return d;
    }

    /** 金 SeekBar：轨道＋填充＋白圆点。 */
    public static void styleGoldSeekBar(Context c, android.widget.SeekBar bar) {
        bar.setProgressDrawable(goldProgressBar(c));
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setSize(dp(c, 16), dp(c, 16));
        bar.setThumb(thumb);
        bar.setThumbOffset(dp(c, 8));
    }

    /** 开关着色：开态金，关态灰（行为不动）。 */
    public static void styleSwitch(android.widget.Switch sw) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked}, new int[]{}};
        sw.setThumbTintList(new ColorStateList(states,
                new int[]{Color.WHITE, Color.WHITE}));
        sw.setTrackTintList(new ColorStateList(states,
                new int[]{GOLD, 0x29FFFFFF}));
    }

    /** 封面圆角裁剪。 */
    public static void roundClip(View v, Context c, int radiusDp) {
        final float r = dp(c, radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view,
                                   android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(),
                        view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** 顶部栏吃状态栏 insets（防接缝）：保留原有上 padding 再叠加。 */
    public static void applyTopInset(View v, Context c, int baseDp) {
        final int base = dp(c, baseDp);
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            int top = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()).top;
            view.setPadding(view.getPaddingLeft(), base + top,
                    view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });
    }

    /** 底部栏吃导航栏 insets（防接缝）。 */
    public static void applyBottomInset(View v, Context c, int baseDp) {
        final int base = dp(c, baseDp);
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, insets) -> {
            int bottom = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), base + bottom);
            return insets;
        });
    }

    /** 分区头：左白标题＋右灰动作字（点击走调用方原逻辑）。 */
    public static LinearLayout sectionHeader(Context c, String title,
                                             String action,
                                             View.OnClickListener onAction) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 20);
        lp.rightMargin = dp(c, 20);
        lp.topMargin = dp(c, 22);
        lp.bottomMargin = dp(c, 10);
        row.setLayoutParams(lp);

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(TEXT);
        t.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        t.setLayoutParams(tlp);
        row.addView(t);

        if (action != null) {
            TextView m = new TextView(c);
            m.setText(action);
            m.setTextSize(13);
            m.setTextColor(FAINT);
            m.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
            if (onAction != null) m.setOnClickListener(onAction);
            row.addView(m);
        }
        return row;
    }

    /** 底部灰小字说明（稿里各页底部的注）。 */
    public static TextView footnote(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(FAINT);
        t.setLineSpacing(dp(c, 4), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 20);
        lp.rightMargin = dp(c, 20);
        lp.topMargin = dp(c, 14);
        lp.bottomMargin = dp(c, 24);
        t.setLayoutParams(lp);
        return t;
    }

    /** 深色页面的返回字钮「‹ 返回」（灰字，点走 finish）。 */
    public static TextView backLabel(Context c, View.OnClickListener onBack) {
        TextView t = new TextView(c);
        t.setText("‹ 返回");
        t.setTextSize(15);
        t.setTextColor(SUB);
        t.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        t.setOnClickListener(onBack);
        return t;
    }
}

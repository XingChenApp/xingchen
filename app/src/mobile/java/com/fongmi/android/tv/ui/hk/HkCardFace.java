package com.fongmi.android.tv.ui.hk;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;

import com.fongmi.android.tv.api.hk.HkItem;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 小程序页规则卡面的构建函数（原 HkPageActivity.RuleAdapter.cardFaceDrawable，
 * 配方出自提交 d0001905e「小程序卡片 v5 化」：微渐变＋按条目主色淡染＋顶部 gloss、无描边、圆角 12dp）。
 * 现行小程序页已换液态玻璃（提交 3cd54b7f7）、原函数随之摘除，本类按其原方逐值复刻为共享件，
 * 供海阔文章列表衬底复用同一套卡面机制；小程序页现行玻璃面不在此动。
 * 主色口径与原函数一致：图标色值定义（parseIconColor）优先，无定义时标题首字色板（paletteColorFor）兜底。
 * 用户定版（2026-11-02，实拍 114103_11_prlt.jpg「这个框挺好的，跟背景搭」）：
 * 海阔控件衬底统一用这张卡面——不用原版纯白（用户原话「白的太难看了，所以我才让你去掉的」）、不用页签玻璃。
 */
public final class HkCardFace {

    private HkCardFace() {
    }

    /** 淡染主色板（与 HkPageActivity 内的同名表同值）：无色值定义时按标题首字哈希取。 */
    private static final int[] TINT_PALETTE = {
            0xFFC9970D, 0xFF2E7FD9, 0xFF35A06B, 0xFFD95F4B, 0xFF8E6BC8, 0xFF2AA5A0
    };

    /** 图标主色："#" 开头解析色值；图片图标/无定义返回 -1（调用方用标题首字色板）。 */
    public static int parseIconColor(String iconDef) {
        if (!TextUtils.isEmpty(iconDef) && iconDef.startsWith("#")) {
            try {
                return Color.parseColor(iconDef);
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    /** 把 tint 按 ratio 染到 base 上（0=全 base，1=全 tint）。与原 blend 逐行同值。 */
    public static int blend(int base, int tint, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int red = Math.round(Color.red(base) * (1 - r) + Color.red(tint) * r);
        int green = Math.round(Color.green(base) * (1 - r) + Color.green(tint) * r);
        int blue = Math.round(Color.blue(base) * (1 - r) + Color.blue(tint) * r);
        return Color.argb(0xFF, red, green, blue);
    }

    /** 标题首字色板兜底（与原 paletteColorFor 逐行同值）。 */
    public static int paletteColorFor(String title) {
        int h = 0;
        if (title != null && !title.isEmpty()) h = Math.abs(title.charAt(0));
        return TINT_PALETTE[h % TINT_PALETTE.length];
    }

    /**
     * 条目主色：pic 本身是 "#色值" 定义时取之（海阔条目 pic 兼作色块定义位），
     * 否则标题色板兜底——即原机制的「图标主色 → 取不到用兜底色」。
     */
    public static int baseColorFor(HkItem item) {
        if (item == null) return paletteColorFor(null);
        int c = parseIconColor(item.getPic());
        if (c != -1) return c;
        return paletteColorFor(item.getTitle());
    }

    /** 卡面本体：原 cardFaceDrawable 逐值（上端白染 6%、下端暖白 #FFF4E2 染 10%、gloss 0x2EFFFFFF→透明、圆角 12dp、无描边）。 */
    public static Drawable face(Context context, int baseColor) {
        int top = blend(0xFFFFFFFF, baseColor, 0.06f);
        int bottom = blend(0xFFFFF4E2, baseColor, 0.10f);
        GradientDrawable face = new GradientDrawable();
        face.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        face.setColors(new int[]{top, bottom});
        face.setCornerRadius(ResUtil.dp2px(12));
        GradientDrawable gloss = new GradientDrawable();
        gloss.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        gloss.setColors(new int[]{0x2EFFFFFF, 0x00FFFFFF});
        gloss.setCornerRadius(ResUtil.dp2px(12));
        return new LayerDrawable(new Drawable[]{face, gloss});
    }
}

package com.fongmi.android.tv.read;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.ReadingSkin;

/**
 * 阅读弹窗统一入口：深色毛玻璃主题＋统一淡入淡出。
 * 用法：把 new AlertDialog.Builder(...) / new HkDialogs(...) 换成 new ReadDialogs(...)，
 * 其余链式调用一字不动。只换弹窗的皮（主题背景/圆角/按钮与列表字色/淡入动画）；
 * 文案、按钮个数与顺序、点击回调逻辑一律不动。
 *
 * 选项类弹窗（原 setItems / setSingleChoiceItems）走 {@link #options} /
 * {@link #optionsChecked}：选项为独立卡片、有间距（他 2026-10-10 定的规矩）。
 */
public class ReadDialogs extends AlertDialog.Builder {

    public ReadDialogs(@NonNull Context context) {
        super(context, R.style.ThemeOverlay_XingChen_GlassDialog_Dark);
    }

    @NonNull
    @Override
    public AlertDialog create() {
        AlertDialog dialog = super.create();
        Window window = dialog.getWindow();
        if (window != null) window.setWindowAnimations(R.style.HkDialogFade);
        return dialog;
    }

    /** 选项回调。 */
    public interface OnSelect {
        void onSelect(int which);
    }

    /**
     * 输入框工厂（阅读弹窗三处输入共用：粘贴导入 / 链接导入 / 搜书）。
     * 用对话框 themed context 创建，显式套 {@code XingChen.DialogEditText.Dark} 样式
     * （浅字＋暗底），光标强制可见色。
     *
     * @param multiLine  true=多行；heightDp&gt;0 时为定高滚动框（文本在框内滚动，
     *                   弹窗不被撑满屏——粘贴导入走这个）
     */
    public static EditText input(Context context, String hint, boolean multiLine, int heightDp) {
        Context themed = new ContextThemeWrapper(context,
                R.style.ThemeOverlay_XingChen_GlassDialog_Dark);
        EditText et = new EditText(themed, null, 0, R.style.XingChen_DialogEditText_Dark);
        et.setHint(hint);
        if (multiLine) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setGravity(Gravity.TOP | Gravity.START);
            if (heightDp > 0) {
                et.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ReadingSkin.dp(context, heightDp)));
                et.setVerticalScrollBarEnabled(true);
                et.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            }
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            et.setSingleLine(true);
        }
        et.setHighlightColor(0x6645C08A);
        applyVisibleCursor(et);
        return et;
    }

    /** 输入框工厂：不定高（链接 / 搜索单行走这个）。 */
    public static EditText input(Context context, String hint, boolean multiLine) {
        return input(context, hint, multiLine, 0);
    }

    /** 光标强制可见色：29+ 走公开 API；低版本用主题默认光标（反射 mCursorDrawable 在 targetSdk 28+ 被 lintVital 拦为致命错，不用）。 */
    private static void applyVisibleCursor(EditText et) {
        if (Build.VERSION.SDK_INT >= 29) {
            et.setTextCursorDrawable(new ColorDrawable(ReadingSkin.GOLD_TEXT));
        }
    }

    /** 选项卡片弹窗：点选即回调并关闭（原 setItems 语义）。 */
    public static void options(Context context, String title, String[] items,
                               OnSelect onSelect) {
        optionsChecked(context, title, items, -1, onSelect);
    }

    /**
     * 单选卡片弹窗：checked 项金色描边高亮（原 setSingleChoiceItems 语义：
     * 本阅读域内调用处均为"点选即生效并关闭"，故不保留 radio 常驻形态）。
     */
    public static void optionsChecked(Context context, String title, String[] items,
                                      int checked, OnSelect onSelect) {
        optionsChecked(context, title, items, checked, onSelect, null, null);
    }

    /**
     * 带页脚按钮的单选卡片弹窗（对齐原 neutralButton，如"试听当前音色"）。
     * 页脚按钮只执行回调，不关闭弹窗（调用方自行决定）。
     *
     * 结构说明（1.3.49 重构）：照在他手机上验证过的 HkDialogs.cards 写法——
     * ScrollView 直接 setView，原生 setTitle，不再用外层 LinearLayout 包
     * ScrollView＋手写标题（后者在弹窗测量链里高度塌陷，列表弹不出）。
     */
    public static void optionsChecked(Context context, String title, String[] items,
                                      int checked, OnSelect onSelect,
                                      String footerText, Runnable onFooter) {
        final AlertDialog[] holder = new AlertDialog[1];
        int pad = ReadingSkin.dp(context, 20);
        int cp = ReadingSkin.dp(context, 14);

        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            TextView card = new TextView(context);
            card.setText(items[i]);
            card.setTextSize(15);
            card.setTextColor(i == checked ? ReadingSkin.GOLD_TEXT : ReadingSkin.TEXT);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(cp, ReadingSkin.dp(context, 13), cp,
                    ReadingSkin.dp(context, 13));
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(ReadingSkin.dp(context, 12));
            if (i == checked) {
                bg.setColor(0x1A45C08A);
                bg.setStroke(ReadingSkin.dp(context, 2), 0x8C45C08A);
            } else {
                bg.setColor(0x14FFFFFF);
                bg.setStroke(1, 0x29FFFFFF);
            }
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = ReadingSkin.dp(context, 8);
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> {
                if (holder[0] != null) holder[0].dismiss();
                onSelect.onSelect(which);
            });
            list.addView(card);
        }

        ScrollView sv = new ScrollView(context);
        sv.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.setPadding(pad, pad, pad, pad);
        sv.setClipToPadding(false);

        ReadDialogs b = new ReadDialogs(context);
        if (title != null) b.setTitle(title);
        if (footerText != null && onFooter != null) {
            // 先占位建出 neutral 按钮，show 后再换掉点击监听——
            // 原生按钮点后自动关闭，这里要「只执行回调、不关闭」，故不用自带 listener。
            b.setNeutralButton(footerText, (d, w) -> {
            });
        }
        AlertDialog dialog = b.setView(sv).create();
        holder[0] = dialog;
        dialog.show();
        if (footerText != null && onFooter != null) {
            android.widget.Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) neutral.setOnClickListener(v -> onFooter.run());
        }
    }
}

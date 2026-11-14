package com.example.hikerview.ui.view.colorDialog;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.ui.hk.HkDialogs;

/**
 * 官 {@code com.example.hikerview.ui.view.colorDialog.PromptDialog} 的等价确认弹窗
 * （官源 PromptDialog.smali：Dialog 子类、单确认钮＋标题/正文两 TextView）。
 *
 * <p>咱家不照搬官的自绘 Dialog（位图三角/颜色态列表一整套绘制层未转写），
 * 弹窗件接咱家既有 {@link HkDialogs}（海阔弹窗统一入口：深色毛玻璃＋淡入淡出）：
 * 标题/文案/确认钮文案与回调形态照官 API 落——setDialogType/setAnimationEnable
 * 两法官在本调用面只存值不改文案行为，此处同口径存值；setPositiveListener 的
 * 回调在确认时先 dismiss 再回调（官各 lambda$realCheckAutoText 首步即
 * PromptDialog.dismiss()，同序）。官本体只有确认钮、无取消钮；本等价件按
 * 136 批口径另加「取消」钮（只关窗、无回调），记名差异在此。
 *
 * <p>官 API 逐项对位：常量 DIALOG_TYPE_*（DEFAULT/INFO=0、HELP=1、WRONG=2、
 * SUCCESS=3、WARNING=4，官字段表逐字）、构造 (Context)/(Context,int 主题位
 * 咱家不用、忽略)、setTitleText(CharSequence/int)、setContentText(Object/int)、
 * setSpannedContentByStr、setPositiveListener 三重载、setTheCancelable、
 * getDialogType、show/dismiss。getTitleTextView/getContentTextView 两法官为
 * 取自绘布局内的 TextView，咱家无自绘布局、无等价物，未落、记名。
 */
public class PromptDialog {

    public static final int DIALOG_TYPE_DEFAULT = 0;
    public static final int DIALOG_TYPE_INFO = 0;
    public static final int DIALOG_TYPE_HELP = 1;
    public static final int DIALOG_TYPE_WRONG = 2;
    public static final int DIALOG_TYPE_SUCCESS = 3;
    public static final int DIALOG_TYPE_WARNING = 4;

    /** 官 PromptDialog$OnPositiveListener（onClick(PromptDialog)）。 */
    public interface OnPositiveListener {
        void onClick(PromptDialog dialog);
    }

    private final Context context;
    private CharSequence title;
    private CharSequence content;
    private CharSequence positiveText;
    private OnPositiveListener positiveListener;
    private int dialogType;
    private boolean animationEnable;
    private boolean cancelable = true;
    private AlertDialog dialog;

    public PromptDialog(Context context) {
        this.context = context;
    }

    /** 官 (Context, int) 构造的第二参是主题资源，咱家弹窗主题由 HkDialogs 统一接管，此参不用。 */
    public PromptDialog(Context context, int themeRes) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public int getDialogType() {
        return dialogType;
    }

    public PromptDialog setDialogType(int dialogType) {
        this.dialogType = dialogType;
        return this;
    }

    public PromptDialog setAnimationEnable(boolean enable) {
        this.animationEnable = enable;
        return this;
    }

    public PromptDialog setTitleText(CharSequence title) {
        this.title = title;
        return this;
    }

    public PromptDialog setTitleText(int resId) {
        this.title = context.getString(resId);
        return this;
    }

    public PromptDialog setContentText(Object content) {
        this.content = content == null ? null : content.toString();
        return this;
    }

    public PromptDialog setContentText(int resId) {
        this.content = context.getString(resId);
        return this;
    }

    public PromptDialog setSpannedContentByStr(String str) {
        this.content = str;
        return this;
    }

    public PromptDialog setPositiveListener(OnPositiveListener listener) {
        this.positiveListener = listener;
        return this;
    }

    public PromptDialog setPositiveListener(int resId, OnPositiveListener listener) {
        this.positiveText = context.getString(resId);
        this.positiveListener = listener;
        return this;
    }

    public PromptDialog setPositiveListener(CharSequence text, OnPositiveListener listener) {
        this.positiveText = text;
        this.positiveListener = listener;
        return this;
    }

    public PromptDialog setTheCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public void dismiss() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }

    public void show() {
        if (HkDialogs.isHostGone(context)) {
            return;
        }
        CharSequence btnText = positiveText == null ? "确定" : positiveText;
        dialog = new HkDialogs(context)
                .setTitle(title)
                .setMessage(content)
                .setCancelable(cancelable)
                .setPositiveButton(btnText, (d, which) -> {
                    dismiss();
                    if (positiveListener != null) {
                        positiveListener.onClick(this);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}

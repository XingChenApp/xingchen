package com.lxj.xpopup.impl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.lxj.xpopup.util.XPopupUtils;

/**
 * 官包内嵌 XPopup 的定制类 {@code com.lxj.xpopup.impl.MyInputConfirmPopupView}
 * 照官 smali 逐字转写（官源 smali_classes3/com/lxj/xpopup/impl/
 * MyInputConfirmPopupView.smali）。咱家依赖的原版 XPopup 2.9.0 无此类，
 * 按同包补齐类先例（com.annimon.stream.function.Consumer 同法）落本文件。
 *
 * <p>与原版 InputConfirmPopupView 的官差异：取消钮点击时，若 cancelText
 * 非空且不等于「取消」（如云口令弹窗的「完整口令」），只回调
 * OnCancelListener、不 dismiss；主题色为输入框单独设 hint/文本色；
 * getMaxWidth 优先 popupInfo.maxWidth。
 */
public class MyInputConfirmPopupView extends ConfirmPopupView implements View.OnClickListener {

    OnCancelListener cancelListener;
    OnInputConfirmListener inputConfirmListener;
    public CharSequence inputContent;

    public MyInputConfirmPopupView(Context context, int i) {
        super(context, i);
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        et_input.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(hint)) {
            et_input.setHint(hint);
        }
        if (!TextUtils.isEmpty(inputContent)) {
            et_input.setText(inputContent);
        }
        XPopupUtils.setCursorDrawableColor(et_input, XPopup.getPrimaryColor());
        if (bindLayoutId == 0) {
            et_input.post(() -> {
                if (et_input.getMeasuredWidth() > 0) {
                    BitmapDrawable bitmapDrawable = XPopupUtils.createBitmapDrawable(getContext(),
                            et_input.getMeasuredWidth(), Color.parseColor("#888888"));
                    BitmapDrawable bitmapDrawable2 = XPopupUtils.createBitmapDrawable(getContext(),
                            et_input.getMeasuredWidth(), XPopup.getPrimaryColor());
                    et_input.setBackgroundDrawable(XPopupUtils.createSelector(bitmapDrawable, bitmapDrawable2));
                }
            });
        }
    }

    public EditText getEditText() {
        return et_input;
    }

    @Override
    protected void applyLightTheme() {
        super.applyLightTheme();
        et_input.setHintTextColor(Color.parseColor("#888888"));
        et_input.setTextColor(Color.parseColor("#333333"));
    }

    @Override
    protected void applyDarkTheme() {
        super.applyDarkTheme();
        et_input.setHintTextColor(Color.parseColor("#888888"));
        et_input.setTextColor(Color.parseColor("#dddddd"));
    }

    public void setListener(OnInputConfirmListener onInputConfirmListener, OnCancelListener onCancelListener) {
        this.cancelListener = onCancelListener;
        this.inputConfirmListener = onInputConfirmListener;
    }

    @Override
    public void onClick(View view) {
        if (view == tv_cancel) {
            if (cancelListener != null) {
                cancelListener.onCancel();
            }
            if (!TextUtils.isEmpty(cancelText) && !"取消".contentEquals(cancelText)) {
                return;
            }
            dismiss();
            return;
        }
        if (view == tv_confirm) {
            if (inputConfirmListener != null) {
                inputConfirmListener.onConfirm(et_input.getText().toString().trim());
            }
            if (popupInfo.autoDismiss.booleanValue()) {
                dismiss();
            }
        }
    }

    @Override
    protected int getMaxWidth() {
        if (popupInfo.maxWidth == 0) {
            return super.getMaxWidth();
        }
        return popupInfo.maxWidth;
    }
}

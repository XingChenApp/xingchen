package com.example.hikerview.ui.view.popup;

import android.content.Context;

import com.lxj.xpopup.R;
import com.lxj.xpopup.core.PopupInfo;
import com.lxj.xpopup.impl.InputConfirmPopupView;
import com.lxj.xpopup.impl.MyInputConfirmPopupView;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;

/**
 * 官 {@code com.example.hikerview.ui.view.popup.MyXpopup} 照官 smali 转写
 * （官源 smali_classes2/com/example/hikerview/ui/view/popup/MyXpopup.smali）：
 * 构造、Builder、三配置法（borderRadius/autoOpenSoftInput/autoFocusEditText/
 * isDestroyOnDismiss）、asInputConfirm 两重载、asInputMyConfirm 三重载。
 * 官默认布局 id 0x7f0d01e7 经官 public.xml 核为库布局 xpopup_confirm_input，
 * 咱家直引 com.lxj.xpopup.R.layout.xpopup_confirm_input（同一库布局）。
 * 官 asImageViewer 四重载依赖 MyImageViewerPopupView/XPopupImageLoader
 * （咱家树无此二类，ImgUtil 批已记名），本批不落。
 */
public class MyXpopup {

    private Context context;
    private final PopupInfo popupInfo = new PopupInfo();

    public MyXpopup() {
    }

    public MyXpopup Builder(Context context) {
        this.context = context;
        return this;
    }

    public MyXpopup borderRadius(float f) {
        this.popupInfo.borderRadius = f;
        return this;
    }

    public MyXpopup autoOpenSoftInput(Boolean bool) {
        this.popupInfo.autoOpenSoftInput = bool;
        return this;
    }

    public MyXpopup autoFocusEditText(boolean z) {
        this.popupInfo.autoFocusEditText = z;
        return this;
    }

    public MyXpopup isDestroyOnDismiss(boolean z) {
        this.popupInfo.isDestroyOnDismiss = z;
        return this;
    }

    public InputConfirmPopupView asInputConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                CharSequence charSequence3, CharSequence charSequence4,
                                                OnInputConfirmListener onInputConfirmListener,
                                                OnCancelListener onCancelListener) {
        return asInputConfirm(charSequence, charSequence2, charSequence3, charSequence4,
                onInputConfirmListener, onCancelListener, R.layout.xpopup_confirm_input);
    }

    public InputConfirmPopupView asInputConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                CharSequence charSequence3, CharSequence charSequence4,
                                                OnInputConfirmListener onInputConfirmListener,
                                                OnCancelListener onCancelListener, int i) {
        InputConfirmPopupView inputConfirmPopupView = new InputConfirmPopupView(this.context, i);
        inputConfirmPopupView.setTitleContent(charSequence, charSequence2, charSequence4);
        inputConfirmPopupView.inputContent = charSequence3;
        inputConfirmPopupView.setListener(onInputConfirmListener, onCancelListener);
        inputConfirmPopupView.popupInfo = this.popupInfo;
        return inputConfirmPopupView;
    }

    public MyInputConfirmPopupView asInputMyConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                    CharSequence charSequence3, CharSequence charSequence4,
                                                    OnInputConfirmListener onInputConfirmListener,
                                                    OnCancelListener onCancelListener) {
        return asInputMyConfirm(charSequence, charSequence2, charSequence3, charSequence4,
                onInputConfirmListener, onCancelListener, R.layout.xpopup_confirm_input);
    }

    public MyInputConfirmPopupView asInputMyConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                    CharSequence charSequence3, CharSequence charSequence4,
                                                    OnInputConfirmListener onInputConfirmListener,
                                                    OnCancelListener onCancelListener, int i) {
        return asInputMyConfirm(charSequence, charSequence2, charSequence3, charSequence4,
                onInputConfirmListener, onCancelListener, i, null);
    }

    public MyInputConfirmPopupView asInputMyConfirm(CharSequence charSequence, CharSequence charSequence2,
                                                    CharSequence charSequence3, CharSequence charSequence4,
                                                    OnInputConfirmListener onInputConfirmListener,
                                                    OnCancelListener onCancelListener, int i, String str) {
        MyInputConfirmPopupView myInputConfirmPopupView = new MyInputConfirmPopupView(this.context, i);
        myInputConfirmPopupView.setTitleContent(charSequence, charSequence2, charSequence4);
        myInputConfirmPopupView.inputContent = charSequence3;
        myInputConfirmPopupView.setListener(onInputConfirmListener, onCancelListener);
        myInputConfirmPopupView.setCancelText(str);
        myInputConfirmPopupView.popupInfo = this.popupInfo;
        return myInputConfirmPopupView;
    }
}

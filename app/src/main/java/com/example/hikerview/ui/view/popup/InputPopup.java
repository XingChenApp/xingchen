package com.example.hikerview.ui.view.popup;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.google.android.material.textfield.TextInputLayout;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 照官 8.83 本体移植（jadx 官包源，资源号已按官 public.xml 还原名）：
 * 双输入确认弹窗（标题行＋地址行）。bind 四参形与六参形俱全；
 * 确认时按 dismissWhenConfirm（默认 true）先关窗，再回 ok(标题文, 地址文)。
 */
public class InputPopup extends CenterPopupView {

    private CancelListener cancelListener;
    private boolean dismissWhenConfirm;
    private OkListener okListener;
    private String title;
    private String titleDefault;
    private EditText titleEdit;
    private String titleHint;
    private String urlDefault;
    private EditText urlEdit;
    private String urlHint;

    public interface CancelListener {
        void cancel();
    }

    public interface OkListener {
        void ok(String str, String str2);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_input_popup;
    }

    public InputPopup(Context context) {
        super(context);
        this.dismissWhenConfirm = true;
    }

    public InputPopup bind(String str, String str2, String str3, OkListener okListener) {
        return bind(str, str2, null, str3, null, okListener);
    }

    public InputPopup bind(String str, String str2, String str3, String str4, String str5, OkListener okListener) {
        this.title = str;
        this.titleHint = str2;
        this.titleDefault = str3;
        this.urlHint = str4;
        this.urlDefault = str5;
        this.okListener = okListener;
        return this;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView textView = (TextView) findViewById(R.id.title);
        this.titleEdit = (EditText) findViewById(R.id.edit_title);
        this.urlEdit = (EditText) findViewById(R.id.edit_url);
        textView.setText(this.title);
        if (StringUtil.isNotEmpty(this.titleHint)) {
            ((TextInputLayout) findViewById(R.id.title_bg)).setHint(this.titleHint);
        }
        if (StringUtil.isNotEmpty(this.urlHint)) {
            ((TextInputLayout) findViewById(R.id.url_bg)).setHint(this.urlHint);
        }
        if (StringUtil.isNotEmpty(this.titleDefault)) {
            this.titleEdit.setText(this.titleDefault);
        }
        if (StringUtil.isNotEmpty(this.urlDefault)) {
            this.urlEdit.setText(this.urlDefault);
        }
        ((TextView) findViewById(R.id.tv_cancel)).setOnClickListener(view -> {
            dismiss();
            CancelListener cancelListener = this.cancelListener;
            if (cancelListener != null) {
                cancelListener.cancel();
            }
        });
        ((TextView) findViewById(R.id.tv_confirm)).setOnClickListener(view -> {
            if (this.dismissWhenConfirm) {
                dismiss();
            }
            this.okListener.ok(this.titleEdit.getText().toString(), this.urlEdit.getText().toString());
        });
    }

    public CancelListener getCancelListener() {
        return this.cancelListener;
    }

    public InputPopup setCancelListener(CancelListener cancelListener) {
        this.cancelListener = cancelListener;
        return this;
    }

    public boolean isDismissWhenConfirm() {
        return this.dismissWhenConfirm;
    }

    public void setDismissWhenConfirm(boolean z) {
        this.dismissWhenConfirm = z;
    }
}

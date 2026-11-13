package com.example.hikerview.ui.view.popup;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.CenterPopupView;

/**
 * 照官 8.83 本体移植（jadx 官包源）：带输入框的确认弹窗。
 * bind(标题, 输入提示, OkListener)，setBtn 可改确认/取消文案；
 * 点确认先关窗再把输入内容回给 ok()，取消同理回 cancel()。
 */
public class ConfirmPopup extends CenterPopupView {

    private String cancelText;
    private OkListener okListener;
    private String okText;
    private String title;
    private EditText titleEdit;
    private String titleHint;

    public interface OkListener {
        void cancel();

        void ok(String str);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.pop_confirm_popup;
    }

    public ConfirmPopup(Context context) {
        super(context);
    }

    public ConfirmPopup bind(String str, String str2, OkListener okListener) {
        this.title = str;
        this.titleHint = str2;
        this.okListener = okListener;
        return this;
    }

    public ConfirmPopup setBtn(String str, String str2) {
        this.okText = str;
        this.cancelText = str2;
        return this;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView textView = (TextView) findViewById(R.id.title);
        this.titleEdit = (EditText) findViewById(R.id.edit_title);
        textView.setText(this.title);
        this.titleEdit.setHint(this.titleHint);
        TextView textView2 = (TextView) findViewById(R.id.tv_cancel);
        textView2.setOnClickListener(view -> {
            dismiss();
            this.okListener.cancel();
        });
        TextView textView3 = (TextView) findViewById(R.id.tv_confirm);
        textView3.setOnClickListener(view -> {
            dismiss();
            this.okListener.ok(this.titleEdit.getText().toString());
        });
        if (StringUtil.isNotEmpty(this.okText)) {
            textView3.setText(this.okText);
        }
        if (StringUtil.isNotEmpty(this.cancelText)) {
            textView2.setText(this.cancelText);
        }
    }
}

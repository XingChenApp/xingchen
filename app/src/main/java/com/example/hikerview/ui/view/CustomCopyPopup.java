package com.example.hikerview.ui.view;

import android.content.Context;
import android.text.Html;
import android.widget.TextView;

import com.example.hikerview.R;
import com.example.hikerview.utils.StringUtil;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

/**
 * 照官 8.83 本体移植（jadx 官包源）：长按自由复制弹窗。with(头, 正文)，
 * 「&lt;/&gt;」钮在原文/HTML 渲染间切换正文，正文 TextView 可选中复制。
 */
public class CustomCopyPopup extends BottomPopupView {

    private TextView codeBtn;
    private String header;
    private boolean showCode;
    private TextView textView;
    private String title;

    @Override
    protected int getImplLayoutId() {
        return R.layout.view_custom_copy_text;
    }

    public CustomCopyPopup with(String str, String str2) {
        this.title = str2;
        this.header = str;
        return this;
    }

    public CustomCopyPopup(Context context) {
        super(context);
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        this.textView = (TextView) findViewById(R.id.movie_one_title);
        TextView textView = (TextView) findViewById(R.id.title);
        if (StringUtil.isNotEmpty(this.header)) {
            textView.setText(this.header);
            this.textView.setText(this.title);
        }
        TextView textView2 = (TextView) findViewById(R.id.code);
        this.codeBtn = textView2;
        textView2.setOnClickListener(view -> tx$onCreate$0());
        if (StringUtil.isNotEmpty(this.title) && StringUtil.isEmpty(this.header)) {
            this.textView.setText(Html.fromHtml(this.title));
        }
    }

    private void tx$onCreate$0() {
        boolean z = this.showCode;
        this.showCode = !z;
        if (!z) {
            this.textView.setText(this.title);
            this.codeBtn.setTextColor(getContext().getResources().getColor(R.color.greenAction));
        } else {
            this.textView.setText(Html.fromHtml(this.title));
            this.codeBtn.setTextColor(getContext().getResources().getColor(R.color.black_666));
        }
    }

    @Override
    protected int getPopupHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * 0.85f);
    }
}
